"""Procedural cinematic graphics: 3D torus-knot hero object, light fields,
particles, bloom, grade. Everything is generated with numpy/cv2 (no stock media).
"""
import math

import cv2
import numpy as np

from .core import H, W, smoothstep, gaussian, radial_flash

HW, HH = W // 2, H // 2  # half-res canvas dimensions
CX, CY = HW // 2, HH // 2  # centre of the half-res canvas


# ------------------------------------------------------------------ torus knot
def torus_knot_points(p=2, q=3, n=520, R=1.0, r=0.34):
    t = np.linspace(0, 2 * np.pi, n, endpoint=False)
    ct, st = np.cos(q * t), np.sin(q * t)
    rr = R + r * np.cos(p * t)
    x = rr * ct
    y = rr * ct * 0 + rr * st
    z = r * np.sin(p * t)
    return np.stack([x, y, z], -1)


def project(pts, rot_y, rot_x, radius=300.0, cx=None, cy=None, persp=0.55):
    """Cheap 3D: rotate, then soft perspective divide + scale to `radius` px."""
    cx = CX if cx is None else cx
    cy = CY if cy is None else cy
    cy_, sy_ = math.cos(rot_y), math.sin(rot_y)
    cx_, sx_ = math.cos(rot_x), math.sin(rot_x)
    Ry = np.array([[cy_, 0, sy_], [0, 1, 0], [-sy_, 0, cy_]])
    Rx = np.array([[1, 0, 0], [0, cx_, -sx_], [0, sx_, cx_]])
    p3 = pts @ (Rx @ Ry).T
    rmax = 1.34
    z = (p3[:, 2] / rmax) * persp + 1.0          # 1 ± persp
    k = radius / rmax / z
    sx_ = cx + p3[:, 0] * k
    sy_ = cy - p3[:, 1] * k
    return np.stack([sx_, sy_], -1), z


def draw_knot(layer, rot_y, rot_x, radius=300.0, color=(255, 205, 130), alpha=1.0,
              thick=2, n=520, R=1.0, r=0.3, p=2, q=3, cx=None, cy=None, glow=0.55):
    """Neon tube: crisp core + additive halo, depth-shaded segments."""
    pts = torus_knot_points(p, q, n, R, r)
    scr, depth = project(pts, rot_y, rot_x, radius, cx=cx, cy=cy)
    dmin, dmax = depth.min(), depth.max()
    k = np.ones(9) / 9
    ds = np.convolve(np.concatenate([depth[-8:], depth, depth[:8]]), k, mode="same")[8:-8]
    shade = 0.35 + 0.95 * (ds - dmin) / (dmax - dmin + 1e-9)
    segs = []
    for i in range(n):
        j = (i + 1) % n
        c = float(np.clip(shade[i], 0.05, 1.35)) * alpha
        segs.append((int(scr[i][0]), int(scr[i][1]), int(scr[j][0]), int(scr[j][1]),
                     (float(color[0]) * c, float(color[1]) * c, float(color[2]) * c)))
    halo = (float(color[0]) * 0.16 * alpha, float(color[1]) * 0.16 * alpha,
            float(color[2]) * 0.16 * alpha)
    for (x0, y0, x1, y1, _c) in segs:
        cv2.line(layer, (x0, y0), (x1, y1), halo, thick * 9, cv2.LINE_AA)
    for (x0, y0, x1, y1, _c) in segs:
        cv2.line(layer, (x0, y0), (x1, y1), halo, thick * 4, cv2.LINE_AA)
    for (x0, y0, x1, y1, c) in segs:
        cv2.line(layer, (x0, y0), (x1, y1), c, thick, cv2.LINE_AA)


# ------------------------------------------------------------------ particles
class Dust:
    def __init__(self, n=260, seed=1):
        rng = np.random.default_rng(seed)
        self.x = rng.uniform(0, HW, n)
        self.y = rng.uniform(0, HH, n)
        self.z = rng.uniform(0.15, 1.0, n)
        self.vx = rng.uniform(-8, 8, n)
        self.vy = rng.uniform(-16, -3, n)
        self.tw = rng.uniform(0.5, 2.2, n)
        self.ph = rng.uniform(0, 6.28, n)

    def step(self, dt):
        self.x = (self.x + self.vx * dt) % HW
        self.y = (self.y + self.vy * dt) % HH


def draw_dust(layer, dust, t, color, size=2.4, alpha=1.0):
    n = len(dust.x)
    tw = 0.45 + 0.55 * np.sin(t * dust.tw + dust.ph)
    a = (tw * dust.z * alpha * 255).astype(np.int32)
    xs = dust.x.astype(np.int32)
    ys = dust.y.astype(np.int32)
    for i in range(n):
        aa = int(a[i])
        if aa < 6:
            continue
        c = (int(color[0] * aa / 255), int(color[1] * aa / 255), int(color[2] * aa / 255))
        rad = 1 if dust.z[i] < 0.6 else 2
        cv2.circle(layer, (int(xs[i]), int(ys[i])), rad, c, -1, cv2.LINE_AA)


# ------------------------------------------------------------------ light field
def light_field(t, seed=0, hue_a=(40, 90, 130), hue_b=(10, 20, 40), intensity=1.0,
                drift=(0.0, 0.0), n_blobs=3, hot=(255, 190, 90)):
    """Low-res animated gradient fog, upscaled. Returns HxWx3 float32 (0..255)."""
    rng = np.random.default_rng(seed)
    centers = rng.uniform(0, 1, (n_blobs, 2))
    speeds = rng.uniform(0.05, 0.16, n_blobs)
    phases = rng.uniform(0, 6.28, n_blobs)
    gy, gx = np.mgrid[0:HH // 8, 0:HW // 8].astype(np.float32)
    gx /= (HW // 8)
    gy /= (HH // 8)
    field = np.zeros((HH // 8, HW // 8), np.float32)
    for i in range(n_blobs):
        cx = 0.5 + 0.42 * math.sin(t * speeds[i] + phases[i]) + drift[0]
        cy = 0.5 + 0.40 * math.cos(t * speeds[i] * 0.8 + phases[i] * 1.7) + drift[1]
        d2 = (gx - cx) ** 2 + (gy - cy) ** 2
        field += np.exp(-d2 / (0.055 + 0.02 * i))
    field = np.clip(field / max(1.0, field.max()), 0, 1) * intensity
    a = np.array(hue_a, np.float32)
    b = np.array(hue_b, np.float32)
    img = b[None, None, :] + (a - b)[None, None, :] * field[..., None]
    # hot core glow
    hotm = smoothstep(0.55, 1.0, field)[..., None]
    img = img + np.array(hot, np.float32)[None, None, :] * hotm * 0.35 * intensity
    img = cv2.resize(img, (W, H), interpolation=cv2.INTER_CUBIC)
    return img


def rings(canvas, cx, cy, r0, t, color, alpha=0.5, count=4, speed=40, thick=2):
    for i in range(count):
        r = (r0 + i * 70 + t * speed) % (W * 0.9)
        a = alpha * (1 - r / (W * 0.9))
        if a <= 0.01:
            continue
        cv2.circle(canvas, (int(cx), int(cy)), int(r), tuple(float(c * a) for c in color),
                   thick, cv2.LINE_AA)


def light_ray(canvas, angle, t, color, alpha=0.15, width=180):
    """Soft diagonal light band (tiny buffer, no hard edges)."""
    sw, sh = W // 10, H // 6
    xs = np.arange(sw, dtype=np.float32) / sw
    ys = np.arange(sh, dtype=np.float32) / sh
    center = 0.42 + 0.16 * math.sin(t * 0.6 + angle)
    band = np.exp(-(((xs[None, :] - center) / 0.22) ** 2)) * np.ones((sh, 1), np.float32)
    band *= (0.35 + 0.65 * ys[:, None])
    band = cv2.resize(band, (W, H), interpolation=cv2.INTER_LINEAR)
    canvas += band[..., None] * np.array(color, np.float32)[None, None, :] * alpha
def grade(canvas, saturation=1.05, contrast=1.04, lift=(0.0, 0.0, 0.0), gain=(1.0, 1.0, 1.0)):
    mid = canvas * gain[None, None, :] + np.array(lift, np.float32)[None, None, :] * 255
    mid = (mid - 128) * contrast + 128
    gray = mid.mean(axis=2, keepdims=True)
    out = gray + (mid - gray) * saturation
    return out


def bloom(canvas, threshold=185, strength=0.55, sigma=11):
    lum = canvas.max(axis=2)
    m = np.clip((lum - threshold) / (255 - threshold), 0, 1)
    small = cv2.resize(canvas * m[..., None], (W // 3, H // 3), interpolation=cv2.INTER_AREA)
    small = cv2.GaussianBlur(small, (0, 0), sigma / 3)
    b = cv2.resize(small, (W, H), interpolation=cv2.INTER_LINEAR)
    return canvas + b * strength


def scanlines(canvas, strength=0.04):
    rows = (np.sin(np.arange(H) * math.pi / 3.0) * 0.5 + 0.5).astype(np.float32)
    canvas *= (1 - strength + strength * rows)[:, None, None]
    return canvas


def glitch_slice(canvas, t, rng, amount=1.0):
    if amount <= 0.01:
        return canvas
    n = int(3 + 6 * amount)
    out = canvas.copy()
    for _ in range(n):
        y0 = int(rng.integers(0, H - 20))
        hh = int(rng.integers(6, 90))
        dx = int(rng.normal(0, 40 * amount))
        if dx == 0:
            continue
        band = out[y0:y0 + hh]
        if dx > 0:
            band[:, dx:] = band[:, :-dx].copy()
        else:
            band[:, :dx] = band[:, -dx:].copy()
    return out


# ------------------------------------------------------------------ 3D grid floor
def draw_grid_floor(layer, t, color, alpha=0.35, horizon=HH * 0.62, spacing=46, speed=1.0):
    """Perspective receding neon grid — sells depth under the hero object."""
    fade = np.zeros_like(layer)
    for i in range(1, 26):
        z = (i * spacing + t * 60 * speed) % (spacing * 25)
        y = horizon + (HH * 0.55) * (z / (z + 120))
        a = alpha * (1 - z / (spacing * 25)) * 0.9
        if a <= 0.01:
            continue
        cv2.line(fade, (0, int(y)), (HW, int(y)), tuple(float(c * a) for c in color), 1, cv2.LINE_AA)
    for i in range(-14, 15):
        x_bottom = HW / 2 + i * spacing * 2.2
        x_top = HW / 2 + i * spacing * 0.28
        cv2.line(fade, (int(x_top), int(horizon)), (int(x_bottom), HH),
                 tuple(float(c * alpha * 0.45) for c in color), 1, cv2.LINE_AA)
    layer += fade
