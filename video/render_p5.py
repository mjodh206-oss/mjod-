"""Part-5 renderer — brutalist editorial.

This is a deliberate identity break from parts 1–4. Those were dark, neon,
space-opera: glowing type on a black nebula. Part 5 is **paper**: off-white
stock, hard black ink, red accents, torn edges, halftone dots, newsprint
rules. On a feed, that reads as a different *channel*, which is what makes a
series feel alive instead of repetitive.

Structure notes (retention):
  * 0.00-0.36  black screen, one white word slams in: «لا أحد»  (stop the thumb)
  * 0.36-1.30  ink splat + «سيقول لك هذا»  (finish the sentence)
  * 1.30-3.10  paper plate, numeral 7 + «حقائق مرة»  (stake the promise)
  * 3.10-5.40  torn page: «لا أحد يريد أن يسمعها»
  * 5.40-...   red plate: «والسابعة…»
  * then the countdown card «٧ حقائق مرة» on a paper plate = a second hook at
    ~7 s, for the people who arrived late and are deciding whether to stay.
"""
import math
import os
import subprocess
import sys

import cv2
import numpy as np

cv2.setNumThreads(1)

from .core import (W, H, FPS, IVORY, add_grain, chroma_shift, radial_flash,
                   smoothstep, ease_out_cubic)
from . import gfx
from . import shaders as SH
from .core import TextLayer
from .text import Line, layer, grad_layer, rule
from .timeline import SECTIONS, TOTAL, align, EP

PAPER = np.array([241, 237, 228], np.float32)      # warm newsprint
INK = np.array([16, 16, 20], np.float32)           # near-black ink
RED = np.array([214, 32, 24], np.float32)          # editorial red
RUST = np.array([188, 52, 18], np.float32)

TIP_COL = {t["n"]: tuple(t["col"]) for t in EP["tips"]}
TIP_WORDS = {t["n"]: t["words"] for t in EP["tips"]}
TIP_WHO = {t["n"]: t["who"] for t in EP["tips"]}
TIP_EN = {t["n"]: t["who_en"] for t in EP["tips"]}
TIP_Q1 = {t["n"]: t["quote"] for t in EP["tips"]}
TIP_Q2 = {t["n"]: t["quote2"] for t in EP["tips"]}
TIP_LOOK = {t["n"]: t.get("look", "paper_brutal") for t in EP["tips"]}
N_TIPS = len(EP["tips"])
OUTRO = EP["outro"]
REHOOK = EP.get("rehook") or {}
HOOK_WORDS = EP["hook_words"]

_ALIGN = {}
_HOOK_T = {}


def tip_times(sec):
    k = sec["idx"]
    if k not in _ALIGN:
        _ALIGN[k] = align(TIP_WORDS[k], sec["segs"], sec["vo_start"])
    return _ALIGN[k]


def hook_times(sec):
    if "hook" not in _HOOK_T:
        _HOOK_T["hook"] = align(HOOK_WORDS, sec["segs"], sec["vo_start"])
    return _HOOK_T["hook"]


def fit_px(text, max_w, base_px, font, min_px=44):
    px = base_px
    while px > min_px:
        if layer(text, font, px).w <= max_w:
            return px
        px -= 4
    return min_px


def sub(c, a, hot=(255, 255, 255)):
    """Additive plate helper (plates are additive, so clip to sane values)."""
    return np.clip(c + a, 0, 255)


# ------------------------------------------------------------------ plates
def plate(look, t, hot, q=0.0):
    """Return the base image for one law's look (paper or ink)."""
    if look == "paper_brutal":
        c = SH.paper(t)
        c = sub(c, SH.halftone(t * 0.4, hot=(0, 0, 0), intensity=0.10, step=15), hot)
        return c
    if look == "ink_splat":
        c = SH.paper(t, seed=81, vig=0.40)
        c = sub(c, SH.ink_splat(t * 0.18, hot=(198, 44, 26), intensity=0.55, seed=88, n=5))
        # a solid slab of ink: the section reads as a printed page turned dark
        c *= 0.16
        c += np.array([26, 22, 20], np.float32)[None, None, :]
        c = sub(c, SH.halftone(t * 0.4, hot=(120, 40, 30), intensity=0.10, step=17), hot)
        return c
    if look == "halftone":
        c = SH.paper(t, seed=61, vig=0.42)
        c = sub(c, SH.halftone(t, hot=(0, 0, 0), intensity=0.22, step=12, drift=0.10), hot)
        return c
    if look == "torn":
        c = SH.paper(t, seed=41)
        ink = SH.torn_edge(t, side="left", hot=(18, 18, 22), intensity=1.0, depth=0.10)
        ink2 = SH.torn_edge(t, side="right", hot=(18, 18, 22), intensity=1.0, depth=0.07, seed=9)
        edge = np.clip(ink + ink2, 0, 1) / 255.0
        return np.clip(c * (1 - edge), 0, 255)
    return SH.paper(t)


def vignette_plate(img, dark=0.5):
    ys = (np.arange(H, dtype=np.float32) / H)[:, None, None]
    xs = (np.arange(W, dtype=np.float32) / W)[None, :, None]
    d = ((xs - 0.5) ** 2 * 1.2 + (ys - 0.5) ** 2) * 3.2
    return img * np.clip(1.0 - dark * d, 0, 1)


def ink_text(canvas, text, font, px, x, y, alpha=1.0, color=None, centre=True,
             glow=0.0, glow_color=(214, 32, 24), glow_sigma=24, scale=1.0):
    """Black ink on paper — the inverse of parts 1-4 glowing type."""
    lab = layer(text, font, px)
    col = tuple(INK.tolist()) if color is None else color
    if centre:
        lab.stamp_centered(canvas, x, y, alpha=alpha, color=col,
                           glow=glow, glow_color=glow_color, glow_sigma=glow_sigma)
    else:
        lab.stamp(canvas, x, y, alpha=alpha, color=col,
                  glow=glow, glow_color=glow_color, glow_sigma=glow_sigma)
    return canvas


def red_bar(canvas, y, h_px, alpha=1.0, x0=0, x1=W, color=RED):
    if alpha <= 0.01:
        return canvas
    color = np.asarray(color, np.float32)
    y0, y1 = int(y - h_px / 2), int(y + h_px / 2)
    canvas[y0:y1, x0:x1] = canvas[y0:y1, x0:x1] * (1 - alpha) + color[None, None, :] * alpha
    return canvas


# ------------------------------------------------------------------ hook
def scene_hook(t, sec):
    """Five hard cuts in 12 s, all on measured word onsets."""
    p = t - sec["start"]
    times = hook_times(sec)
    nrm = lambda w: w.strip("….,؟:،")
    words = [nrm(w) for (w, a, b) in times]

    def at(phrase):
        pw = [nrm(w) for w in phrase.split()]
        for i in range(len(words) - len(pw) + 1):
            if words[i:i + len(pw)] == pw:
                return times[i][1], times[i + len(pw) - 1][2]
        return None, None

    t_nobody, _ = at("لا أحد")     # 0.30
    t_say, _ = at("سيقول")         # 0.80
    t_facts, _ = at("حقائق")       # 3.10
    t_want, _ = at("يريد")         # 4.60
    t_seventh, _ = at("والسابعة")  # 7.10
    t_break, _ = at("ستكسر")       # 8.37

    CUT_1 = 1.30
    CUT_2 = (t_facts or 3.79) - 0.10
    CUT_3 = (t_want or 5.07) + 1.72   # just after يسمعها has finished

    # ============================================ A · black screen, white word
    if p < CUT_1:
        c = np.zeros((H, W, 3), np.float32)
        c += SH.scan_bar(t, hot=(28, 28, 34), intensity=1.0, speed=0.9, thickness=0.24)
        k = smoothstep(t_nobody - 0.04, t_nobody + 0.03, p)
        L = grad_layer("لا أحد", "fonts/Tajawal-ExtraBold.ttf", 250,
                       c1=(255, 255, 252), c2=(232, 226, 214))
        L.stamp_centered(c, W / 2, H * 0.44, alpha=k,
                         scale=1.16 - 0.16 * ease_out_cubic(np.clip((p - t_nobody) / 0.3, 0, 1)),
                         glow=0.30, glow_color=(255, 255, 255), glow_sigma=40)
        k2 = smoothstep(t_say - 0.06, t_say + 0.04, p)
        ink_text(c, "سيقول لك هذا", "fonts/Tajawal-Bold.ttf", 92, W / 2, H * 0.565,
                 alpha=k2 * 0.92, color=(228, 222, 210))
        # a torn red band snaps in under the line
        if k2 > 0.02:
            red_bar(c, H * 0.635, 10, alpha=k2 * 0.95)
        return c

    # ============================================ B · paper + numeral 7
    if p < CUT_2:
        c = SH.paper(t, seed=31)
        c = sub(c, SH.halftone(t * 0.3, hot=(0, 0, 0), intensity=0.09, step=16))
        c = vignette_plate(c, 0.42)
        # red accent lives in the corner, never behind the type
        c = sub(c, SH.ink_splat(t * 0.25, hot=(214, 32, 24), intensity=0.30,
                                seed=45, n=3) * 0.35)
        k = smoothstep(1.30, 1.40, p)
        pop = 1.0 + 0.30 * (1 - ease_out_cubic(np.clip((p - 1.30) / 0.28, 0, 1)))
        num = TextLayer("7", "fonts/Anton-Regular.ttf", 660,
                        gradient=(np.array([26, 26, 32], np.float32),
                                  np.array([92, 20, 14], np.float32)))
        num.stamp_centered(c, W / 2, H * 0.30, alpha=k, scale=pop)
        # hazard rules top and bottom of the numeral block (editorial, not glow)
        red_bar(c, H * 0.415, 9, alpha=k * 0.95, x0=110, x1=W - 110)
        kf = smoothstep(1.42, 1.58, p)   # promise lands with the cut, not 2 s late
        ink_text(c, "سبع حقائق", "fonts/Tajawal-ExtraBold.ttf",
                 fit_px("سبع حقائق", 960, 150, "fonts/Tajawal-ExtraBold.ttf"),
                 W / 2, H * 0.545, alpha=kf)
        ink_text(c, "مرة", "fonts/Tajawal-ExtraBold.ttf", 150, W / 2, H * 0.665,
                 alpha=kf, color=tuple(RED.tolist()))
        red_bar(c, H * 0.735, 9, alpha=kf * 0.95, x0=110, x1=W - 110)
        # corner label, like a magazine folio
        ink_text(c, "الجزء الخامس", "fonts/Tajawal-Bold.ttf", 34, 76, 150,
                 alpha=0.55, centre=False)
        return c

    # ============================================ C · torn page, the refusal
    if p < CUT_3:
        c = plate("torn", t, INK)
        c = vignette_plate(c, 0.45)
        k = smoothstep(CUT_2 + 0.06, CUT_2 + 0.22, p)
        ink_text(c, "لا أحد يريد", "fonts/Tajawal-ExtraBold.ttf", 132, W / 2, H * 0.44,
                 alpha=k)
        ink_text(c, "أن يسمعها", "fonts/Tajawal-ExtraBold.ttf", 132, W / 2, H * 0.545,
                 alpha=k, color=tuple(RED.tolist()))
        k2 = smoothstep(CUT_2 + 0.42, CUT_2 + 0.72, p)
        ink_text(c, "وهذا سبب أنك تحتاجها", "fonts/Tajawal-Bold.ttf", 52,
                 W / 2, H * 0.655, alpha=k2 * 0.85)
        # a red rule under the refusal, so the empty plate is never empty
        red_bar(c, H * 0.592, 6, alpha=k * 0.95, x0=150, x1=W - 150)
        return c

    # ============================================ D · red plate, the seventh
    if p < (t_break or 8.37) - 0.10:
        c = np.zeros((H, W, 3), np.float32)
        c[:, :] = RED[None, None, :] * 0.94
        c = sub(c, SH.halftone(t * 0.5, hot=(0, 0, 0), intensity=0.20, step=13))
        c = sub(c, SH.ink_splat(t * 0.3, hot=(30, 10, 8), intensity=0.30, seed=67, n=4) * 0.5)
        # giant ghost numeral: the beat is never an empty red field
        kk = smoothstep(CUT_3 + 0.02, CUT_3 + 0.30, p)
        ink_text(c, "7", "fonts/Anton-Regular.ttf", 1060, W / 2, H * 0.605,
                 alpha=kk * 0.17,
                 color=(255, 250, 244), scale=1.0)
        # thin white rules framing the beat
        red_bar(c, H * 0.245, 5, alpha=kk * 0.5, x0=100, x1=W - 100, color=(255, 248, 240))
        red_bar(c, H * 0.775, 5, alpha=kk * 0.35, x0=100, x1=W - 100, color=(255, 248, 240))
        k = smoothstep((t_seventh or 6.76) - 0.14, (t_seventh or 6.76) - 0.01, p)
        sc = 1.0 + 0.10 * (1 - ease_out_cubic(np.clip((p - (t_seventh or 6.76) + 0.14) / 0.26, 0, 1)))
        ink_text(c, "والسابعة", "fonts/Tajawal-ExtraBold.ttf", 168, W / 2, H * 0.325,
                 alpha=k, color=(255, 252, 246), scale=sc)
        k2 = smoothstep(7.60, 7.86, p)
        ink_text(c, "الحقيقة التي تكسر الباقي", "fonts/Tajawal-Bold.ttf", 50,
                 W / 2, H * 0.735, alpha=k2 * 0.85, color=(255, 246, 238))
        return c

    # ============================================ E · it will break you
    c = np.zeros((H, W, 3), np.float32)
    c[:, :] = RED[None, None, :] * 0.95
    c = sub(c, SH.halftone(t * 0.4, hot=(0, 0, 0), intensity=0.14, step=13))
    c = sub(c, SH.ink_splat(t * 0.3, hot=(24, 8, 6), intensity=0.22, seed=99, n=3) * 0.5)
    k = smoothstep((t_break or 8.37) - 0.16, (t_break or 8.37) - 0.02, p)
    ink_text(c, "ستكسر", "fonts/Tajawal-ExtraBold.ttf", 180, W / 2, H * 0.435,
             alpha=k, color=(255, 252, 246))
    ink_text(c, "كل ما تعرفه", "fonts/Tajawal-ExtraBold.ttf", 116, W / 2, H * 0.545,
             alpha=k, color=(255, 252, 246))
    ink_text(c, "عن نفسك", "fonts/Tajawal-Bold.ttf", 88, W / 2, H * 0.628,
             alpha=k * 0.95, color=(255, 252, 246))
    if p > sec["end"] - sec["start"] - 0.30:
        k3 = smoothstep(sec["end"] - sec["start"] - 0.30,
                        sec["end"] - sec["start"] - 0.02, p)
        c *= (1 - 0.9 * k3)
    return c


# ------------------------------------------------------------------ rehook
def scene_rehook(t, sec):
    p = t - sec["start"]
    span = max(0.1, sec["end"] - sec["start"])
    c = np.zeros((H, W, 3), np.float32)
    c[:, :] = PAPER[None, None, :] * 0.10
    c += SH.scan_bar(t, hot=(30, 30, 36), intensity=1.0, speed=0.8, thickness=0.3)
    k = smoothstep(0.03, 0.20, p) * (1 - smoothstep(span - 0.28, span - 0.04, p))
    ink_text(c, REHOOK.get("who", "الأرقام"), "fonts/Tajawal-Bold.ttf", 44,
             W / 2, H * 0.375, alpha=k * 0.6, color=(214, 208, 198))
    kb = smoothstep(0.22, 0.40, p) * k
    big = REHOOK.get("big", "اثنان بالمئة")
    L = grad_layer(big, "fonts/Tajawal-ExtraBold.ttf",
                   fit_px(big, 980, 160, "fonts/Tajawal-ExtraBold.ttf"),
                   c1=(255, 252, 245), c2=(240, 120, 90))
    L.stamp_centered(c, W / 2, H * 0.495, alpha=kb,
                     scale=1.14 - 0.14 * ease_out_cubic(np.clip((p - 0.22) / 0.4, 0, 1)))
    ks = smoothstep(0.70, 0.92, p) * k
    ink_text(c, REHOOK.get("small", "فقط سيصفقون لك"), "fonts/Tajawal-Bold.ttf", 58,
             W / 2, H * 0.585, alpha=ks * 0.9, color=(226, 220, 210))
    red_bar(c, H * 0.545, 8, alpha=ks * 0.9)
    return c


# ------------------------------------------------------------------ tips
def scene_tip(t, sec):
    p = t - sec["start"]
    idx = sec["idx"]
    hot, deep = TIP_COL[idx]
    look = TIP_LOOK[idx]
    final = idx == N_TIPS
    times = tip_times(sec)

    c = plate(look, t, hot)
    c = vignette_plate(c, 0.30 if not final else 0.46)
    # a hairline grid makes the plate look *designed* instead of flat
    for gy in (H * 0.398, H * 0.648):
        cv2.line(c, (70, int(gy)), (W - 70, int(gy)),
                 (222, 218, 210), 1, cv2.LINE_AA)

    # top rule + folio, like a printed page
    red_bar(c, H * 0.145, 5, alpha=0.9, x0=86, x1=W - 86)
    ink_text(c, f"{idx:02d} / 07", "fonts/BebasNeue-Regular.ttf", 40,
             W - 90, H * 0.115, alpha=0.55, centre=False)
    ink_text(c, "حقائق مرة", "fonts/Tajawal-Bold.ttf", 36, 90, H * 0.115,
             alpha=0.55, centre=False)

    intro = smoothstep(0.0, 0.18, p)
    pop = 1.0 + 0.26 * (1 - ease_out_cubic(np.clip(p / 0.26, 0, 1)))
    # on paper plates the number is ink; on red/ink plates it's knocked out
    dark_plate = look in ("ink_splat",)
    num_col = (np.array([255, 252, 246], np.float32) if dark_plate
               else np.array([26, 26, 30], np.float32))
    num = TextLayer(f"{idx:02d}", "fonts/Anton-Regular.ttf", 240,
                    gradient=(num_col, num_col * 0.72))
    num.stamp_centered(c, W / 2, H * 0.245, alpha=intro, scale=pop)

    who = TIP_WHO[idx]
    wa = smoothstep(0.05, 0.26, p)
    ink_text(c, who, "fonts/Tajawal-ExtraBold.ttf",
             fit_px(who, 930, 84, "fonts/Tajawal-ExtraBold.ttf"),
             W / 2, H * 0.335, alpha=wa,
             color=(255, 252, 246) if dark_plate else tuple(INK.tolist()))
    en = layer(TIP_EN[idx], "fonts/BebasNeue-Regular.ttf", 32)
    en.stamp_centered(c, W / 2, H * 0.365, alpha=wa * 0.42,
                      color=(228, 224, 214) if dark_plate else (86, 84, 80))

    q1, q2 = TIP_Q1[idx], TIP_Q2[idx]
    f1 = "fonts/Tajawal-ExtraBold.ttf"
    qa = smoothstep(0.36, 0.70, p)
    # quote 1 is always knocked out on a red bar: maximum contrast, no glow
    if qa > 0.02:
        bar_y = H * 0.472
        _qpx = fit_px(q1, 900, 98, f1, 54)
        lab = layer(q1, f1, _qpx)
        bw_half = min(W * 0.5 - 40, lab.w / 2 + 46)
        y0, y1 = int(bar_y - _qpx * 0.82), int(bar_y + _qpx * 0.82)
        x0, x1 = int(W / 2 - bw_half), int(W / 2 + bw_half)
        c[y0:y1, x0:x1] = c[y0:y1, x0:x1] * (1 - qa) + RED[None, None, :] * qa
        lab.stamp_centered(c, W / 2, bar_y, alpha=qa, color=(255, 253, 248))
    q2a = smoothstep(0.82, 1.20, p)
    ink_text(c, q2, "fonts/Tajawal-Bold.ttf",
             fit_px(q2, 980, 68, "fonts/Tajawal-Bold.ttf", 44),
             W / 2, H * 0.585, alpha=q2a * 0.95,
             color=(252, 249, 242) if dark_plate else (34, 32, 30))

    # --- editorial filler: three red ticks + a rotated folio, so no dead band
    tick_a = smoothstep(0.9, 1.25, p)
    if tick_a > 0.02:
        for i in range(3):
            ty = H * (0.700 + i * 0.028)
            tw = 120 - i * 32
            red_bar(c, ty, 11, alpha=tick_a * (0.95 - i * 0.22),
                    x0=110, x1=110 + tw)
    rot = layer("الجزء الخامس", "fonts/Tajawal-Bold.ttf", 30)
    rot.mask = np.ascontiguousarray(np.rot90(rot.mask, k=-1))
    rot.h, rot.w = rot.mask.shape
    rot.stamp_centered(c, W - 52, H * 0.735, alpha=tick_a * 0.40,
                       color=(250, 247, 240) if dark_plate else (52, 50, 48))

    # captions, inverted palette (black plates get paper type, paper gets ink)
    dark_plate = look in ("ink_splat",)
    caption_block(c, t, times, y=0.815, px=72, light=dark_plate)

    # progress: a printed rule that fills
    frac = np.clip(p / max(0.1, sec["end"] - sec["start"]), 0, 1)
    prog = (idx - 1 + frac) / N_TIPS
    bx0, bx1, by = 120, W - 120, int(H * 0.885)
    base_col = (250, 247, 240) if dark_plate else (60, 58, 56)
    cv2.line(c, (bx0, by), (bx1, by), tuple(float(v) for v in base_col), 3, cv2.LINE_AA)
    cv2.line(c, (bx0, by), (bx0 + int((bx1 - bx0) * prog), by),
             tuple(float(v) for v in RED), 5, cv2.LINE_AA)

    if final:
        beat = 0.0
        for hb in (0.28, 1.10, 1.92, 2.70, 3.45):
            if hb <= p < hb + 0.42:
                beat = math.exp(-(p - hb) * 11)
        c *= (1 - 0.16 * beat)
    # 2-frame paper flick at the section end so cuts feel hand-made
    tail = sec["end"] - sec["start"] - p
    if tail < 0.07:
        c *= 1 - 0.35 * (0.07 - tail) / 0.07
    return c


# ------------------------------------------------------------------ captions
def caption_groups(times):
    groups, cur = [], []
    for i, (w, a, b) in enumerate(times):
        if cur and a - times[cur[-1]][2] > 0.28:
            groups.append(cur)
            cur = []
        cur.append(i)
        if len(cur) >= 4:
            groups.append(cur)
            cur = []
    if cur:
        groups.append(cur)
    return groups


def caption_block(canvas, t, times, y=0.795, px=72, light=False):
    if not times or t < times[0][1] - 0.10:
        return canvas
    groups = caption_groups(times)
    cur = groups[0]
    for g in groups:
        if times[g[0]][1] <= t + 0.05:
            cur = g
    if t > times[cur[-1]][2] + 0.45:
        return canvas
    f = "fonts/Tajawal-ExtraBold.ttf"
    spoken = [(i, times[i][0].strip("….,؟:،")) for i in cur if times[i][1] <= t + 0.06]
    spoken = [(i, w) for i, w in spoken if w]
    if not spoken:
        return canvas
    size = px
    while size > 38:
        laid = [(i, layer(w, f, size)) for i, w in spoken]
        if sum(L.w for _, L in laid) + 22 * (len(laid) - 1) <= 960:
            break
        size -= 4
    laid = [(i, layer(w, f, size)) for i, w in spoken]
    gap = 22
    rw = sum(L.w for _, L in laid) + gap * (len(laid) - 1)
    row_h = max(L.h for _, L in laid)
    appear = smoothstep(times[cur[0]][1] - 0.06, times[cur[0]][1] + 0.07, t)
    yy = H * y - row_h / 2
    x = W / 2 + rw / 2
    on_col = (255, 252, 246) if light else (16, 16, 20)
    off_col = (200, 196, 188) if light else (86, 84, 80)
    for i, L in laid:
        x -= L.w
        on = times[i][1] - 0.05 <= t <= times[i][2] + 0.14
        a = appear * (1.0 if on else 0.72)
        # solid box behind each word: readable over any paper texture
        if on:
            L.stamp(canvas, x, yy, alpha=a * 0.9, color=on_col)
        else:
            L.stamp(canvas, x, yy, alpha=a, color=off_col)
        x -= gap
    return canvas


# ------------------------------------------------------------------ outro
def scene_outro(t, sec):
    p = t - sec["start"]
    c = SH.paper(t, seed=17, vig=0.55)
    c = vignette_plate(c, 0.42)
    # clean paper + a red edge bar and an ink corner — the type stays untouched
    c[:, 0:14] = RED[None, None, :] * 0.9
    c[int(H * 0.945):, :] = c[int(H * 0.945):, :] * 0.0 + INK[None, None, :]
    red_bar(c, H * 0.30, 6, alpha=0.9, x0=110, x1=W - 110)
    red_bar(c, H * 0.885, 4, alpha=0.55, x0=110, x1=W - 110)

    a1 = smoothstep(0.10, 0.50, p)
    ink_text(c, OUTRO["title"], "fonts/Tajawal-ExtraBold.ttf",
             fit_px(OUTRO["title"], 940, 96, "fonts/Tajawal-ExtraBold.ttf"),
             W / 2, H * 0.375, alpha=a1)
    a2 = smoothstep(0.95, 1.40, p)
    ink_text(c, OUTRO["l2"], "fonts/Tajawal-Bold.ttf", 62, W / 2, H * 0.468,
             alpha=a2 * 0.95)
    a3 = smoothstep(1.60, 2.05, p)
    ink_text(c, OUTRO["l3"], "fonts/Tajawal-Bold.ttf", 50, W / 2, H * 0.535,
             alpha=a3 * 0.85)

    a4 = smoothstep(2.6, 3.2, p)
    if a4 > 0.01:
        bx, by, bw, bh = W / 2, H * 0.655, 760, 120
        y0, y1 = int(by - bh / 2), int(by + bh / 2)
        x0, x1 = int(bx - bw / 2), int(bx + bw / 2)
        c[y0:y1, x0:x1] = c[y0:y1, x0:x1] * (1 - a4) + INK[None, None, :] * a4
        lab = layer(OUTRO["cta"], "fonts/Tajawal-ExtraBold.ttf", 54)
        lab.stamp_centered(c, bx, by, alpha=a4, color=(255, 252, 246))
    a5 = smoothstep(3.6, 4.1, p)
    if a5 > 0.01:
        ink_text(c, OUTRO["comment"], "fonts/Tajawal-Bold.ttf", 40,
                 W / 2, H * 0.755, alpha=a5 * 0.65)
    a6 = smoothstep(4.4, 4.9, p)
    if a6 > 0.01:
        ink_text(c, OUTRO["follow"], "fonts/Tajawal-Bold.ttf", 40,
                 W / 2, H * 0.818, alpha=a6 * 0.55, color=tuple(RED.tolist()))

    # end-card never goes static: slow push-in + a filling rule at the very bottom
    dur = max(0.1, sec["end"] - sec["start"])
    z = 1.0 + 0.045 * (p / dur)
    M = cv2.getRotationMatrix2D((W / 2, H / 2), 0.0, z)
    M[0, 2] += (W - W * z) / 2
    M[1, 2] += (H - H * z) / 2
    c = cv2.warpAffine(c, M, (W, H), flags=cv2.INTER_LINEAR,
                       borderMode=cv2.BORDER_REPLICATE)
    prog = min(1.0, p / dur)
    c[int(H * 0.962):int(H * 0.972), 0:max(2, int(W * prog))] = RED[None, None, :]
    return c


# ------------------------------------------------------------------ grade
def apply_grade(c):
    """Paper grade: no bloom (bloom is for neon), just contrast + warm paper."""
    c = np.clip(c, 0, 255) / 255.0
    c = c * 1.02
    c = c / (1 + c * 0.10)
    # lift shadows a touch toward warm paper so blacks never go pure
    c = c * 0.965 + np.array([0.012, 0.010, 0.008], np.float32)[None, None, :]
    c = np.clip(c, 0, 1) ** (1 / 1.06)
    g = c.mean(axis=2, keepdims=True)
    c = g + (c - g) * 1.02
    return np.clip(c, 0, 1)


def render_frame(fi, total_frames, resolve_sec):
    t = fi / FPS
    sec = resolve_sec(fi)
    kind = sec["kind"]
    if kind == "hook":
        c = scene_hook(t, sec)
    elif kind == "rehook":
        c = scene_rehook(t, sec)
    elif kind == "tip":
        c = scene_tip(t, sec)
    else:
        c = scene_outro(t, sec)
    p = t - sec["start"]

    # paper cuts are hard, not glitchy — a single-frame white flash instead
    if p < 0.055 and kind != "hook":
        c += (0.055 - p) / 0.055 * 60
    if p < 0.10:
        c = chroma_shift(c, 5 * (0.10 - p) / 0.10)

    span = max(1.0, sec["end"] - sec["start"])
    z = 1.0 + (0.030 if kind == "hook" else 0.038) * min(1.0, p / span)
    if z > 1.001:
        c = cv2.resize(c, None, fx=z, fy=z, interpolation=cv2.INTER_LINEAR)
        y0 = (c.shape[0] - H) // 2
        x0 = (c.shape[1] - W) // 2
        c = c[y0:y0 + H, x0:x0 + W]

    c = apply_grade(c)
    rng = np.random.default_rng(11 + fi % 7)
    add_grain(c, rng, 0.014)
    if t < 0.10:
        c *= 0.55 + 0.45 * smoothstep(0.0, 0.10, t)
    if t > TOTAL - 0.75:
        c *= 1 - smoothstep(TOTAL - 0.75, TOTAL - 0.03, t)
    return (np.clip(c, 0, 1) * 255).astype(np.uint8)


# ------------------------------------------------------------------ encode
_N = 0
_BOUNDS = []


def _resolve(fi):
    t = fi / FPS
    for i, (a, b, s) in enumerate(_BOUNDS):
        if t < b or i == len(_BOUNDS) - 1:
            return s
    return _BOUNDS[-1][2]


def _work(fi):
    return render_frame(fi, _N, _resolve).tobytes()


def main(out_path=None, workers=2, start=None, end=None, audio=None, crf=18):
    from multiprocessing import Pool
    global _N, _BOUNDS
    n = int(TOTAL * FPS)
    _N = n
    _BOUNDS = [(s["start"], s["end"], s) for s in SECTIONS]
    out_path = out_path or EP["out"]
    audio = audio or (EP["build"] + "/mix.wav")
    lo = int(start * FPS) if start is not None else 0
    hi = int(end * FPS) if end is not None else n
    cmd = ["ffmpeg", "-y", "-loglevel", "error",
           "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
           "-thread_queue_size", "512", "-i", "-"]
    has_audio = bool(audio) and os.path.exists(audio)
    if has_audio:
        cmd += ["-i", audio]
    cmd += ["-c:v", "libx264", "-preset", "slow", "-crf", str(crf),
            "-pix_fmt", "yuv420p", "-profile:v", "high", "-level", "4.2",
            "-x264-params", "keyint=48:min-keyint=24:ref=4:aq-mode=3",
            "-color_primaries", "bt709", "-color_trc", "bt709", "-colorspace", "bt709"]
    if has_audio:
        cmd += ["-c:a", "aac", "-b:a", "192k", "-ar", "48000"]
    cmd += ["-t", f"{(hi - lo) / FPS:.3f}", "-movflags", "+faststart", out_path]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    with Pool(workers) as pool:
        for i, buf in enumerate(pool.imap(_work, range(lo, hi), chunksize=6)):
            proc.stdin.write(buf)
            if i % 120 == 0:
                print(f"  {lo + i}/{hi}  ({100 * i / max(1, hi - lo):.0f}%)", flush=True)
    proc.stdin.close()
    proc.wait()
    print("done", out_path)


if __name__ == "__main__":
    ff = "/usr/local/lib/python3.11/dist-packages/imageio_ffmpeg/binaries/ffmpeg-linux-x86_64-v9.0.2"
    if os.path.exists(ff):
        os.environ["PATH"] = os.path.dirname(ff) + ":" + os.environ["PATH"]
    a, b = (sys.argv[1], sys.argv[2]) if len(sys.argv) > 2 else (None, None)
    out = sys.argv[3] if len(sys.argv) > 3 else None
    main(out_path=out, start=float(a) if a else None, end=float(b) if b else None)
