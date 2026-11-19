"""Text shaping helpers on top of core: cached layers, RTL word layout,
multi-style cards and kinetic reveal utilities.
"""
import functools
import math
import re

import cv2
import numpy as np

from .core import (Font, TextLayer, W, H, GOLD, GOLD_LIGHT, GOLD_DEEP, IVORY,
                   ease_out_cubic, ease_out_expo, ease_out_back, smoothstep, fast_glow)

AR_BOLD = "fonts/Tajawal-ExtraBold.ttf"
AR_REG = "fonts/Tajawal-Bold.ttf"
AR_CAIRO = "fonts/Cairo[slnt,wght].ttf"
LAT_BOLD = "fonts/Anton-Regular.ttf"
LAT_COND = "fonts/BebasNeue-Regular.ttf"


_DIGIT = re.compile(r"(\d+)")


_ALLDIG = re.compile(r"([\d\u0660-\u0669]+(?:[.,][\d\u0660-\u0669]+)?)")


def bidi_fix(text):
    """Digits are LTR inside an RTL line: mirror the run and keep token order.

    HarfBuzz lays the whole run right-to-left, so a digit token comes out
    reversed (e.g. ٣٧ -> ٧٣). We reverse each token's glyphs and then reverse the
    order of the tokens so numbers read left-to-right while the Arabic stays RTL.
    """
    parts = _ALLDIG.split(text)
    nums = [_ALLDIG.fullmatch(p) is not None for p in parts]
    out = []
    for p, isnum in zip(parts, nums):
        out.append(p[::-1] if isnum else p)
    # reorder consecutive number tokens together with their order preserved
    return "".join(out)


@functools.lru_cache(maxsize=512)
def layer(text, font=AR_BOLD, px=90, stroke=0.0, track=0.0):
    return TextLayer(text, font, px, stroke=stroke, track=track)


@functools.lru_cache(maxsize=512)
def grad_layer(text, font=AR_BOLD, px=90, c1=None, c2=None, stroke=0.0):
    a = tuple(GOLD_LIGHT) if c1 is None else c1
    b = tuple(GOLD) if c2 is None else c2
    L = TextLayer(text, font, px, gradient=(np.array(a, np.float32), np.array(b, np.float32)),
                  stroke=stroke, stroke_color=tuple(GOLD_DEEP))
    return L


class Line:
    """One line of Arabic text laid out RTL, with per-word reveal."""

    def __init__(self, text, font=AR_BOLD, px=80, gap=0.26, highlight=None,
                 highlight_color=None, solid=None, grad=None):
        self.text = text
        self.px = px
        self.words = [w for w in text.split(" ") if w]
        self.gap = gap
        self.font = font
        self.highlight = set(highlight or [])
        self.highlight_color = np.array(highlight_color if highlight_color is not None
                                        else (255, 255, 255), np.float32)
        self.solid = solid is not None or grad is not None
        if grad is not None:
            c1 = np.array(grad[0], np.float32)
            c2 = np.array(grad[1], np.float32)
            self.layers = [TextLayer(bidi_fix(w), font, px, gradient=(c1, c2)) for w in self.words]
        elif self.solid:
            base = np.array(solid, np.float32)
            self.layers = [TextLayer(bidi_fix(w), font, px, color=base) for w in self.words]
        else:
            self.layers = [grad_layer(bidi_fix(w), font, px) for w in self.words]
        self.gap_px = int(px * gap)
        self.width = sum(l.w for l in self.layers) + self.gap_px * (len(self.layers) - 1)
        self.height = max(l.h for l in self.layers)

    def draw(self, canvas, cx, cy, alpha=1.0, reveal=None, stagger=0.06, t=0.0,
             rise=28, scale_in=0.9, color=None, pop=0.0, glow=0.0,
             highlight_color=None, word_alpha=None, jitter=0.0, glow_color=None,
             glow_sigma=None):
        """Draw centered at (cx,cy). reveal=float>=0 starts staggered word fade."""
        n = len(self.layers)
        x_right = cx + self.width / 2
        for i, L in enumerate(self.layers):
            # RTL: first word is right-most
            x0 = x_right - L.w
            a = alpha
            sc = 1.0
            dy = 0.0
            if reveal is not None:
                st = reveal - i * stagger
                p = float(np.clip(st / 0.32, 0, 1))
                if p <= 0:
                    x_right -= L.w + self.gap_px
                    continue
                e = ease_out_cubic(p)
                a *= e
                dy = rise * (1 - e)
                sc = scale_in + (1 - scale_in) * e
            if word_alpha is not None:
                a *= word_alpha[i]
            if sc != 1.0:
                L2 = L
                sw = max(1, int(L2.w * sc))
                sh = max(1, int(L2.h * sc))
            else:
                sw, sh = L.w, L.h
            col = color
            if i in self.highlight:
                col = highlight_color if highlight_color is not None else self.highlight_color
            if pop > 0:
                a = min(1.0, a * (1 + pop))
            yy = cy - L.h / 2 + dy
            L.stamp(canvas, x0 + (L.w - sw) / 2, yy + (L.h - sh) / 2, alpha=min(1, a),
                    scale=sc, color=col, glow=glow, glow_color=glow_color,
                    glow_sigma=glow_sigma)
            x_right -= L.w + self.gap_px

    def draw_wide(self, canvas, cx, cy, alpha=1.0, progress=1.0, color=None, glow=0.0):
        """Reveal left→right by clipping width (for sweeping statements)."""
        x0 = cx - self.width / 2
        x1 = x0 + self.width * progress
        clip = canvas[int(cy - self.height):int(cy + self.height), int(max(0, x0)):int(max(0, x1))]
        if clip.size == 0:
            return
        tmp = np.zeros_like(clip)
        self.draw(tmp, cx - max(0, x0) + 0, cy - int(cy - self.height), alpha=alpha,
                  color=color, glow=glow)
        canvas[int(cy - self.height):int(cy + self.height), int(max(0, x0)):int(max(0, x1))] = tmp


def stack(lines, canvas, cx, cy, alphas=None, gap=18):
    """Draw several Line objects vertically centered around cy."""
    hs = [l.height for l in lines]
    total = sum(hs) + gap * (len(lines) - 1)
    y = cy - total / 2
    for i, l in enumerate(lines):
        a = 1.0 if alphas is None else alphas[i]
        if a > 0.002:
            l.draw(canvas, cx, y + l.height / 2, alpha=a)
        y += l.height + gap


def number_badge(canvas, n, cx, cy, size=300, t=0.0, alpha=1.0, color=None, progress=1.0,
                 font=LAT_BOLD):
    """Huge ghosted numeral with a thin gold ring."""
    s = f"{n:02d}" if isinstance(n, int) else str(n)
    L = grad_layer(s, font, size)
    c = color if color is not None else tuple(GOLD * 0.55)
    L.stamp_centered(canvas, cx, cy, alpha=alpha * 0.22, color=c)
    # ring
    r = int(size * 0.72)
    cv2.ellipse(canvas, (int(cx), int(cy)), (r, r), 0, -90, -90 + 360 * progress,
                tuple(float(x) for x in GOLD), 3, cv2.LINE_AA)


def rule(canvas, cx, cy, width, t, alpha=1.0, color=None, thickness=3):
    c = color if color is not None else GOLD
    w = width * t
    if w < 2 or alpha <= 0.01:
        return
    x0, x1 = int(cx - w / 2), int(cx + w / 2)
    cv2.line(canvas, (x0, int(cy)), (x1, int(cy)), tuple(float(v * alpha) for v in c),
             thickness, cv2.LINE_AA)


def badge_text(canvas, text, cx, cy, px=54, font=LAT_COND, color=(255, 255, 255), alpha=1.0,
               pad=(34, 12), fill=(0, 0, 0), fill_alpha=0.35, radius=6):
    L = layer(text, font, px)
    w, h = L.w + pad[0] * 2, L.h + pad[1] * 2
    x0, y0 = int(cx - w / 2), int(cy - h / 2)
    if fill_alpha > 0:
        overlay = canvas.copy()
        cv2.rectangle(overlay, (x0, y0), (x0 + w, y0 + h), fill, -1)
        cv2.rectangle(overlay, (x0, y0), (x0 + w, y0 + h), tuple(GOLD * 0.8), 2, cv2.LINE_AA)
        canvas[:] = canvas * (1 - fill_alpha) + overlay * fill_alpha
    L.stamp(canvas, cx - L.w / 2, y0 + pad[1], alpha=alpha, color=color)
    return w, h
