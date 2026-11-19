"""Core rendering utilities: Arabic shaping (HarfBuzz + FreeType), text layers,
glow, gradients, easing, and frame compositing helpers.
"""
import functools
import math

import cv2
import numpy as np
import uharfbuzz as hb
import freetype as ft

W, H = 1080, 1920
FPS = 30

# ---------------------------------------------------------------- palette
GOLD_LIGHT = np.array([255, 236, 176], np.float32)
GOLD = np.array([232, 184, 75], np.float32)
GOLD_DEEP = np.array([178, 122, 32], np.float32)
IVORY = np.array([246, 243, 236], np.float32)
INK = np.array([6, 8, 13], np.float32)


# ---------------------------------------------------------------- fonts
class Font:
    _cache = {}

    def __init__(self, path):
        self.path = path
        self.blob = hb.Blob.from_file_path(path)
        self.hb_face = hb.Face(self.blob)
        self.upem = self.hb_face.upem
        self.ft_face = ft.Face(path)

    @classmethod
    def get(cls, path):
        if path not in cls._cache:
            cls._cache[path] = cls(path)
        return cls._cache[path]

    def shape(self, text, px, features=None):
        f = hb.Font(self.hb_face)
        f.scale = (int(px), int(px))
        buf = hb.Buffer()
        buf.add_str(text)
        buf.guess_segment_properties()
        f.set_variations({"wght": 700}) if False else None
        hb.shape(f, buf, features or {})
        return buf.glyph_infos, buf.glyph_positions

    def advance(self, text, px):
        infos, pos = self.shape(text, px)
        return sum(p.x_advance for p in pos)

    def ink(self, text, px):
        """Return (width, height, above baseline, below baseline) in px."""
        infos, pos = self.shape(text, px)
        self.ft_face.set_char_size(int(px * 64))
        x = 0
        xmin, ymin, xmax, ymax = 1e9, 1e9, -1e9, -1e9
        for i, p in enumerate(pos):
            self.ft_face.load_glyph(infos[i].codepoint, ft.FT_LOAD_RENDER)
            g = self.ft_face.glyph
            if g.bitmap.width:
                x0 = x + p.x_offset + g.bitmap_left
                y0 = -(p.y_offset + g.bitmap_top)
                xmin = min(xmin, x0)
                xmax = max(xmax, x0 + g.bitmap.width)
                ymin = min(ymin, y0)
                ymax = max(ymax, y0 + g.bitmap.rows)
            x += p.x_advance
        if xmin > xmax:
            return 1, 1, 0, 0
        return int(xmax - xmin), int(ymax - ymin), -ymin, ymax

    def render_mask(self, text, px, pad=40):
        """Render text to a float mask (0..1) plus metadata.

        Returns dict(mask=HxW float32, ox, oy) where (ox, oy) is the offset of
        the mask's top-left corner relative to the pen origin (0,0 = baseline
        start of the run).
        """
        infos, pos = self.shape(text, px)
        self.ft_face.set_char_size(int(px * 64))
        # bounds
        x = 0
        xmin, ymin, xmax, ymax = 1e9, 1e9, -1e9, -1e9
        blits = []
        for i, p in enumerate(pos):
            self.ft_face.load_glyph(infos[i].codepoint, ft.FT_LOAD_RENDER)
            g = self.ft_face.glyph
            bw, bh, bpitch = int(g.bitmap.width), int(g.bitmap.rows), int(g.bitmap.pitch)
            raw = np.asarray(g.bitmap.buffer, np.uint8).ravel().copy()
            if bw:
                bx = x + p.x_offset + g.bitmap_left
                by = -(p.y_offset + g.bitmap_top)
                blits.append((bx, by, raw, bw, bh, bpitch))
                xmin = min(xmin, bx)
                xmax = max(xmax, bx + g.bitmap.width)
                ymin = min(ymin, by)
                ymax = max(ymax, by + g.bitmap.rows)
            x += p.x_advance
        if not blits:
            return dict(mask=np.zeros((1, 1), np.float32), ox=0, oy=0, w=1, h=1)
        canvas = np.zeros((ymax - ymin, xmax - xmin), np.float32)
        for bx, by, arr, bw, bh, bpitch in blits:
            if bw == 0 or bh == 0:
                continue
            flat = arr.reshape(bh, bpitch)[:, :bw] if bpitch != bw else arr.reshape(bh, bw)
            a = flat.astype(np.float32) / 255.0
            y0, x0 = by - ymin, bx - xmin
            sub = canvas[y0:y0 + bh, x0:x0 + bw]
            np.maximum(sub, a, out=sub)
        return dict(mask=canvas, ox=xmin, oy=ymin, w=canvas.shape[1], h=canvas.shape[0],
                    adv=x, asc=ymin, desc=ymax)


# ---------------------------------------------------------------- helpers
def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a + 1e-9), 0, 1)
    return t * t * (3 - 2 * t)


def ease_out_cubic(t):
    t = np.clip(t, 0, 1)
    return 1 - (1 - t) ** 3


def ease_in_cubic(t):
    t = np.clip(t, 0, 1)
    return t ** 3


def ease_out_expo(t):
    t = np.clip(t, 0, 1)
    return 1 - 2 ** (-10 * t)


def ease_in_out(t):
    t = np.clip(t, 0, 1)
    return 3 * t * t - 2 * t * t * t


def ease_out_back(t, s=1.7):
    t = np.clip(t, 0, 1) - 1
    return 1 + (s + 1) * t ** 3 + s * t ** 2


def lerp(a, b, t):
    return a + (b - a) * t


def mix_color(c1, c2, t):
    return c1 + (c2 - c1) * float(np.clip(t, 0, 1))


def gaussian(img, sigma):
    if sigma <= 0.3:
        return img
    k = int(sigma * 4) | 1
    return cv2.GaussianBlur(img, (k, k), sigma, borderType=cv2.BORDER_REFLECT)


def fast_glow(mask, sigma, scale=None):
    """Blurred version of a mask (padded so no border wrap-around)."""
    h, w = mask.shape
    s = sigma if sigma else 8
    pad = int(s * 2.6) + 4
    m = np.pad(mask, ((pad, pad), (pad, pad)), mode='constant', constant_values=0)
    if scale is None:
        scale = 4 if max(h, w) < 900 else 2
    scale = max(1, int(scale))
    if scale > 1:
        sh, sw = max(4, m.shape[0] // scale), max(4, m.shape[1] // scale)
        small = cv2.resize(m, (sw, sh), interpolation=cv2.INTER_AREA)
        small = cv2.GaussianBlur(small, (0, 0), max(0.6, s / scale))
        out = cv2.resize(small, (m.shape[1], m.shape[0]), interpolation=cv2.INTER_LINEAR)
    else:
        out = cv2.GaussianBlur(m, (0, 0), s)
    return out[pad:pad + h, pad:pad + w]


# ---------------------------------------------------------------- text layer
class TextLayer:
    """A rendered, styled Arabic text (possibly multi-line)."""

    def __init__(self, text, font_path, px, color=None, gradient=None, align="center",
                 line_gap=0.16, stroke=0, stroke_color=None, track=0.0, shadow=None):
        self.text = text
        self.font = Font.get(font_path)
        self.px = px
        self.color = GOLD_LIGHT if color is None else np.asarray(color, np.float32)
        self.gradient = gradient  # (c1, c2) vertical
        self.align = align
        self.line_gap = line_gap
        self.stroke = stroke
        self.stroke_color = GOLD if stroke_color is None else np.asarray(stroke_color, np.float32)
        self.track = track
        self.shadow = shadow
        self._build()

    def _build(self):
        lines = self.text.split("\n")
        rendered = []
        for ln in lines:
            r = self.font.render_mask(ln, self.px)
            rendered.append(r)
        gap = int(self.px * self.line_gap)
        # baseline-to-baseline: asc + desc metrics per line
        total_h = 0
        tops = []
        for r in rendered:
            tops.append(total_h)
            total_h += r["h"] + gap
        total_h -= gap
        widths = [r["w"] for r in rendered]
        w = max(widths)
        mask = np.zeros((total_h, w), np.float32)
        for r, top, lw in zip(rendered, tops, widths):
            x0 = 0 if self.align == "left" else (w - lw) // 2
            sub = mask[top:top + r["h"], x0:x0 + r["w"]]
            np.maximum(sub, r["mask"], out=sub)
        self.mask = mask
        self.w, self.h = w, total_h
        self.line_rows = [(tops[i], rendered[i]["h"]) for i in range(len(rendered))]

    def stamp(self, canvas, x, y, alpha=1.0, scale=1.0, color=None, glow=0.0,
              glow_color=None, glow_sigma=None, tint=None, rot=0.0):
        """Composite onto canvas (H,W,3 float32) with top-left of the text box at (x,y)."""
        if alpha <= 0.002:
            return
        mask = self.mask
        if abs(scale - 1.0) > 0.004:
            nw = max(1, int(round(self.w * scale)))
            nh = max(1, int(round(self.h * scale)))
            interp = cv2.INTER_AREA if scale < 1 else cv2.INTER_LINEAR
            mask = cv2.resize(mask, (nw, nh), interpolation=interp)
        h, w = mask.shape
        x0, y0 = int(round(x)), int(round(y))
        # clipping
        cx0, cy0 = max(0, x0), max(0, y0)
        cx1, cy1 = min(canvas.shape[1], x0 + w), min(canvas.shape[0], y0 + h)
        if cx1 <= cx0 or cy1 <= cy0:
            return
        m = mask[cy0 - y0:cy1 - y0, cx0 - x0:cx1 - x0]
        base = color if color is not None else self.color
        base = np.asarray(base, np.float32)

        if self.gradient is not None:
            c1, c2 = self.gradient
            gcol = np.linspace(0, 1, h, dtype=np.float32)[:, None]
            grad = c1[None, None, :] * (1 - gcol[..., None]) + c2[None, None, :] * gcol[..., None]
            grad = cv2.resize(grad, (w, h), interpolation=cv2.INTER_NEAREST) if False else grad
            full_rgb = np.empty((h, w, 3), np.float32)
            full_rgb[:] = grad
            rgb = full_rgb[cy0 - y0:cy1 - y0, cx0 - x0:cx1 - x0]
        else:
            rgb = base

        if self.stroke > 0:
            # outline via dilate-erode difference
            k = max(1, int(self.stroke * 2) | 1)
            kern = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k))
            big = cv2.dilate(mask, kern)
            outline = np.clip(big - mask, 0, 1)
            o = outline[cy0 - y0:cy1 - y0, cx0 - x0:cx1 - x0]
            sc = np.asarray(self.stroke_color, np.float32)
            canvas[cy0:cy1, cx0:cx1] = canvas[cy0:cy1, cx0:cx1] * (1 - (o * alpha)[..., None]) + \
                sc * (o * alpha)[..., None]

        if glow > 0.01:
            sigma = glow_sigma if glow_sigma else max(6, self.px * 0.22)
            pad = int(sigma * 2.4) + 6
            # glow needs room around the glyph box, otherwise the falloff is
            # clipped into a visible rectangle
            pm = np.pad(mask, ((pad, pad), (pad, pad)), mode="constant")
            g = fast_glow(pm, sigma)
            g = np.clip(g * 1.9, 0, 1)
            gx0, gy0 = x0 - pad, y0 - pad
            gx1 = min(canvas.shape[1], gx0 + g.shape[1])
            gy1 = min(canvas.shape[0], gy0 + g.shape[0])
            if gx1 > max(0, gx0) and gy1 > max(0, gy0):
                gc0, gcy0 = max(0, gx0), max(0, gy0)
                gm = g[gcy0 - gy0:gy1 - gy0, gc0 - gx0:gx1 - gx0]
                gc = np.asarray(glow_color, np.float32) if glow_color is not None else base
                canvas[gcy0:gy1, gc0:gx1] += gc[None, None, :] * (gm * glow * alpha)[..., None]

        a = (m * alpha)[..., None]
        if tint is not None:
            rgb = rgb * (1 - tint[3]) + np.asarray(tint[:3], np.float32) * tint[3]
        canvas[cy0:cy1, cx0:cx1] = canvas[cy0:cy1, cx0:cx1] * (1 - a) + rgb * a

    def stamp_centered(self, canvas, cx, cy, **kw):
        sc = kw.get("scale", 1.0)
        self.stamp(canvas, cx - self.w * sc / 2, cy - self.h * sc / 2, **kw)


# ---------------------------------------------------------------- misc frame ops
def add_grain(canvas, rng, amount=0.03, scale=1):
    h, w = canvas.shape[0] // scale, canvas.shape[1] // scale
    n = rng.normal(0, 1, (h, w)).astype(np.float32)
    if scale > 1:
        n = np.repeat(np.repeat(n, scale, axis=0), scale, axis=1)
    n = n[:canvas.shape[0], :canvas.shape[1]]
    canvas += n[..., None] * amount


def vignette_map(strength=1.0, power=2.2):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    nx = (xx - W / 2) / (W / 2)
    ny = (yy - H / 2) / (H / 2)
    r = np.sqrt(nx ** 2 + ny ** 2) / 1.4142
    v = 1 - strength * np.clip(r ** power, 0, 1)
    return np.clip(v, 0, 1).astype(np.float32)


def chroma_shift(img, amount):
    """Shift red and blue channels horizontally for an aberration look."""
    if abs(amount) < 0.5:
        return img
    a = int(round(amount))
    out = img.copy()
    out[:, :, 0] = np.roll(img[:, :, 0], a, axis=1)
    out[:, :, 2] = np.roll(img[:, :, 2], -a, axis=1)
    return out


_FLASH = {}


def radial_flash(canvas, cx, cy, radius, color, strength):
    key = (int(cx), int(cy), int(radius))
    if key not in _FLASH:
        sh, sw = H // 4, W // 4
        yy, xx = np.mgrid[0:sh, 0:sw].astype(np.float32)
        d = np.sqrt((xx - cx / 4) ** 2 + (yy - cy / 4) ** 2)
        m = np.exp(-(d / (radius / 4)) ** 2)
        _FLASH[key] = cv2.resize(m, (W, H), interpolation=cv2.INTER_LINEAR)
    m = _FLASH[key]
    canvas += (np.asarray(color, np.float32)[None, None, :] * m[..., None]) * strength
