"""Shadertoy-grade procedural visuals, re-implemented with numpy + OpenCV.

These are vectorised versions of the demoscene / shadertoy looks that read as
"expensive" on a phone screen — domain-warped nebula, accretion vortex, neon
tunnel, Tron grid, glass shards, metaball orbs, volumetric god rays — but
written to run at 1080x1920 on a CPU-only box.

Every function returns float32 (H, W, 3) in 0..255, additive-friendly
(black = no-op) except `shockwave` / `anamorphic_flare` which draw in-place.
"""
import math

import cv2
import numpy as np

from .core import W, H

RNG_GLOBAL = np.random.default_rng(1234)


# ------------------------------------------------------------------ helpers
def _grid(w, h):
    ys = (np.arange(h, dtype=np.float32) + 0.5) / h
    xs = (np.arange(w, dtype=np.float32) + 0.5) / w
    return xs[None, :], ys[:, None]


def _hash2(ix, iy, seed=0):
    n = ix * 374761393.0 + iy * 668265263.0 + seed * 1442695040.0
    n = np.sin(n) * 43758.5453123
    return n - np.floor(n)


def vnoise(x, y, seed=0):
    x, y = np.broadcast_arrays(np.asarray(x, np.float32), np.asarray(y, np.float32))
    ix, iy = np.floor(x), np.floor(y)
    fx, fy = x - ix, y - iy
    ux = fx * fx * (3 - 2 * fx)
    uy = fy * fy * (3 - 2 * fy)
    a = _hash2(ix, iy, seed)
    b = _hash2(ix + 1, iy, seed)
    c = _hash2(ix, iy + 1, seed)
    d = _hash2(ix + 1, iy + 1, seed)
    return (a * (1 - ux) + b * ux) * (1 - uy) + (c * (1 - ux) + d * ux) * uy


def fbm(x, y, octaves=4, seed=0, lac=2.0, gain=0.5):
    x, y = np.broadcast_arrays(np.asarray(x, np.float32), np.asarray(y, np.float32))
    s = np.zeros(x.shape, np.float32)
    amp, tot = 1.0, 0.0
    fx, fy = x, y
    for _ in range(octaves):
        s += amp * vnoise(fx, fy, seed)
        tot += amp
        amp *= gain
        fx *= lac
        fy *= lac
        seed = (seed * 7 + 13) % 1000
    return s / tot


def _up(img, w=W, h=H, interp=cv2.INTER_LINEAR):
    return cv2.resize(img, (w, h), interpolation=interp)


def _finish(img, hot, gain=1.0):
    """Tonemap a low-res HDR-ish image to canvas units (0..255, additive)."""
    img = np.maximum(img, 0) * gain
    out = 1.0 - np.exp(-img * 1.35)
    out = np.power(np.clip(out, 0, 1), 0.92)
    col = out[..., None] * np.array(hot, np.float32)[None, None, :]
    return _up(col, interp=cv2.INTER_LINEAR)


# ------------------------------------------------------------------ 1. nebula
def nebula(t, hot=(255, 180, 90), seed=11, intensity=1.0, scale=1.5, cx=0.5, cy=0.42):
    """Domain-warped fbm nebula with ridged filaments."""
    w, h = W // 4, H // 4
    xs, ys = _grid(w, h)
    x = (xs - cx) * (W / H) * scale
    y = (ys - cy) * scale
    qx = fbm(x + t * 0.045, y, 2, seed)
    qy = fbm(x, y + t * 0.035, 2, seed + 7)
    x2 = x + 1.15 * (qx - 0.5) + 0.10 * math.sin(t * 0.11)
    y2 = y + 1.15 * (qy - 0.5) + 0.10 * math.cos(t * 0.09)
    f = fbm(x2 * 1.15, y2 * 1.15 + t * 0.02, 3, seed + 19)
    ridge = 1.0 - np.abs(fbm(x2 * 1.8, y2 * 1.8, 2, seed + 41) * 2 - 1)
    ridge = np.power(np.clip(ridge, 0, 1), 2.4)
    r2 = ((xs - cx) * 1.35) ** 2 + (ys - cy) ** 2
    core = np.exp(-r2 * 4.0)
    v = np.clip(f - 0.36, 0, None) ** 1.7
    v *= (0.30 + 1.05 * ridge) * (0.25 + 1.0 * core) * intensity * 5.2
    return _finish(v, hot)


# ------------------------------------------------------------------ 2. vortex
def vortex(t, hot=(255, 160, 80), seed=3, intensity=1.0, arms=5, twist=2.6):
    """Log-spiral accretion vortex — the 'Interstellar' hero plate."""
    w, h = W // 3, H // 3
    xs, ys = _grid(w, h)
    dx = (xs - 0.5) * (W / H)
    dy = ys - 0.5
    r = np.sqrt(dx * dx + dy * dy) + 1e-5
    ang = np.arctan2(dy, dx)
    sp = ang * arms + np.log(r) * twist * arms * 0.5 + t * 0.9
    arm = np.power(0.5 + 0.5 * np.cos(sp), 2.6)
    ring = 0.5 + 0.5 * np.sin(r * 55.0 - t * 2.4 + 3.0 * vnoise(xs * 6, ys * 6, seed))
    ring = np.power(ring, 12.0)
    core = np.exp(-r * 11.0)
    halo = np.exp(-r * 3.4)
    f = 0.45 + 1.1 * fbm(dx * 4 + t * 0.1, dy * 4, 4, seed + 5)
    v = (arm * 2.0 * halo + ring * 0.30 * halo + core * 3.0) * f
    v *= np.exp(-r * 1.15) * intensity * 2.2
    return _finish(v, hot)


# ------------------------------------------------------------------ 3. tunnel
def tunnel(t, hot=(255, 190, 110), seed=9, intensity=1.0, rings=22.0, speed=1.2):
    """Infinite neon tunnel: polar raymarch, depth fog, flowing streaks."""
    w, h = W // 3, H // 3
    xs, ys = _grid(w, h)
    dx = xs - 0.5
    dy = (ys - 0.5) * (H / W)
    r = np.sqrt(dx * dx + dy * dy) + 1e-4
    ang = np.arctan2(dy, dx)
    depth = 1.0 / r
    z = depth * rings - t * speed * rings
    band = np.power(0.5 + 0.5 * np.cos(z * 6.28318), 4.0)
    ribs = np.power(0.5 + 0.5 * np.cos(ang * 22.0 + t * 0.45), 1.6)
    st = fbm(ang * 6.0, z * 0.35, 3, seed)
    streak = np.power(np.clip(st * 1.6 - 0.4, 0, 1), 2.0)
    fog = np.exp(-r * 2.2)
    v = (band * 0.55 + streak * 0.9) * (0.35 + 0.9 * ribs) * fog * 1.7
    v += np.exp(-r * 10.0) * 2.2
    return _finish(v * intensity, hot)


# ------------------------------------------------------------------ 4. energy grid
def energy_grid(t, hot=(140, 200, 255), seed=21, intensity=1.0, horizon=0.58,
                cells=14.0, wave=0.9):
    """Perspective wave-grid with travelling shock rings (Tron hero plate)."""
    w, h = W // 3, H // 3
    xs, ys = _grid(w, h)
    dy = np.maximum(ys - horizon, 0.0015)
    z = 0.30 / dy
    x = (xs - 0.5) * z * 1.6
    rr = np.sqrt(x * x + (z - 0.35) ** 2)
    shock = np.exp(-((rr - (t * 1.15) % 3.2) ** 2) * 6.0)
    gx = np.abs(np.sin(x * cells * 3.14159))
    gz = np.abs(np.sin(z * cells * 2.2))
    line = np.exp(-((np.minimum(gx, gz)) ** 2) * 700.0)
    fog = np.exp(-z * 1.35)
    glow = (line * 2.6 + shock * 2.6) * fog
    glow *= 1.0 + wave * 0.5 * np.sin(z * 3.0 - t * 2.0)
    glow *= (ys > horizon) * 1.0
    sky = np.exp(-np.abs(ys - horizon) * 26.0) * 1.1
    return _finish(glow * intensity + sky, hot)


# ------------------------------------------------------------------ 5. shards
def shards(t, hot=(255, 120, 60), seed=33, intensity=1.0, n=40, speed=0.5):
    """Floating faceted glass shards with rim light."""
    w, h = W // 3, H // 3
    acc = np.zeros((h, w), np.float32)
    rng = np.random.default_rng(seed)
    for i in range(n):
        ph = rng.uniform(0, 6.28)
        cy = ((t * speed * (0.4 + rng.random()) + ph) % 1.8) - 0.4
        cx = (rng.random() * 1.4 - 0.2) + 0.06 * math.sin(t * 0.7 + ph)
        sz = rng.uniform(0.018, 0.075)
        rot = t * 0.35 * (rng.random() - 0.5) + ph
        pts = []
        for k in range(3):
            a = rot + k * 2.0944
            pts.append(((cx + math.cos(a) * sz) * w, (cy + math.sin(a) * sz) * h))
        pts = np.array(pts, np.int32)
        mask = np.zeros((h, w), np.uint8)
        cv2.fillConvexPoly(mask, pts, 1, cv2.LINE_AA)
        grad = np.linspace(0.10, 0.65, h, dtype=np.float32)[:, None]
        acc += mask.astype(np.float32) * grad
        rim = np.zeros_like(mask)
        cv2.polylines(rim, [pts.reshape(-1, 1, 2)], True, 1, 3)
        acc += rim.astype(np.float32) * 0.9
    ys = (np.arange(h, dtype=np.float32) / h)[:, None]
    acc *= (0.4 + 0.9 * ys)
    blur = cv2.GaussianBlur(acc, (0, 0), 1.2)
    return _finish((acc * 1.6 + blur * 0.5) * intensity, hot)


# ------------------------------------------------------------------ 6. metaball orbs
def orbs(t, hot=(255, 220, 160), seed=7, intensity=1.0, n=9):
    """Additive metaball orbs that merge — soft, expensive-looking plate."""
    w, h = W // 3, H // 3
    rng = np.random.default_rng(seed)
    xs, ys = _grid(w, h)
    field = np.zeros((h, w), np.float32)
    for i in range(n):
        ph = rng.uniform(0, 6.28)
        fx, fy = rng.uniform(0.4, 1.6), rng.uniform(0.5, 1.4)
        cx = 0.5 + 0.30 * math.sin(t * 0.21 * fx + ph)
        cy = 0.5 + 0.30 * math.cos(t * 0.17 * fy + ph * 1.7)
        rad = 0.055 + 0.03 * (0.5 + 0.5 * math.sin(t * 0.5 + ph))
        d2 = (xs - cx) ** 2 * (W / H) ** 2 + (ys - cy) ** 2
        field += rad * rad / (d2 + 1e-5)
    surf = np.clip((field - 2.3) / 1.6, 0, 1) ** 1.4
    return _finish(surf * intensity * 0.9, hot)


# ------------------------------------------------------------------ 7. rain streaks
def rain(t, hot=(180, 210, 255), intensity=1.0, n=170, speed=2.4, wind=0.18):
    """Long anamorphic rain streaks, full-res for crispness."""
    out = np.zeros((H, W), np.float32)
    rng = np.random.default_rng(41)
    xs = rng.uniform(-100, W + 100, n)
    ph = rng.uniform(0, 1, n)
    ln = rng.uniform(70, 320, n)
    al = rng.uniform(0.25, 1.0, n)
    for i in range(n):
        y = ((t * speed * 900 + ph[i] * (H + 400)) % (H + 400)) - 200
        x = xs[i] + y * wind
        cv2.line(out, (int(x), int(y)), (int(x + ln[i] * wind), int(y + ln[i])),
                 float(al[i] * 0.16), 2, cv2.LINE_AA)
    out = cv2.GaussianBlur(out, (0, 0), 0.6)
    return out[..., None] * np.array(hot, np.float32)[None, None, :] * intensity


# ------------------------------------------------------------------ 8. god rays
def god_rays(t, hot=(255, 200, 130), intensity=1.0, cx=0.5, cy=0.18, n=11, seed=5):
    """Radial volumetric shafts through dust."""
    w, h = W // 4, H // 4
    xs, ys = _grid(w, h)
    dx = xs - cx
    dy = ys - cy
    ang = np.arctan2(dy, dx)
    r = np.sqrt(dx * dx * (W / H) ** 2 + dy * dy) + 1e-4
    rays = np.zeros((h, w), np.float32)
    for i in range(n):
        f = 3.0 + i * 2.7
        rays += (0.5 + 0.5 * np.cos(ang * f + i * 1.31 + t * (0.12 + 0.05 * i))) ** 14 / (i + 2)
    rays /= rays.max() + 1e-6
    fall = np.exp(-r * 1.9)
    dust = 0.55 + 0.9 * fbm(xs * 6 + t * 0.15, ys * 6, 3, seed)
    v = np.clip(rays - 0.42, 0, None) ** 1.5 * fall * (0.30 + 0.9 * dust) * 3.4
    return _finish(v * intensity, hot)


# ------------------------------------------------------------------ 9. starfield
_STAR_CACHE = {}


def stars(t, intensity=1.0, n=260, drift=0.02, seed=13):
    """Vectorised starfield: one scatter into a half-res buffer, then blur."""
    key = seed
    if key not in _STAR_CACHE:
        rng = np.random.default_rng(seed)
        _STAR_CACHE[key] = (rng.uniform(0, W // 2, n), rng.uniform(0, H // 2, n),
                            rng.uniform(0.2, 1.0, n), rng.uniform(1.0, 3.4, n),
                            rng.uniform(0, 6.28, n))
    xs, ys, z, tw, ph = _STAR_CACHE[key]
    hw, hh = W // 2, H // 2
    y = (ys + t * drift * 30 * z) % hh
    a = (0.30 + 0.55 * (0.5 + 0.5 * np.sin(t * tw + ph))) * z
    xi = np.clip(xs.astype(np.int32), 0, hw - 1)
    yi = np.clip(y.astype(np.int32), 0, hh - 1)
    buf = np.zeros((hh, hw), np.float32)
    np.add.at(buf, (yi, xi), a)
    np.add.at(buf, (np.clip(yi + 1, 0, hh - 1), xi), a * 0.35)
    buf = cv2.GaussianBlur(buf, (0, 0), 1.1)
    out = _up(buf, interp=cv2.INTER_LINEAR)
    return out[..., None] * np.array([255, 244, 226], np.float32) * intensity


# ------------------------------------------------------------------ 10. shockwave
def shockwave(canvas, t, t0, hot=(255, 160, 80), speed=1700.0, life=1.1, thick=26):
    """Expanding ring of light with a bright leading edge."""
    p = t - t0
    if p < 0 or p > life:
        return canvas
    k = p / life
    r = speed * p * (0.55 + 0.45 * k)
    a = (1 - k) ** 2.2
    for mult, aa in ((1.0, 1.0), (1.09, 0.35), (1.2, 0.14)):
        rad = int(r * mult)
        if rad < 4 or rad > W * 1.6:
            continue
        cv2.circle(canvas, (int(W / 2), int(H * 0.5)), rad,
                   tuple((np.array(hot, np.float32) * (0.20 * a * aa)).tolist()),
                   max(2, int(thick * (1 - k) * aa + 2)), cv2.LINE_AA)
    return canvas


# ------------------------------------------------------------------ 11. anamorphic flare
def anamorphic_flare(canvas, x, y, hot=(255, 190, 120), alpha=1.0):
    """Horizontal anamorphic streak + 4-point star — the 'expensive lens' tell."""
    if alpha <= 0.004:
        return canvas
    h, w = H // 2, W // 2
    xs, ys = _grid(w, h)
    dx = (xs - x) * (W / H)
    dy = ys - y
    streak = np.exp(-(dy * 330.0) ** 2) * np.exp(-(dx * 5.2) ** 2)
    star = np.exp(-(np.abs(dx) * 40 + np.abs(dy) * 40) ** 2)
    core = np.exp(-((dx * dx + dy * dy) * 20000.0))
    v = (streak * 0.45 + star * 0.14 + core * 0.55) * alpha
    img = _up((v * 120.0)[..., None] * np.array(hot, np.float32) / 255.0, interp=cv2.INTER_LINEAR)
    canvas += img
    return canvas


# ------------------------------------------------------------------ 12. zoom blur
def zoom_blur(img, amount=1.0, steps=8):
    if amount <= 0.01:
        return img
    out = img.astype(np.float32)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    cx, cy = W / 2, H / 2
    for i in range(1, steps + 1):
        s = 1.0 + amount * 0.055 * i / steps
        mx = (xx - cx) / s + cx
        my = (yy - cy) / s + cy
        out += cv2.remap(img, mx, my, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT)
    return out / (steps + 1)


# ------------------------------------------------------------------ 13. halation
def bloom_halation(canvas, bloom=0.95, bloom_thr=130, halo=0.34, halo_thr=140):
    """Single-pass bloom + film halation.

    Both used to downscale/blur/upscale the same bright pass twice (~450 ms).
    The bright pass is computed once at 1/4 resolution; two Gaussian widths are
    taken from it (tight bloom, wide warm halation) and added back with tints.
    """
    # bright pass at quarter resolution straight from the full frame (no full-res max())
    small_src = cv2.resize(canvas, (W // 4, H // 4), interpolation=cv2.INTER_AREA)
    lum = small_src.max(axis=2)
    m = np.clip((lum - bloom_thr) / (255 - bloom_thr), 0, 1)
    small = small_src * m[..., None]
    tight = cv2.GaussianBlur(small, (0, 0), 5.0)
    wide = cv2.GaussianBlur(small, (0, 0), 13.0)
    up_t = _up(tight, interp=cv2.INTER_LINEAR)
    up_w = _up(wide, interp=cv2.INTER_LINEAR)
    warm = np.array([1.0, 0.60, 0.34], np.float32)
    return canvas + up_t * bloom + up_w * warm[None, None, :] * halo


def halation(canvas, strength=0.5, threshold=150, sigma=22):
    """Film halation: warm bleed around hot highlights — the 'shot on film' tell."""
    lum = canvas.max(axis=2)
    m = np.clip((lum - threshold) / (255 - threshold), 0, 1)
    small = cv2.resize(canvas * m[..., None], (W // 4, H // 4), interpolation=cv2.INTER_AREA)
    halo = cv2.GaussianBlur(small, (0, 0), sigma / 4.0)
    halo = _up(halo, interp=cv2.INTER_LINEAR)
    tint = np.array([1.0, 0.62, 0.36], np.float32)
    return canvas + halo * tint[None, None, :] * strength


# ------------------------------------------------------------------ 14. gate weave
def gate_weave(img, t):
    """Sub-pixel vertical breathing like a real camera gate."""
    dy = 0.55 * math.sin(t * 1.7) + 0.35 * math.sin(t * 0.61 + 1.2)
    dx = 0.40 * math.sin(t * 0.83 + 0.4)
    M = np.float32([[1, 0, dx], [0, 1, dy]])
    return cv2.warpAffine(img, M, (W, H), flags=cv2.INTER_LINEAR,
                          borderMode=cv2.BORDER_REFLECT)


# ------------------------------------------------------------------ 15. hero plate
def deep_space(t, hot=(255, 180, 100), intensity=1.0, seed=17, neb_amt=0.55,
               vortex_amt=0.35, stars_amt=0.55):
    """Layered hero background: nebula + slow vortex + stars + god rays."""
    out = nebula(t, hot=hot, seed=seed, intensity=neb_amt)
    out += vortex(t * 0.55, hot=tuple(np.array(hot) * 0.85), seed=seed + 3,
                  intensity=vortex_amt)
    out += stars(t, intensity=stars_amt, seed=seed + 5)
    out += god_rays(t, hot=hot, intensity=0.35, cy=0.12, seed=seed + 9)
    return out * intensity


# ------------------------------------------------------------------ 16. kinetic type plate
def speed_lines(t, hot=(255, 210, 150), intensity=1.0, n=90, speed=2600.0):
    """Radial warp-speed streaks — instant 'we are moving fast' read."""
    out = np.zeros((H, W), np.float32)
    rng = np.random.default_rng(77)
    ang = rng.uniform(0, 6.28318, n)
    rad = rng.uniform(0.0, 1.0, n)
    ln = rng.uniform(90, 420, n)
    al = rng.uniform(0.25, 1.0, n)
    cx, cy = W / 2, H * 0.5
    for i in range(n):
        p = (rad[i] + t * speed / 1200.0 * (0.5 + rad[i])) % 1.15
        r0 = p * W * 0.9
        x0, y0 = cx + math.cos(ang[i]) * r0, cy + math.sin(ang[i]) * r0
        x1, y1 = cx + math.cos(ang[i]) * (r0 + ln[i]), cy + math.sin(ang[i]) * (r0 + ln[i])
        fade = min(1.0, p * 1.6) * (1 - max(0.0, p - 0.75) / 0.4)
        cv2.line(out, (int(x0), int(y0)), (int(x1), int(y1)), float(al[i] * 0.20 * max(0, fade)), 2, cv2.LINE_AA)
    out = cv2.GaussianBlur(out, (0, 0), 0.7)
    return out[..., None] * np.array(hot, np.float32)[None, None, :] * intensity
