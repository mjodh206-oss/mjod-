"""Part-4 renderer — retention edit.

The previous part held ~50 % of viewers. That is a structure problem, not a
beauty problem, so this renderer changes the *shape* of the video as well as
the look:

  1. The hook is six images, not five, and the first three seconds contain
     **three** complete cuts (0.00 / 0.55 / 1.70) — a scroll has to be stopped
     before 1 s, not "around" 3 s.
  2. A 7-dot "theft meter" sits in the dead zone above the captions for the
     whole runtime, so a viewer who joins late instantly knows where they are
     and how much is left. It also gives a mini-reward on every law.
  3. A 2.6 s `rehook` beat with no voice at all sits at the exact point where
     short-form retention normally collapses (~55 % through). Silence is the
     loudest pattern interrupt available.
  4. Every law ends with a 3-frame flash-cut, so cuts feel deliberate.

The look book is the part-3 shader set plus `clock` and `mirror` plates.
"""
import math
import os
import subprocess
import sys

import cv2
import numpy as np

cv2.setNumThreads(1)

from .core import (W, H, FPS, GOLD, GOLD_LIGHT, IVORY,
                   add_grain, vignette_map, chroma_shift, radial_flash,
                   smoothstep, ease_out_cubic)
from . import gfx
from . import shaders as SH
from .core import TextLayer
from .text import Line, layer, grad_layer, rule
from .timeline import SECTIONS, TOTAL, align, EP

VIG = vignette_map(0.70, 1.9)[..., None].astype(np.float32)

TIP_COL = {t["n"]: tuple(t["col"]) for t in EP["tips"]}
TIP_WORDS = {t["n"]: t["words"] for t in EP["tips"]}
TIP_WHO = {t["n"]: t["who"] for t in EP["tips"]}
TIP_EN = {t["n"]: t["who_en"] for t in EP["tips"]}
TIP_Q1 = {t["n"]: t["quote"] for t in EP["tips"]}
TIP_Q2 = {t["n"]: t["quote2"] for t in EP["tips"]}
TIP_LOOK = {t["n"]: t.get("look", "nebula") for t in EP["tips"]}
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


def fit_scale(text, font, base_px, max_scale, max_w=940, min_px=48):
    """Pick a px size such that base_px*max_scale still fits inside max_w."""
    px = base_px
    while px > min_px and layer(text, font, int(px * max_scale)).w > max_w:
        px -= 6
    return px


def fit_px(text, max_w, base_px, font, min_px=54):
    px = base_px
    while px > min_px:
        if layer(text, font, px).w <= max_w:
            return px
        px -= 4
    return min_px


def _drift(c, q, zoom=0.07, pan=20.0, spin=0.0):
    """Sub-degree camera move — no shot is ever a still frame."""
    q = max(0.0, q)
    z = 1.0 + zoom * min(1.0, q / 2.2)
    M = cv2.getRotationMatrix2D((W / 2, H / 2), spin * math.sin(q * 1.2) * 0.4, z)
    M[0, 2] += pan * math.sin(q * 0.95)
    M[1, 2] += 10.0 * math.sin(q * 0.72 + 0.5)
    return cv2.warpAffine(c.astype(np.float32), M, (W, H), flags=cv2.INTER_LINEAR,
                          borderMode=cv2.BORDER_REFLECT)


def plate(look, t, hot, intensity=1.0):
    if look == "tunnel":
        return SH.tunnel(t, hot=hot, intensity=intensity * 0.9)
    if look == "tunnel_blue":
        return SH.tunnel(t * 1.2, hot=hot, intensity=intensity * 1.05, rings=16)
    if look == "tunnel_hot":
        return SH.tunnel(t * 1.3, hot=hot, intensity=intensity * 1.1, rings=24)
    if look == "energy":
        return SH.energy_grid(t, hot=hot, intensity=intensity * 1.2)
    if look == "orbs":
        return SH.orbs(t, hot=hot, intensity=intensity * 0.9)
    if look == "vortex":
        return SH.vortex(t, hot=hot, intensity=intensity)
    if look == "shards":
        return SH.shards(t, hot=hot, intensity=intensity)
    if look == "clock":
        return SH.clock(t, hot=hot, intensity=intensity)
    if look == "mirror":
        return SH.mirror(t, hot=hot, intensity=intensity)
    return SH.nebula(t, hot=hot, intensity=intensity * 1.1)


def vignette_plate(img, dark=0.72):
    """Crush the edges of a plate so type always wins."""
    return img * (VIG ** 1.15)


def scrims(canvas, bands=((0.28, 0.075, 0.52), (0.515, 0.085, 0.34),
                          (0.775, 0.115, 0.48))):
    ys = np.arange(H, dtype=np.float32) / H
    for y, s, a in bands:
        canvas *= (1 - a * np.exp(-(((ys - y) / s) ** 2)))[:, None, None]
    return canvas


# ------------------------------------------------------------------ theft meter
def theft_meter(c, t, hot, current, alpha=1.0):
    """Seven dots that fill with a law and drain after it — a progress reward."""
    if alpha <= 0.01:
        return c
    n = N_TIPS
    gap, r = 54, 10
    total_w = gap * (n - 1)
    x0 = W / 2 - total_w / 2
    y = int(H * 0.252)
    for i in range(n):
        k = i + 1
        x = int(x0 + i * gap)
        if k < current:
            fill, rad = 1.0, r
        elif k == current:
            fill, rad = 1.0, r + 2
        else:
            fill, rad = 0.0, r - 1
        if fill:
            cv2.circle(c, (x, y), rad + 6, tuple((np.array(hot) * 0.28 * alpha).tolist()),
                       -1, cv2.LINE_AA)
            cv2.circle(c, (x, y), rad, tuple((np.array(hot) * 1.25 * alpha).tolist()),
                       -1, cv2.LINE_AA)
        else:
            cv2.circle(c, (x, y), rad, tuple((np.array(IVORY) * 0.30 * alpha).tolist()),
                       3, cv2.LINE_AA)
    # the connecting rail
    cv2.line(c, (int(x0 - 24), y), (int(x0 + total_w + 24), y),
             tuple((np.array(IVORY) * 0.14 * alpha).tolist()), 2, cv2.LINE_AA)
    return c


# ------------------------------------------------------------------ hook (6 cuts)
def scene_hook(t, sec):
    """Six cuts inside 10.7 s; three complete images before 2 s.

    A 0.00  black → white smash → «سبعة»       (0.30)
    B 0.55  clock, hands spinning fast → «تسرق عمرك»   (0.85)
    C 1.70  vortex → «الآن»                    (2.20)
    D 3.10  slow cold nebula → «ليست أعداءك» / «ولا حظك»
    E 5.60  dark grid → «عادات في يومك العادي»
    F 7.90  mirror plate → «والسابع… أنت في المرآة»
    """
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

    t_seven, _ = at("سبعة")            # 0.30
    t_steal, _ = at("تسرق")            # 0.85
    t_now, _ = at("الآن")              # 2.20
    t_not, _ = at("ليست")              # 3.10
    t_habits, _ = at("عادات")          # 5.60
    t_seventh, _ = at("والسابع")       # 7.90
    t_mirror, _ = at("المرآة")         # 9.20

    CUT_B = 0.55
    CUT_C = (t_now or 2.20) - 0.14
    CUT_D = (t_not or 3.10) - 0.12
    CUT_E = (t_habits or 5.60) - 0.12
    CUT_F = (t_seventh or 7.90) - 0.12

    # ============================================ A · 0.00 the white smash
    if p < CUT_B:
        c = np.full((H, W, 3), 3.0, np.float32)
        c += max(0.0, 1 - p / 0.13) * 245            # 4-frame blowout
        c += SH.speed_lines(t * 1.4, hot=(255, 240, 220), intensity=1.15)
        c = vignette_plate(c, 0.55)
        # the numeral is the hook: a 7 big enough to read at thumbnail size
        pop = 1.0 + 0.34 * (1 - ease_out_cubic(np.clip(p / 0.30, 0, 1)))
        k7 = smoothstep(0.00, 0.045, p)
        num = TextLayer("7", "fonts/Anton-Regular.ttf", 700,
                        gradient=(np.array([255, 255, 248], np.float32),
                                  np.array([255, 118, 24], np.float32)))
        num.stamp_centered(c, W / 2, H * 0.395, alpha=k7, scale=pop,
                           glow=0.72, glow_color=(255, 120, 30), glow_sigma=56)
        k = smoothstep(0.05, 0.16, p)
        _px = fit_scale("سبعة تسرقك", "fonts/Tajawal-ExtraBold.ttf", 132, 1.06)
        L = grad_layer("سبعة تسرقك", "fonts/Tajawal-ExtraBold.ttf", _px, stroke=7,
                       c1=(255, 255, 250), c2=(255, 150, 40))
        L.stamp_centered(c, W / 2, H * 0.588, alpha=k, scale=1.0,
                         glow=0.5, glow_color=(255, 130, 40), glow_sigma=38)
        SH.anamorphic_flare(c, 0.5, 0.395, hot=(255, 235, 200),
                            alpha=max(0.0, 0.7 - p) * 0.9)
        return c

    # ============================================ B · 0.55 the clock
    if p < CUT_C:
        q = p - CUT_B
        # hands race backwards: time being taken, not passing
        c = SH.clock(t, hot=(255, 232, 190), intensity=1.10,
                     hour_ang=-q * 5.0, minute_ang=-q * 22.0, cy=0.33, R=290)
        c += SH.radial_pulse(t, hot=(255, 200, 140), intensity=0.5, cy=0.33)
        c = vignette_plate(c, 0.80)
        c = scrims(c, bands=((0.505, 0.085, 0.62), (0.565, 0.06, 0.45)))
        k = smoothstep(0.55 + 0.02, 0.55 + 0.12, p)
        Line("تسرق عمرك", "fonts/Tajawal-ExtraBold.ttf",
             fit_px("تسرق عمرك", 940, 118, "fonts/Tajawal-ExtraBold.ttf")).draw(
            c, W / 2, H * 0.525, alpha=k, reveal=(p - 0.55) * 5.0,
            glow=0.6, glow_color=(255, 80, 20), glow_sigma=34)
        return _drift(c, q, 0.07)

    # ============================================ C · 2.06 "now"
    if p < CUT_D:
        c = SH.vortex(t * 1.8, hot=(255, 110, 30), intensity=1.3)
        c = vignette_plate(c, 0.86)
        SH.anamorphic_flare(c, 0.5, 0.475, hot=(255, 150, 80), alpha=0.55)
        c = scrims(c, bands=((0.475, 0.115, 0.66),))
        k = smoothstep((t_now or 2.20) - 0.14, (t_now or 2.20) + 0.02, p)
        L = grad_layer("الآن", "fonts/Tajawal-ExtraBold.ttf",
                       fit_scale("الآن", "fonts/Tajawal-ExtraBold.ttf", 230, 1.30), stroke=6,
                       c1=(255, 250, 232), c2=(255, 90, 20))
        L.stamp_centered(c, W / 2, H * 0.48, alpha=k,
                         scale=1.30 - 0.30 * ease_out_cubic(
                             np.clip((p - (t_now or 2.2)) / 0.4, 0, 1)),
                         glow=0.75, glow_color=(255, 60, 10), glow_sigma=44)
        return _drift(c, p - CUT_C, 0.10, spin=0.7)

    # ============================================ D · not your enemies
    if p < CUT_E:
        c = SH.nebula(t * 0.8, hot=(150, 170, 255), intensity=0.55, scale=2.1)
        c = vignette_plate(c, 0.92)
        c += SH.stars(t, intensity=0.28, seed=61)
        c = scrims(c, bands=((0.455, 0.10, 0.58), (0.545, 0.085, 0.50)))
        k = smoothstep((t_not or 3.1) - 0.12, (t_not or 3.1) + 0.02, p)
        Line("ليست أعداءك", "fonts/Tajawal-ExtraBold.ttf", 110).draw(
            c, W / 2, H * 0.462, alpha=k, reveal=(p - (t_not or 3.1) + 0.12) * 3.2,
            glow=0.45, glow_color=(110, 140, 255), glow_sigma=30)
        Line("ولا حظك", "fonts/Tajawal-Bold.ttf", 84).draw(
            c, W / 2, H * 0.548, alpha=k * 0.96, color=tuple(IVORY),
            reveal=(p - (t_not or 3.1) + 0.42) * 2.6, rise=16)
        return _drift(c, p - CUT_D, 0.06, pan=14)

    # ============================================ E · ordinary day
    if p < CUT_F:
        c = SH.energy_grid(t * 1.5, hot=(255, 210, 150), intensity=1.25, horizon=0.52)
        c = vignette_plate(c, 0.84)
        c = scrims(c, bands=((0.465, 0.095, 0.55), (0.555, 0.08, 0.45)))
        k = smoothstep((t_habits or 5.6) - 0.12, (t_habits or 5.6) + 0.02, p)
        Line("عادات في يومك", "fonts/Tajawal-ExtraBold.ttf", 104).draw(
            c, W / 2, H * 0.468, alpha=k, reveal=(p - (t_habits or 5.6) + 0.12) * 3.0,
            glow=0.5, glow_color=(255, 160, 60), glow_sigma=28)
        Line("العادي", "fonts/Tajawal-Bold.ttf", 92).draw(
            c, W / 2, H * 0.552, alpha=k * 0.95, color=tuple(IVORY),
            reveal=(p - (t_habits or 5.6) + 0.5) * 2.6, rise=14)
        return _drift(c, p - CUT_E, 0.055, pan=12)

    # ============================================ F · the mirror
    c = SH.mirror(t, hot=(255, 200, 170), intensity=1.0)
    c += SH.nebula(t * 0.6, hot=(255, 70, 30), intensity=0.45, scale=2.4)
    c = vignette_plate(c, 0.93)
    c = scrims(c, bands=((0.425, 0.085, 0.68), (0.545, 0.095, 0.72)))
    k = smoothstep((t_seventh or 7.9) - 0.12, (t_seventh or 7.9) + 0.02, p)
    layer("والسابع", "fonts/Tajawal-Bold.ttf", 64).stamp_centered(
        c, W / 2, H * 0.425, alpha=k * 0.9, color=tuple(IVORY))
    k2 = smoothstep((t_mirror or 9.2) - 0.18, (t_mirror or 9.2) + 0.02, p)
    if k2 > 0.02:
        SH.anamorphic_flare(c, 0.5, 0.545, hot=(255, 110, 60), alpha=k2 * 0.45)
    _px = fit_scale("أنت في المرآة", "fonts/Tajawal-ExtraBold.ttf", 124, 1.22)
    L = grad_layer("أنت في المرآة", "fonts/Tajawal-ExtraBold.ttf", _px, stroke=5,
                   c1=(255, 252, 244), c2=(255, 70, 26))
    L.stamp_centered(c, W / 2, H * 0.545, alpha=k2,
                     scale=1.22 - 0.22 * ease_out_cubic(
                         np.clip((p - (t_mirror or 9.2)) / 0.42, 0, 1)),
                     glow=0.8, glow_color=(255, 30, 8), glow_sigma=42)
    if p > sec["end"] - sec["start"] - 0.35:
        k3 = smoothstep(sec["end"] - sec["start"] - 0.35,
                        sec["end"] - sec["start"] - 0.02, p)
        c *= (1 - 0.95 * k3)
    return _drift(c, p - CUT_F, 0.09)


# ------------------------------------------------------------------ re-hook
def scene_rehook(t, sec):
    """2.6 s with no voice: the middle-of-video pattern interrupt.

    Structure: hard cut to black → clock dial snaps in with the hands stopped →
    the big number lands → the bed slams back in on the next law.
    """
    p = t - sec["start"]
    span = max(0.1, sec["end"] - sec["start"])
    c = np.full((H, W, 3), 4.0, np.float32)
    k = smoothstep(0.02, 0.20, p) * (1 - smoothstep(span - 0.30, span - 0.04, p))
    c += SH.radial_pulse(t * 0.9, hot=(255, 190, 130), intensity=0.75 * k, cy=0.36)
    c += SH.clock(t * 0.25, hot=(255, 210, 160), intensity=0.34 * k,
                  hour_ang=-1.2, minute_ang=2.0, cy=0.255, R=150)
    c = vignette_plate(c, 0.95)
    c = scrims(c, bands=((0.505, 0.10, 0.66), (0.585, 0.085, 0.60)))
    kh = smoothstep(0.12, 0.30, p)
    layer(REHOOK.get("who", "الوقت"), "fonts/Tajawal-Bold.ttf", 54).stamp_centered(
        c, W / 2, H * 0.378, alpha=kh * 0.8, color=tuple(IVORY))
    kb = smoothstep(0.30, 0.46, p) * k
    _b = REHOOK.get("big", "نصف عمرك")
    big = grad_layer(_b, "fonts/Tajawal-ExtraBold.ttf",
                     fit_scale(_b, "fonts/Tajawal-ExtraBold.ttf", 138, 1.22),
                     stroke=5, c1=(255, 250, 240), c2=(255, 90, 30))
    big.stamp_centered(c, W / 2, H * 0.505, alpha=kb,
                       scale=1.22 - 0.22 * ease_out_cubic(np.clip((p - 0.30) / 0.4, 0, 1)),
                       glow=0.7, glow_color=(255, 60, 12), glow_sigma=38)
    ks = smoothstep(0.85, 1.05, p) * k
    layer(REHOOK.get("small", "يمرّ وأنت تنتظر"), "fonts/Tajawal-Bold.ttf", 62).stamp_centered(
        c, W / 2, H * 0.585, alpha=ks * 0.9, color=tuple(IVORY))
    if p < 0.5:
        c += (1 - p / 0.5) * 40
    return c


# ------------------------------------------------------------------ tips
def scene_tip(t, sec):
    p = t - sec["start"]
    idx = sec["idx"]
    hot, deep = TIP_COL[idx]
    look = TIP_LOOK[idx]
    final = idx == N_TIPS
    times = tip_times(sec)

    c = plate(look, t, hot, intensity=1.0 if not final else 1.15)
    c = vignette_plate(c, 0.74 if not final else 0.86)
    c += SH.stars(t, intensity=0.26, seed=idx * 11)
    c += SH.god_rays(t, hot=hot, intensity=0.20, cy=0.30, seed=idx)
    c = scrims(c, bands=((0.228, 0.055, 0.42), (0.28, 0.075, 0.55),
                         (0.475, 0.085, 0.48), (0.565, 0.085, 0.44),
                         (0.775, 0.115, 0.52)))

    intro = smoothstep(0.0, 0.20, p)
    pop = 1.0 + 0.5 * (1 - ease_out_cubic(np.clip(p / 0.28, 0, 1)))
    num = TextLayer(f"{idx:02d}", "fonts/Anton-Regular.ttf", 250,
                    gradient=(np.array([255, 246, 214], np.float32),
                              np.array(deep, np.float32)))
    num.stamp_centered(c, W / 2, H * 0.175, alpha=intro, scale=pop,
                       glow=0.55, glow_color=deep, glow_sigma=40)
    ring_r = int(152 * pop)
    cv2.ellipse(c, (int(W / 2), int(H * 0.175)), (ring_r, ring_r), 0, -90,
                -90 + 360 * ease_out_cubic(np.clip(p / 0.45, 0, 1)),
                tuple((np.array(hot) * 0.9).tolist()), 3, cv2.LINE_AA)
    who = layer(TIP_WHO[idx], "fonts/Tajawal-ExtraBold.ttf", 80)
    wa = smoothstep(0.07, 0.32, p)
    who.stamp_centered(c, W / 2, H * 0.335, alpha=wa, color=tuple(hot),
                       glow=0.5, glow_color=deep, glow_sigma=30)
    en = layer(TIP_EN[idx], "fonts/BebasNeue-Regular.ttf", 40)
    en.stamp_centered(c, W / 2, H * 0.335 + 74, alpha=wa * 0.55,
                      color=tuple(np.clip(np.array(IVORY) * 0.72 +
                                          np.array(hot) * 0.28, 0, 255).tolist()))
    rule(c, W / 2, H * 0.335 + 126, 240, ease_out_cubic(smoothstep(0.16, 0.62, p)),
         alpha=0.75, color=hot, thickness=3)

    theft_meter(c, t, hot, idx, alpha=min(1.0, wa * 1.6))

    q1, q2 = TIP_Q1[idx], TIP_Q2[idx]
    f1 = "fonts/Tajawal-ExtraBold.ttf"
    ha = np.array(hot, np.float32)
    c_hi = tuple(np.clip(ha * 0.9 + np.array([56, 44, 28], np.float32), 0, 255).tolist())
    c_lo = tuple(np.clip(ha * 0.60 + np.array([22, 14, 6], np.float32), 0, 255).tolist())
    Line(q1, f1, fit_px(q1, 980, 112, f1, 62), grad=(c_hi, c_lo)).draw(
        c, W / 2, H * 0.478, alpha=smoothstep(0.42, 0.78, p),
        reveal=(p - 0.42) * 1.7, glow=0.38, glow_color=deep, glow_sigma=32)
    f2 = "fonts/Tajawal-Bold.ttf"
    c2hi = tuple(np.clip(np.array(IVORY) * 0.65 + ha * 0.35, 0, 255).tolist())
    c2lo = tuple(np.clip(np.array(IVORY) * 0.56 + ha * 0.28, 0, 255).tolist())
    Line(q2, f2, fit_px(q2, 1000, 76, f2, 46), grad=(c2hi, c2lo)).draw(
        c, W / 2, H * 0.568, alpha=smoothstep(0.92, 1.35, p) * 0.95,
        reveal=(p - 0.92) * 1.6, rise=18)

    # a tiny hit on the key word of each law — reward, not decoration
    key = q1.split()[0]
    for (w, a0, b0) in times:
        if w.strip("….,؟:،") == key.strip("….,؟:،") and a0 - 0.02 <= t <= b0 + 0.3:
            kk = smoothstep(a0 - 0.05, a0 + 0.05, t) * (1 - smoothstep(b0 + 0.18, b0 + 0.30, t))
            SH.anamorphic_flare(c, 0.5, 0.478, hot=hot, alpha=kk * 0.14)
            SH.shockwave(c, t, a0, hot=hot, speed=820, life=0.65, thick=12)

    caption_block(c, t, times, hot, y=0.775, px=76)

    frac = np.clip(p / max(0.1, sec["end"] - sec["start"]), 0, 1)
    prog = (idx - 1 + frac) / N_TIPS
    bw, y0 = 340, int(H * 0.885)
    x0 = int(W / 2 - bw / 2)
    cv2.line(c, (x0, y0), (x0 + bw, y0), tuple((np.array(IVORY) * 0.14).tolist()), 3, cv2.LINE_AA)
    cv2.line(c, (x0, y0), (x0 + int(bw * prog), y0),
             tuple((np.array(hot) * 0.92).tolist()), 3, cv2.LINE_AA)
    cv2.circle(c, (x0 + int(bw * prog), y0), 6, tuple(np.array(hot).tolist()), -1, cv2.LINE_AA)
    layer(f"{idx} / {N_TIPS}", "fonts/BebasNeue-Regular.ttf", 36).stamp_centered(
        c, W / 2, y0 + 46, alpha=0.42, color=tuple((np.array(hot) * 0.85).tolist()))

    if p < 0.15:
        radial_flash(c, W / 2, H * 0.30, 580, hot, (0.15 - p) / 0.15 * 0.45)
    if final:
        beat = 0.0
        for hb in (0.30, 1.25, 2.10, 2.95, 3.80):
            if hb <= p < hb + 0.45:
                beat = math.exp(-(p - hb) * 10)
        c *= (1 - 0.20 * beat)
        c += np.exp(-((np.arange(H) - H * 0.5) / (H * 0.42)) ** 2)[:, None, None] * \
            np.array([28, 3, 0], np.float32) * (0.35 + 0.65 * beat)
    # 3-frame flash-cut so law changes feel intentional
    tail = sec["end"] - sec["start"] - p
    if tail < 0.10:
        c += (0.10 - tail) / 0.10 * 34
    return c


# ------------------------------------------------------------------ captions
def caption_groups(times):
    groups, cur = [], []
    for i, (w, a, b) in enumerate(times):
        if cur and a - times[cur[-1]][2] > 0.30:
            groups.append(cur)
            cur = []
        cur.append(i)
        if len(cur) >= 4:
            groups.append(cur)
            cur = []
    if cur:
        groups.append(cur)
    return groups


def caption_block(canvas, t, times, hot, y=0.775, px=76):
    if not times or t < times[0][1] - 0.12:
        return canvas
    groups = caption_groups(times)
    cur = groups[0]
    for g in groups:
        if times[g[0]][1] <= t + 0.05:
            cur = g
    if t > times[cur[-1]][2] + 0.5:
        return canvas
    f = "fonts/Tajawal-Bold.ttf"
    spoken = [(i, times[i][0].strip("….,؟:،")) for i in cur if times[i][1] <= t + 0.06]
    spoken = [(i, w) for i, w in spoken if w]
    if not spoken:
        return canvas
    size = px
    while size > 40:
        laid = [(i, layer(w, f, size)) for i, w in spoken]
        if sum(L.w for _, L in laid) + 26 * (len(laid) - 1) <= 960:
            break
        size -= 4
    laid = [(i, layer(w, f, size)) for i, w in spoken]
    gap = 26
    rw = sum(L.w for _, L in laid) + gap * (len(laid) - 1)
    row_h = max(L.h for _, L in laid)
    appear = smoothstep(times[cur[0]][1] - 0.07, times[cur[0]][1] + 0.09, t)
    yy = H * y - row_h / 2
    x = W / 2 + rw / 2
    for i, L in laid:
        x -= L.w
        on = times[i][1] - 0.05 <= t <= times[i][2] + 0.16
        a = appear * (1.0 if on else 0.74)
        col = tuple(np.clip(np.array(hot) * 0.62 + np.array([250, 246, 238]) * 0.62,
                            0, 255).tolist()) if on else tuple(np.array([236, 232, 224]).tolist())
        L.stamp(canvas, x, yy, alpha=a, color=col, glow=0.34 if on else 0.10, glow_sigma=18)
        x -= gap
    return canvas


# ------------------------------------------------------------------ outro
def scene_outro(t, sec):
    p = t - sec["start"]
    c = SH.nebula(t * 0.85, hot=(255, 195, 120), intensity=0.9, scale=1.9)
    c = vignette_plate(c, 0.82)
    c += SH.stars(t, intensity=0.42, seed=9)
    c += SH.god_rays(t, hot=(255, 200, 130), intensity=0.38, cy=0.18)
    c = scrims(c, bands=((0.405, 0.075, 0.40), (0.49, 0.085, 0.34),
                         (0.556, 0.075, 0.30), (0.655, 0.10, 0.28)))

    a1 = smoothstep(0.08, 0.50, p)
    Line(OUTRO["title"], "fonts/Tajawal-ExtraBold.ttf", 88).draw(
        c, W / 2, H * 0.395, alpha=a1, reveal=(p - 0.08) * 1.8, glow=0.5,
        glow_color=(255, 170, 60), glow_sigma=34)
    a2 = smoothstep(1.05, 1.55, p)
    Line(OUTRO["l2"], "fonts/Tajawal-Bold.ttf", 60).draw(
        c, W / 2, H * 0.483, alpha=a2 * 0.96, reveal=(p - 1.05) * 1.6, color=tuple(IVORY))
    a3 = smoothstep(1.80, 2.30, p)
    Line(OUTRO["l3"], "fonts/Tajawal-Bold.ttf", 56).draw(
        c, W / 2, H * 0.548, alpha=a3 * 0.92, reveal=(p - 1.80) * 1.6, color=tuple(IVORY))

    a4 = smoothstep(2.9, 3.5, p)
    if a4 > 0.01:
        bx, by, bw, bh = W / 2, H * 0.652, 720, 128
        ov = c.copy()
        cv2.rectangle(ov, (int(bx - bw / 2), int(by - bh / 2)),
                      (int(bx + bw / 2), int(by + bh / 2)), (255, 255, 255), -1)
        c[:] = c * (1 - 0.13 * a4) + ov * (0.13 * a4)
        cv2.rectangle(c, (int(bx - bw / 2), int(by - bh / 2)),
                      (int(bx + bw / 2), int(by + bh / 2)), tuple(GOLD.tolist()), 4, cv2.LINE_AA)
        layer(OUTRO["cta"], "fonts/Tajawal-ExtraBold.ttf", 56).stamp_centered(
            c, bx, by, alpha=a4, color=tuple(GOLD_LIGHT), glow=0.45,
            glow_color=(255, 170, 60), glow_sigma=24)
        SH.anamorphic_flare(c, bx / W, by / H, hot=(255, 190, 120), alpha=a4 * 0.16)
    a5 = smoothstep(4.0, 4.6, p)
    if a5 > 0.01:
        layer(OUTRO["comment"], "fonts/Tajawal-Bold.ttf", 42).stamp_centered(
            c, W / 2, H * 0.742, alpha=a5 * 0.75, color=tuple(IVORY))
    a6 = smoothstep(4.9, 5.4, p)
    if a6 > 0.01:
        layer(OUTRO["follow"], "fonts/Tajawal-Bold.ttf", 42).stamp_centered(
            c, W / 2, H * 0.812, alpha=a6 * 0.62, color=tuple(GOLD))
    return c


# ------------------------------------------------------------------ grade
def apply_grade(c):
    c = SH.bloom_halation(np.clip(c, 0, 255), bloom=0.95, bloom_thr=132,
                          halo=0.30, halo_thr=140)
    c = np.clip(c, 0, 255) / 255.0
    c = c * 0.97 / (1 + c * 0.13)
    c *= VIG
    c = np.clip(c, 0, 1) ** (1 / 1.03)
    g = c.mean(axis=2, keepdims=True)
    c = g + (c - g) * 1.07
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

    if p < 0.22 and kind != "hook":
        rng = np.random.default_rng(int(t * 1000))
        c = gfx.glitch_slice(c, t, rng, amount=(0.22 - p) / 0.22 * 0.9)
        c = np.roll(c, int(16 * (0.22 - p) / 0.22), axis=1)
    if p < 0.18:
        c = chroma_shift(c, 10 * (0.18 - p) / 0.18)

    span = max(1.0, sec["end"] - sec["start"])
    z = 1.0 + (0.035 if kind == "hook" else 0.05) * min(1.0, p / span)
    if z > 1.001:
        c = cv2.resize(c, None, fx=z, fy=z, interpolation=cv2.INTER_LINEAR)
        y0 = (c.shape[0] - H) // 2
        x0 = (c.shape[1] - W) // 2
        c = c[y0:y0 + H, x0:x0 + W]

    c = apply_grade(c)
    c = SH.gate_weave(c, t)
    rng = np.random.default_rng(11 + fi % 7)
    add_grain(c, rng, 0.011)
    if t < 0.14:
        c *= 0.35 + 0.65 * smoothstep(0.0, 0.14, t)   # keep the opening smash visible
    if t > TOTAL - 0.9:
        c *= 1 - smoothstep(TOTAL - 0.9, TOTAL - 0.04, t)
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
