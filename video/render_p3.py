"""Part-3 renderer: same engine, far more aggressive look book.

This module subclasses nothing — it reuses the shared primitives (Arabic
shaping, glow, grade) but replaces every scene with a shader-driven one and
rebuilds the hook as a 3-beat kinetic sequence designed to win the first
three seconds.

Why a separate module instead of more branches in render.py:
  * the hook needs a genuinely different rhythm (3 hard cuts in 2.6 s),
  * every law gets its own look (tunnel / grid / orbs / vortex / shards /
    nebula / inferno) instead of the small fx_* variations,
  * grading gets film halation + gate weave, which only looks right when the
    underlying plates are the new ones.
"""
import math
import os
import subprocess
import sys

import cv2
import numpy as np

cv2.setNumThreads(1)

from .core import (W, H, FPS, GOLD, GOLD_LIGHT, GOLD_DEEP, IVORY,
                   add_grain, vignette_map, chroma_shift, radial_flash,
                   smoothstep, ease_out_cubic, ease_in_out, lerp, mix_color)
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
TOP_LABEL = EP["top_label"]
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


def fit_px(text, max_w, base_px, font, min_px=54):
    px = base_px
    while px > min_px:
        if layer(text, font, px).w <= max_w:
            return px
        px -= 4
    return min_px


def beat_window(times, phrase, t, hold=0.16):
    words = phrase.split()
    text = [w for (w, a, b) in times]
    nrm = lambda s: s.strip("….,؟:،")
    for i in range(len(text) - len(words) + 1):
        if [nrm(w) for w in text[i:i + len(words)]] != [nrm(w) for w in words]:
            continue
        a0 = times[i][1]
        a1 = times[i + len(words) - 1][2]
        if t < a0 - 0.12 or t > a1 + hold:
            return 0.0, a0
        ina = smoothstep(a0 - 0.12, a0 + 0.04, t)
        outa = 1 - smoothstep(a1 + 0.03, a1 + hold, t)
        return ina * outa, a0
    return 0.0, None


# ------------------------------------------------------------------ plates
def plate(look, t, hot, intensity=1.0):
    """Return an additive background plate for one law's signature look."""
    if look == "tunnel":
        return SH.tunnel(t, hot=hot, intensity=intensity * 0.9)
    if look == "tunnel_hot":
        return SH.tunnel(t * 1.25, hot=hot, intensity=intensity * 1.1, rings=26)
    if look == "energy":
        return SH.energy_grid(t, hot=hot, intensity=intensity)
    if look == "orbs":
        return SH.orbs(t, hot=hot, intensity=intensity * 0.85)
    if look == "vortex":
        return SH.vortex(t, hot=hot, intensity=intensity * 0.9)
    if look == "shards":
        return SH.shards(t, hot=hot, intensity=intensity)
    if look == "nebula":
        return SH.nebula(t, hot=hot, intensity=intensity * 1.1)
    return SH.deep_space(t, hot=hot, intensity=intensity)


def _drift(c, q, zoom=0.06, pan=18.0, spin=0.0):
    """Sub-degree camera move so no hook shot is ever a still frame."""
    q = max(0.0, q)
    z = 1.0 + zoom * min(1.0, q / 2.4)
    M = cv2.getRotationMatrix2D((W / 2, H / 2), spin * math.sin(q * 1.1) * 0.35, z)
    M[0, 2] += pan * math.sin(q * 0.9)
    M[1, 2] += 9.0 * math.sin(q * 0.7 + 0.6)
    out = cv2.warpAffine(c.astype(np.float32), M, (W, H), flags=cv2.INTER_LINEAR,
                         borderMode=cv2.BORDER_REFLECT)
    return out


def vignette_plate(img, dark=0.72):
    """Crush the edges of a plate so type always wins."""
    return img * VIG ** 1.15


def scrims(canvas, bands=((0.28, 0.075, 0.52), (0.515, 0.085, 0.34), (0.775, 0.115, 0.48))):
    ys = np.arange(H, dtype=np.float32) / H
    for y, s, a in bands:
        canvas *= (1 - a * np.exp(-(((ys - y) / s) ** 2)))[..., None] if False else \
            (1 - a * np.exp(-(((ys - y) / s) ** 2)))[:, None, None]
    return canvas


# ------------------------------------------------------------------ hook (3 s weapon)
def scene_hook(t, sec):
    """Five hard cuts in 11 s — every cut starts on a real word onset.

    A 0.00  strike        "توقف"                       (white / red)
    B 1.12  vortex        "أنت لا تفشل… بسبب سوء الحظ"  (orange)
    C 4.28  tunnel + "7"  "سبعة أخطاء… ترتكبها كل يوم"  (amber)
    D 6.55  tron grid     "دون أن تشعر"                (ice blue)
    E 8.18  red nebula    "والخطأ السابع… يقتلك بصمت"   (blood red)

    The first three seconds therefore contain two complete images and the
    video's whole promise, which is what decides whether a scroll stops.
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

    t_stop, _ = at("توقف")                              # 0.30
    t_you, _ = at("أنت")                                # 1.24
    t_luck, _ = at("بسبب")                              # 1.96
    t_seven, _ = at("سبعة")                             # 4.40
    t_commit, _ = at("ترتكبها")                         # 5.73
    t_feel, _ = at("دون")                               # 6.63
    t_seventh, _ = at("والخطأ")                         # 8.32
    t_kill, _ = at("يقتلك")                             # 10.08

    CUT_A = (t_you or 1.24) - 0.12
    CUT_B = (t_seven or 4.40) - 0.12
    CUT_C = (t_feel or 6.63) - 0.10
    CUT_D = (t_seventh or 8.32) - 0.14

    # ============================================================ A · strike
    if p < CUT_A:
        c = np.full((H, W, 3), 4.0, np.float32)
        c += SH.nebula(t * 1.6, hot=(90, 120, 210), intensity=0.20, scale=2.6) * 0.5
        c = vignette_plate(c, 0.7)
        c += max(0.0, 1 - p / 0.12) * 215          # 3-frame white smash
        k = smoothstep(t_stop - 0.05, t_stop + 0.09, p)
        L = grad_layer("توقف", "fonts/Tajawal-ExtraBold.ttf", 300, stroke=9,
                       c1=(255, 252, 235), c2=(255, 150, 40))
        L.stamp_centered(c, W / 2, H * 0.455, alpha=k,
                         scale=1.30 - 0.30 * ease_out_cubic(np.clip((p - t_stop) / 0.4, 0, 1)),
                         glow=0.55, glow_color=(255, 120, 40), glow_sigma=44)
        by = int(H * 0.545)
        cv2.line(c, (86, by), (W - 86, by), (255, 246, 236), 4, cv2.LINE_AA)
        layer("سبعة أخطاء… تفعلها كل يوم", "fonts/Tajawal-Bold.ttf", 46).stamp_centered(
            c, W / 2, H * 0.585, alpha=k * 0.8, color=tuple(IVORY))
        SH.anamorphic_flare(c, 0.5, 0.455, hot=(255, 240, 220), alpha=k * 0.45)
        return _drift(c, p, 0.05)

    # ============================================================ B · vortex
    if p < CUT_B:
        c = SH.vortex(t * 1.7, hot=(255, 115, 35), intensity=1.25)
        c = vignette_plate(c, 0.86)
        c += SH.stars(t, intensity=0.26, seed=31)
        SH.anamorphic_flare(c, 0.5, 0.445, hot=(255, 150, 80), alpha=0.45)
        c = scrims(c, bands=((0.45, 0.105, 0.70), (0.545, 0.08, 0.58)))
        k = smoothstep(t_you - 0.14, t_you + 0.02, p)
        Line("أنت لا تفشل", "fonts/Tajawal-ExtraBold.ttf", 126).draw(
            c, W / 2, H * 0.43, alpha=k, reveal=(p - t_you + 0.14) * 3.0,
            glow=0.55, glow_color=(255, 60, 10), glow_sigma=34)
        k2 = smoothstep(t_luck - 0.12, t_luck + 0.02, p)
        Line("بسبب سوء الحظ", "fonts/Tajawal-Bold.ttf", 82).draw(
            c, W / 2, H * 0.522, alpha=k2 * 0.98, color=tuple(IVORY),
            reveal=(p - t_luck + 0.12) * 2.8, rise=16,
            glow=0.2 if k2 > 0.5 else 0.0, glow_color=(255, 120, 60), glow_sigma=18)
        # punch on the second line so the 3 s mark lands on a beat
        if k2 > 0.0:
            SH.anamorphic_flare(c, 0.5, 0.522, hot=(255, 170, 120),
                                alpha=max(0.0, 0.5 - (p - t_luck) * 2.0) * 0.5)
        return _drift(c, p - CUT_A, 0.075, spin=0.6)

    # ============================================================ C · the 7
    if p < CUT_C:
        c = SH.tunnel(t * 1.5, hot=(255, 205, 130), intensity=1.0, rings=11)
        c = vignette_plate(c, 0.9)
        c += SH.speed_lines(t, hot=(255, 235, 190), intensity=0.28)
        c = scrims(c, bands=((0.40, 0.10, 0.60), (0.605, 0.10, 0.58)))
        k = smoothstep(t_seven - 0.13, t_seven + 0.02, p)
        pop = 1.0 + 0.62 * (1 - ease_out_cubic(np.clip((p - t_seven) / 0.5, 0, 1)))
        num = TextLayer("7", "fonts/Anton-Regular.ttf", 720,
                        gradient=(np.array([255, 255, 248], np.float32),
                                  np.array([255, 140, 30], np.float32)))
        num.stamp_centered(c, W / 2, H * 0.345, alpha=k, scale=pop,
                           glow=0.7, glow_color=(255, 110, 30), glow_sigma=54)
        SH.shockwave(c, t, t_seven - 0.02, hot=(255, 200, 120), speed=2100, life=0.9)
        Line("سبعة أخطاء", "fonts/Tajawal-ExtraBold.ttf", 112).draw(
            c, W / 2, H * 0.565, alpha=k, reveal=(p - t_seven + 0.1) * 3.0,
            glow=0.55, glow_color=(255, 90, 30), glow_sigma=32)
        k3 = smoothstep(t_commit - 0.12, t_commit + 0.02, p)
        Line("ترتكبها كل يوم", "fonts/Tajawal-Bold.ttf", 76).draw(
            c, W / 2, H * 0.645, alpha=k3 * 0.95, color=tuple(IVORY),
            reveal=(p - t_commit + 0.12) * 2.6, rise=16)
        return _drift(c, p - CUT_B, 0.09)

    # ============================================================ D · grid
    if p < CUT_D:
        c = SH.energy_grid(t * 1.7, hot=(110, 200, 255), intensity=1.5, horizon=0.50)
        c = vignette_plate(c, 0.80)
        c += SH.stars(t, intensity=0.24, seed=44)
        c = scrims(c, bands=((0.465, 0.09, 0.50), (0.545, 0.07, 0.34)))
        k = smoothstep(t_feel - 0.13, t_feel + 0.02, p)
        Line("دون أن تشعر", "fonts/Tajawal-ExtraBold.ttf", 116).draw(
            c, W / 2, H * 0.475, alpha=k, reveal=(p - t_feel + 0.13) * 3.0,
            glow=0.6, glow_color=(70, 150, 255), glow_sigma=34)
        SH.shockwave(c, t, t_feel - 0.02, hot=(110, 200, 255), speed=1500, life=0.8)
        return _drift(c, p - CUT_C, 0.07, pan=40)

    # ============================================================ E · the kill
    c = SH.nebula(t * 0.8, hot=(255, 70, 30), intensity=0.85, scale=2.2)
    c = vignette_plate(c, 0.95)
    c += SH.god_rays(t, hot=(255, 80, 30), intensity=0.30, cy=0.30)
    c = scrims(c, bands=((0.455, 0.10, 0.66), (0.56, 0.095, 0.62)))
    k = smoothstep(t_seventh - 0.13, t_seventh + 0.02, p)
    layer("والخطأ السابع", "fonts/Tajawal-Bold.ttf", 60).stamp_centered(
        c, W / 2, H * 0.435, alpha=k * 0.9, color=tuple(IVORY))
    k2 = smoothstep(t_kill - 0.16, t_kill + 0.02, p)
    if k2 > 0.02:
        SH.shockwave(c, t, t_kill - 0.03, hot=(255, 70, 25), speed=1700, life=0.85)
        SH.anamorphic_flare(c, 0.5, 0.545, hot=(255, 110, 60), alpha=k2 * 0.45)
    L = grad_layer("يقتلك بصمت", "fonts/Tajawal-ExtraBold.ttf", 152, stroke=5,
                   c1=(255, 250, 242), c2=(255, 80, 32))
    L.stamp_centered(c, W / 2, H * 0.545, alpha=k2,
                     scale=1.22 - 0.22 * ease_out_cubic(np.clip((p - t_kill) / 0.45, 0, 1)),
                     glow=0.8, glow_color=(255, 30, 8), glow_sigma=44)
    if p > sec["end"] - sec["start"] - 0.40:
        k3 = smoothstep(sec["end"] - sec["start"] - 0.40,
                        sec["end"] - sec["start"] - 0.02, p)
        c *= (1 - 0.95 * k3)
    return _drift(c, p - CUT_D, 0.08)


# ------------------------------------------------------------------ tips
def scene_tip(t, sec):
    p = t - sec["start"]
    idx = sec["idx"]
    hot, deep = TIP_COL[idx]
    look = TIP_LOOK[idx]
    final = idx == N_TIPS
    times = tip_times(sec)

    c = plate(look, t, hot, intensity=1.0 if not final else 1.15)
    c = vignette_plate(c, 0.75 if not final else 0.88)
    c += SH.stars(t, intensity=0.32, seed=idx * 7)
    c += SH.god_rays(t, hot=hot, intensity=0.22, cy=0.30, seed=idx)
    c = scrims(c, bands=((0.28, 0.075, 0.58), (0.475, 0.085, 0.48),
                         (0.565, 0.085, 0.44), (0.775, 0.115, 0.52)))

    # ---- number + name
    intro = smoothstep(0.0, 0.22, p)
    pop = 1.0 + 0.5 * (1 - ease_out_cubic(np.clip(p / 0.30, 0, 1)))
    num = TextLayer(f"{idx:02d}", "fonts/Anton-Regular.ttf", 250,
                    gradient=(np.array([255, 246, 214], np.float32),
                              np.array(deep, np.float32)))
    num.stamp_centered(c, W / 2, H * 0.175, alpha=intro, scale=pop,
                       glow=0.55, glow_color=deep, glow_sigma=40)
    ring_r = int(152 * pop)
    cv2.ellipse(c, (int(W / 2), int(H * 0.175)), (ring_r, ring_r), 0, -90,
                -90 + 360 * ease_out_cubic(np.clip(p / 0.5, 0, 1)),
                tuple((np.array(hot) * 0.9).tolist()), 3, cv2.LINE_AA)
    who = layer(TIP_WHO[idx], "fonts/Tajawal-ExtraBold.ttf", 82)
    wa = smoothstep(0.08, 0.36, p)
    who.stamp_centered(c, W / 2, H * 0.335, alpha=wa, color=tuple(hot),
                       glow=0.5, glow_color=deep, glow_sigma=30)
    en = layer(TIP_EN[idx], "fonts/BebasNeue-Regular.ttf", 40)
    en.stamp_centered(c, W / 2, H * 0.335 + 74, alpha=wa * 0.55,
                      color=tuple(mix_color(np.array(IVORY), np.array(hot), 0.4)))
    rule(c, W / 2, H * 0.335 + 126, 240, ease_out_cubic(smoothstep(0.18, 0.7, p)),
         alpha=0.75, color=hot, thickness=3)
    eye = layer(["", "الخطأ الأول", "الخطأ الثاني", "الخطأ الثالث", "الخطأ الرابع",
                 "الخطأ الخامس", "الخطأ السادس", "الخطأ السابع"][idx],
                "fonts/Tajawal-Bold.ttf", 40)
    eye.stamp_centered(c, W / 2, 152, alpha=wa * 0.5, color=tuple(hot))

    # ---- quote
    q1, q2 = TIP_Q1[idx], TIP_Q2[idx]
    f1 = "fonts/Tajawal-ExtraBold.ttf"
    ha = np.array(hot, np.float32)
    c_hi = tuple(np.clip(ha * 0.9 + np.array([56, 44, 28], np.float32), 0, 255).tolist())
    c_lo = tuple(np.clip(ha * 0.60 + np.array([22, 14, 6], np.float32), 0, 255).tolist())
    l1 = Line(q1, f1, fit_px(q1, 980, 112, f1, 64), grad=(c_hi, c_lo))
    l1.draw(c, W / 2, H * 0.475, alpha=smoothstep(0.55, 0.95, p),
            reveal=(p - 0.55) * 1.5, glow=0.38, glow_color=deep, glow_sigma=32)
    f2 = "fonts/Tajawal-Bold.ttf"
    c2hi = tuple(mix_color(np.array(IVORY), ha * 0.9, 0.35).tolist())
    c2lo = tuple(mix_color(np.array(IVORY) * 0.82, ha * 0.75, 0.35).tolist())
    l2 = Line(q2, f2, fit_px(q2, 1000, 76, f2, 48), grad=(c2hi, c2lo))
    l2.draw(c, W / 2, H * 0.565, alpha=smoothstep(1.15, 1.65, p) * 0.94,
            reveal=(p - 1.15) * 1.5, rise=20)

    # ---- kinetic strike on the law's key word
    key = q1.split()[0]
    for i, (w, a0, b0) in enumerate(times):
        if w.strip("….,؟:،") == key.strip("….,؟:،") and a0 - 0.02 <= t <= b0 + 0.35:
            k = smoothstep(a0 - 0.05, a0 + 0.06, t) * (1 - smoothstep(b0 + 0.2, b0 + 0.35, t))
            SH.anamorphic_flare(c, 0.5, 0.475, hot=hot, alpha=k * 0.16)
            SH.shockwave(c, t, a0, hot=hot, speed=900, life=0.7, thick=14)

    # ---- captions
    caption_block(c, t, times, hot, y=0.775, px=76)
    law_frame(c, t, hot, idx, N_TIPS, alpha=wa)

    # ---- progress
    frac = np.clip(p / max(0.1, sec["end"] - sec["start"]), 0, 1)
    prog = (idx - 1 + frac) / N_TIPS
    bw, y0 = 340, int(H * 0.885)
    x0 = int(W / 2 - bw / 2)
    cv2.line(c, (x0, y0), (x0 + bw, y0), tuple((np.array(IVORY) * 0.14).tolist()), 3, cv2.LINE_AA)
    cv2.line(c, (x0, y0), (x0 + int(bw * prog), y0), tuple((np.array(hot) * 0.92).tolist()), 3, cv2.LINE_AA)
    cv2.circle(c, (x0 + int(bw * prog), y0), 6, tuple(np.array(hot).tolist()), -1, cv2.LINE_AA)
    lab = layer(f"{idx} / {N_TIPS}", "fonts/BebasNeue-Regular.ttf", 36)
    lab.stamp_centered(c, W / 2, y0 + 46, alpha=0.45, color=tuple((np.array(hot) * 0.85).tolist()))

    if p < 0.16:
        radial_flash(c, W / 2, H * 0.30, 580, hot, (0.16 - p) / 0.16 * 0.5)
    if final:
        beat = 0.0
        for hb in (0.35, 1.45, 2.35, 3.25, 4.15, 5.05):
            if hb <= p < hb + 0.5:
                beat = math.exp(-(p - hb) * 9)
        c *= (1 - 0.18 * beat)
        c += np.exp(-((np.arange(H) - H * 0.5) / (H * 0.42)) ** 2)[:, None, None] * \
            np.array([26, 3, 0], np.float32) * (0.35 + 0.65 * beat)
        SH.shockwave(c, t, sec["vo_start"] + 3.05, hot=(255, 60, 24), speed=800, life=0.9, thick=16)
    return c


# ------------------------------------------------------------------ captions
def caption_groups(times):
    groups, cur = [], []
    for i, (w, a, b) in enumerate(times):
        if cur and a - times[cur[-1]][2] > 0.30:
            groups.append(cur)
            cur = []
        cur.append(i)
        if len(cur) >= 5:
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
    if t > times[cur[-1]][2] + 0.55:
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
    appear = smoothstep(times[cur[0]][1] - 0.08, times[cur[0]][1] + 0.10, t)
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


def law_frame(canvas, t, hot, active, total, alpha=1.0):
    if alpha <= 0.01:
        return canvas
    for i in range(total):
        y = H * 0.20 + i * (H * 0.60 / max(1, total - 1))
        on = (i + 1) == active
        a = alpha * (0.60 if on else 0.16)
        col = tuple((np.array(hot) * a).tolist()) if on else tuple((np.array(IVORY) * a).tolist())
        cv2.line(canvas, (54, int(y)), (54 + (30 if on else 12), int(y)), col, 3, cv2.LINE_AA)
        cv2.line(canvas, (W - 54, int(y)), (W - 54 - (30 if on else 12), int(y)), col, 3, cv2.LINE_AA)
    return canvas


# ------------------------------------------------------------------ outro
def scene_outro(t, sec):
    p = t - sec["start"]
    c = SH.nebula(t * 0.85, hot=(255, 200, 130), intensity=0.95, scale=1.9)
    c = vignette_plate(c, 0.82)
    c += SH.stars(t, intensity=0.45, seed=9)
    c += SH.god_rays(t, hot=(255, 200, 130), intensity=0.40, cy=0.18)
    c = scrims(c, bands=((0.415, 0.075, 0.42), (0.50, 0.085, 0.34), (0.66, 0.10, 0.30)))

    a1 = smoothstep(0.10, 0.55, p)
    l1 = Line(OUTRO["title"], "fonts/Tajawal-ExtraBold.ttf", 96)
    l1.draw(c, W / 2, H * 0.405, alpha=a1, reveal=(p - 0.10) * 1.7, glow=0.5,
            glow_color=(255, 170, 60), glow_sigma=34)
    a2 = smoothstep(1.15, 1.7, p)
    Line(OUTRO["l2"], "fonts/Tajawal-Bold.ttf", 62).draw(
        c, W / 2, H * 0.492, alpha=a2 * 0.96, reveal=(p - 1.15) * 1.5, color=tuple(IVORY))
    a3 = smoothstep(1.95, 2.5, p)
    Line(OUTRO["l3"], "fonts/Tajawal-Bold.ttf", 58).draw(
        c, W / 2, H * 0.552, alpha=a3 * 0.92, reveal=(p - 1.95) * 1.5, color=tuple(IVORY))

    a4 = smoothstep(3.2, 3.9, p)
    if a4 > 0.01:
        bx, by, bw, bh = W / 2, H * 0.655, 720, 130
        ov = c.copy()
        cv2.rectangle(ov, (int(bx - bw / 2), int(by - bh / 2)),
                      (int(bx + bw / 2), int(by + bh / 2)), (255, 255, 255), -1)
        c[:] = c * (1 - 0.13 * a4) + ov * (0.13 * a4)
        cv2.rectangle(c, (int(bx - bw / 2), int(by - bh / 2)),
                      (int(bx + bw / 2), int(by + bh / 2)), tuple(GOLD.tolist()), 4, cv2.LINE_AA)
        layer(OUTRO["cta"], "fonts/Tajawal-ExtraBold.ttf", 58).stamp_centered(
            c, bx, by, alpha=a4, color=tuple(GOLD_LIGHT), glow=0.45,
            glow_color=(255, 170, 60), glow_sigma=24)
        SH.anamorphic_flare(c, bx / W, by / H, hot=(255, 190, 120), alpha=a4 * 0.18)
    a5 = smoothstep(4.5, 5.2, p)
    if a5 > 0.01:
        layer(OUTRO["comment"], "fonts/Tajawal-Bold.ttf", 42).stamp_centered(
            c, W / 2, H * 0.745, alpha=a5 * 0.75, color=tuple(IVORY))
    a6 = smoothstep(5.5, 6.1, p)
    if a6 > 0.01:
        layer(OUTRO["follow"], "fonts/Tajawal-Bold.ttf", 42).stamp_centered(
            c, W / 2, H * 0.815, alpha=a6 * 0.6, color=tuple(GOLD))
    return c


# ------------------------------------------------------------------ grade
def apply_grade(c):
    c = SH.bloom_halation(np.clip(c, 0, 255), bloom=0.95, bloom_thr=132,
                          halo=0.30, halo_thr=140)
    c = np.clip(c, 0, 255) / 255.0
    c = c * 0.97 / (1 + c * 0.13)          # gentle shoulder keeps highlights clean
    c *= VIG
    c = np.clip(c, 0, 1) ** (1 / 1.03)
    sat = 1.06
    g = c.mean(axis=2, keepdims=True)
    c = g + (c - g) * sat
    return np.clip(c, 0, 1)


def render_frame(fi, total_frames, resolve_sec):
    t = fi / FPS
    sec = resolve_sec(fi)
    kind = sec["kind"]
    c = scene_hook(t, sec) if kind == "hook" else \
        (scene_tip(t, sec) if kind == "tip" else scene_outro(t, sec))
    p = t - sec["start"]

    # cut-in punch: tiny zoom + glitch on every section boundary
    if p < 0.24 and kind != "hook":
        rng = np.random.default_rng(int(t * 1000))
        c = gfx.glitch_slice(c, t, rng, amount=(0.24 - p) / 0.24 * 0.9)
        c = np.roll(c, int(18 * (0.24 - p) / 0.24), axis=1)
    if p < 0.20:
        c = chroma_shift(c, 10 * (0.20 - p) / 0.20)

    # constant slow push-in
    span = max(1.0, sec["end"] - sec["start"])
    z = 1.0 + 0.05 * min(1.0, p / span)
    if kind == "hook":
        z = 1.0 + 0.035 * min(1.0, p / span)
    if z > 1.001:
        c = cv2.resize(c, None, fx=z, fy=z, interpolation=cv2.INTER_LINEAR)
        y0 = (c.shape[0] - H) // 2
        x0 = (c.shape[1] - W) // 2
        c = c[y0:y0 + H, x0:x0 + W]

    c = apply_grade(c)
    c = SH.gate_weave(c, t)
    rng = np.random.default_rng(11 + fi % 7)
    add_grain(c, rng, 0.012)
    if t < 0.28:
        c *= smoothstep(0.0, 0.28, t)
    if t > TOTAL - 1.0:
        c *= 1 - smoothstep(TOTAL - 1.0, TOTAL - 0.04, t)
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
