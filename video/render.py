"""Frame renderer — vertical 1080x1920 cinematic short.

Layout grid (safe areas for shorts/reels UI):
    y  90..170   top eyebrow / progress
    y 300..640   hero object (knot)
    y 520..900   number + attribution
    y 900..1220  the quote (2 lines, word-synced)
    y 1240..1400 spoken-word ticker
    y 1700..1790 bottom progress
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
                   smoothstep, ease_out_cubic, ease_out_expo, ease_in_cubic,
                   ease_in_out, ease_out_back, lerp, mix_color, fast_glow)
from . import gfx
from .core import TextLayer
from .text import Line, layer, grad_layer, number_badge, rule, badge_text
from .timeline import SECTIONS, TOTAL, align, TRUTH

VIG = vignette_map(0.70, 1.9)[..., None].astype(np.float32)
BOUNCE = (1 + 0.055 * np.sin(np.arange(H) / H * np.pi))[:, None, None].astype(np.float32)

TIP_COL = {
    1: ((255, 214, 130), (255, 140, 40)),
    2: ((255, 226, 168), (255, 172, 84)),
    3: ((206, 228, 255), (90, 160, 255)),
    4: ((255, 226, 180), (255, 186, 96)),
    5: ((255, 202, 196), (255, 96, 70)),
    6: ((200, 255, 228), (60, 220, 175)),
    7: ((255, 236, 218), (255, 74, 28)),
}

TIP_WORDS = {
    1: "الأولى، من سقراط: اعرف نفسك. فالحياة التي لا تُفحَص… لا تستحق أن تُعاش.".split(),
    2: "الثانية، من ماركوس أوريليوس: لديك سلطة على عقلك، لا على ما يحدث لك.".split(),
    3: "الثالثة، من سينيكا: نحن نتألم في الخيال… أكثر مما نتألم في الواقع. الخوف الذي في رأسك أكبر من كل ما سيحدث لك فعلاً.".split(),
    4: "الرابعة، من أرسطو: نحن ما نفعله مراراً. فالتميز ليس فعلاً… بل عادة.".split(),
    5: "الخامسة، من نيتشه: من يملك سبباً يعيش من أجله… يتحمل أي طريقة للحياة.".split(),
    6: "السادسة، من الإمام علي: قيمة كل امرئ ما يحسن. اسأل نفسك… ماذا تتقن؟".split(),
    7: "والسابعة، ليست نصيحة… إنها حقيقة. لا أحد قادم لينقذك. من تكون غداً… هو ما تفعله في هذه اللحظة.".split(),
}

_ALIGN_CACHE = {}

HOOK_WORDS = "توقف سبع جمل فقط غيّرت مصير البشرية والسابعة قد تغيّر حياتك قبل أن ينتهي هذا الفيديو".split()


_HOOK_TIMES = {}


def hook_times(sec):
    k = "hook"
    if k not in _HOOK_TIMES:
        words = HOOK_WORDS
        _HOOK_TIMES[k] = align(words, sec["segs"], sec["vo_start"])
    return _HOOK_TIMES[k]


def tip_words_times(sec):
    key = sec["idx"]
    if key not in _ALIGN_CACHE:
        _ALIGN_CACHE[key] = align(TIP_WORDS[key], sec["segs"], sec["vo_start"])
    return _ALIGN_CACHE[key]


def fit_px(text, max_w, base_px, font, min_px=54):
    """Largest px that keeps a single line under max_w."""
    px = base_px
    while px > min_px:
        L = layer(text, font, px)
        if L.w <= max_w:
            return px
        px -= 4
    return min_px


# ------------------------------------------------------------------ backdrops
def backdrop(t, sec, hot=(255, 190, 110), dark=(5, 6, 11), intensity=0.85, seed=3):
    idx = sec.get("idx", 0)
    h = np.array(hot, np.float32)
    hue_a = tuple((h * 0.62 + np.array([12, 16, 26], np.float32)).tolist())
    canvas = gfx.light_field(t, seed=seed + idx, hue_a=hue_a, hue_b=dark,
                             intensity=intensity, hot=hot, n_blobs=3)
    # soft vertical light shaft from above
    cx = W / 2
    shaft = np.exp(-((np.arange(W) - cx) / 330.0) ** 2)[None, :, None]
    canvas += shaft * np.array(hot, np.float32) * 0.055
    gfx.light_ray(canvas, 0.0, t, hot, alpha=0.045, width=150)
    return canvas


def grid_layer(t, hot, alpha=0.30, horizon=0.63):
    km = np.zeros((H // 2, W // 2, 3), np.float32)
    gfx.draw_grid_floor(km, t, hot, alpha=alpha, horizon=(H // 2) * horizon)
    return cv2.resize(km, (W, H), interpolation=cv2.INTER_LINEAR)


def hero(canvas, t, idx, alpha=1.0, scale=1.0, color=(255, 205, 130), cy=0.34):
    if alpha <= 0.01:
        return canvas
    km = np.zeros((H // 2, W // 2, 3), np.float32)
    gfx.draw_knot(km, t * 0.38 + idx * 0.55, 0.42 + 0.10 * math.sin(t * 0.45),
                  radius=250 * scale, color=color, thick=2, n=460,
                  cx=W // 4, cy=H // 4)
    km = cv2.resize(km, (W, H), interpolation=cv2.INTER_LINEAR)
    shift = int(H * cy) - (H // 2)
    km = np.roll(km, shift, axis=0)
    glow = cv2.resize(cv2.GaussianBlur(cv2.resize(km, (W // 4, H // 4),
                                                 interpolation=cv2.INTER_AREA), (0, 0), 9),
                      (W, H), interpolation=cv2.INTER_LINEAR)
    canvas += (km * 0.95 + glow * 0.75) * alpha
    return canvas


def dust_layer(t, seed=1, alpha=0.5, color=(255, 226, 180)):
    d = gfx.Dust(300, seed=seed)
    for k in range(int(t * 12) % 2 + 1):
        d.step(1 / 12)
    dm = np.zeros((H // 2, W // 2, 3), np.float32)
    gfx.draw_dust(dm, d, t, color, alpha=alpha)
    return cv2.resize(dm, (W, H), interpolation=cv2.INTER_LINEAR)


def top_bar(canvas, idx, alpha=1.0, hot=GOLD):
    if alpha <= 0.01:
        return
    a = alpha
    lab = layer("7 نصائح غيّرت التاريخ", "fonts/Tajawal-Bold.ttf", 38)
    lab.stamp_centered(canvas, W / 2, 128, alpha=0.42 * a, color=tuple(hot))
    if idx:
        n = layer(f"{idx:02d} / 07", "fonts/BebasNeue-Regular.ttf", 40)
        n.stamp_centered(canvas, W / 2, 186, alpha=0.55 * a, color=tuple(np.array(hot) * 0.9))


def caption_block(canvas, t, times, hot, max_lines=3, y=0.775, px=76):
    """Big karaoke captions for muted viewers: 2-4 words, current word lit."""
    if t < times[0][1] - 0.1:
        return canvas
    # soft scrim so captions read over anything
    ys = np.arange(H, dtype=np.float32) / H
    band = np.exp(-(((ys - y) / 0.075) ** 2))[:, None, None]
    canvas *= (1 - 0.42 * band)
    # find index of current word
    cur = 0
    for i, (w, a, b) in enumerate(times):
        if t >= a - 0.02:
            cur = i
    # build groups of ~3 words
    words = [w for (w, a, b) in times]
    group = 3 if len(words) <= 14 else 4
    g0 = (cur // group) * group
    chunk = list(range(g0, min(len(words), g0 + group)))
    texts = [words[i].strip("….,؟:") for i in chunk]
    texts = [x for x in texts if x]
    if not texts:
        return canvas
    span = times[min(len(times) - 1, g0 + group - 1)][2] - times[g0][1]
    appear = smoothstep(times[g0][1] - 0.08, times[g0][1] + 0.10, t)
    f = "fonts/Tajawal-Bold.ttf"
    # layout: wrap into up to 2 rows
    rows, cur_row, cur_w = [], [], 0
    for i, w in zip(chunk, texts):
        L = layer(w, f, px)
        if cur_row and cur_w + L.w + 24 > 980:
            rows.append(cur_row)
            cur_row, cur_w = [], 0
        cur_row.append((i, L))
        cur_w += L.w + 24
    rows.append(cur_row)
    total_h = len(rows) * (px + 18)
    yy = H * y - total_h / 2
    for row in rows:
        rw = sum(L.w for _, L in row) + 24 * (len(row) - 1)
        x = W / 2 - rw / 2
        row_h = max(L.h for _, L in row)
        for i, L in row:
            on = times[i][1] - 0.05 <= t <= times[i][2] + 0.12
            a = appear * (0.97 if on else 0.66)
            col = tuple(mix_color(np.array(IVORY), np.array(hot), 0.85)) if on else tuple(IVORY)
            # place RTL: rightmost first
            pass
        # RTL order: reverse x placement
        x = W / 2 + rw / 2
        for i, L in row:
            x -= L.w
            on = times[i][1] - 0.05 <= t <= times[i][2] + 0.12
            a = appear * (1.0 if on else 0.74)
            col = tuple(np.clip(np.array(hot) * 0.62 + np.array([250, 246, 238]) * 0.62,
                                0, 255).tolist()) if on else tuple((np.array([236, 232, 224])).tolist())
            L.stamp(canvas, x, yy + (row_h - L.h) / 2, alpha=a, color=col,
                    glow=0.30 if on else 0.10, glow_sigma=18)
            x -= 24
        yy += row_h + 18
    return canvas


def fx_rings(canvas, t, hot, cx=W / 2, cy=None, count=5):
    cy = H * 0.5 if cy is None else cy
    for i in range(count):
        r = 260 + ((t * 90 + i * 150) % 900)
        a = 0.22 * (1 - (r - 260) / 900)
        if a <= 0.005:
            continue
        cv2.circle(canvas, (int(cx), int(cy)), int(r),
                   tuple((np.array(hot) * a).tolist()), 2, cv2.LINE_AA)
    return canvas


def fx_rain(canvas, t, hot, n=70, speed=900):
    rng = np.random.default_rng(5)
    xs = rng.uniform(0, W, n)
    ph = rng.uniform(0, 1, n)
    ln = rng.uniform(40, 140, n)
    for i in range(n):
        y = ((t * speed + ph[i] * H) % (H + 200)) - 100
        x = xs[i] + (y - H / 2) * 0.10
        a = 0.045 + 0.05 * (i % 3) / 2
        cv2.line(canvas, (int(x), int(y)), (int(x + ln[i] * 0.10), int(y + ln[i] * 1.6)),
                 tuple((np.array(hot) * a).tolist()), 2, cv2.LINE_AA)
    return canvas


def fx_dots(canvas, t, hot, step=96):
    for j in range(0, H, step):
        for i in range(0, W, step):
            ph = (i * 0.013 + j * 0.019)
            a = 0.05 + 0.09 * max(0.0, math.sin(t * 2.2 - ph))
            cv2.circle(canvas, (i, j), 2, tuple((np.array(hot) * a).tolist()), -1)
    return canvas


# ------------------------------------------------------------------ scenes
def scene_hook(t, sec):
    p = t - sec["start"]
    canvas = backdrop(t, sec, hot=(255, 160, 70), intensity=0.95, seed=5)
    canvas += dust_layer(t, seed=4, alpha=0.42)
    hero(canvas, t, 0, alpha=0.34 * smoothstep(0, 1.2, p), scale=1.15 + 0.06 * math.sin(t * 0.5),
         color=(255, 190, 105), cy=0.34)
    canvas += grid_layer(t, (255, 180, 100), alpha=0.16)
    # faint numeral watermark
    seven = grad_layer("7", "fonts/Anton-Regular.ttf", 900)
    seven.stamp_centered(canvas, W / 2, H * 0.33, alpha=0.03,
                         color=tuple((np.array(GOLD) * 0.7).tolist()))

    # ---- beat 1: "توقف"
    if p < 1.2:
        q = float(np.clip((p - 0.02) / 0.22, 0, 1))
        sc = 1.0 + 0.5 * (1 - ease_out_cubic(q))
        a = q * (1 - smoothstep(0.92, 1.2, p))
        stop = grad_layer("توقف", "fonts/Tajawal-ExtraBold.ttf", 230, stroke=6)
        stop.stamp_centered(canvas, W / 2, H * 0.44, alpha=a, scale=sc,
                            glow=0.55, glow_color=(255, 110, 40), glow_sigma=46)
        if 0.0 < p < 0.5:
            radial_flash(canvas, W / 2, H * 0.44, 520, (255, 120, 60),
                         (0.5 - p) / 0.5 * 0.35)
    # ---- beat 2: "سبع جمل فقط"
    if 1.05 < p < 3.3:
        q = smoothstep(1.05, 1.42, p)
        out = 1 - smoothstep(3.02, 3.3, p)
        big = grad_layer("سبع جمل", "fonts/Tajawal-ExtraBold.ttf", 200, stroke=6)
        big.stamp_centered(canvas, W / 2, H * 0.40, alpha=q * out,
                          glow=0.6, glow_color=(255, 170, 70), glow_sigma=40)
        sm = layer("فقط", "fonts/Tajawal-Bold.ttf", 100)
        sm.stamp_centered(canvas, W / 2, H * 0.505, alpha=q * out * 0.92, color=tuple(IVORY))
    # ---- beat 3
    if 3.25 < p < 5.5:
        q = smoothstep(3.3, 3.7, p)
        out = 1 - smoothstep(5.2, 5.5, p)
        l = Line("غيّرت مصير البشرية", "fonts/Tajawal-ExtraBold.ttf",
                 fit_px("غيّرت مصير البشرية", 960, 100, "fonts/Tajawal-ExtraBold.ttf"))
        l.draw(canvas, W / 2, H * 0.42, alpha=q * out, reveal=(p - 3.3) * 1.7,
               glow=0.42, glow_color=(255, 160, 60), glow_sigma=30)
        sub = layer("في تاريخ الكون", "fonts/Tajawal-Bold.ttf", 62)
        sub.stamp_centered(canvas, W / 2, H * 0.495, alpha=q * out * 0.78, color=tuple(IVORY))
    # ---- beat 4: "والسابعة؟"
    if 5.45 < p < 7.1:
        q = smoothstep(5.5, 5.85, p)
        out = 1 - smoothstep(6.85, 7.1, p)
        pulse = 1 + 0.022 * math.sin(p * 9)
        l = grad_layer("والسابعة؟", "fonts/Tajawal-ExtraBold.ttf", 175, stroke=5)
        l.stamp_centered(canvas, W / 2, H * 0.42, alpha=q * out, scale=pulse,
                         glow=0.6, glow_color=(255, 55, 28), glow_sigma=46)
        det = layer("الشريحة التي لا يتحدث عنها أحد", "fonts/Tajawal-Bold.ttf", 44)
        det.stamp_centered(canvas, W / 2, H * 0.505, alpha=q * out * 0.6, color=tuple(IVORY))
    # ---- beat 5: promise
    if 7.0 < p < 10.5:
        q = smoothstep(7.05, 7.5, p)
        out = 1 - smoothstep(10.1, 10.5, p)
        l1 = Line("قد تغيّر حياتك", "fonts/Tajawal-ExtraBold.ttf", 104)
        l1.draw(canvas, W / 2, H * 0.40, alpha=q * out, reveal=(p - 7.05) * 1.7,
                glow=0.45, glow_color=(255, 150, 50), glow_sigma=32)
        l2 = Line("قبل أن ينتهي هذا الفيديو", "fonts/Tajawal-Bold.ttf", 58)
        l2.draw(canvas, W / 2, H * 0.49, alpha=q * out * 0.85, reveal=(p - 7.5) * 1.7,
                color=tuple(IVORY))
        if p > 8.4:
            k = smoothstep(8.4, 9.0, p) * out
            for i in range(3):
                w = layer("٠١ ٠٢ ٠٣ ٠٤ ٠٥ ٠٦ ٠٧".split()[i], "fonts/Tajawal-Bold.ttf", 40)
                w.stamp_centered(canvas, W / 2 - 150 + i * 150, H * 0.565,
                                 alpha=k * 0.5, color=tuple(np.array(GOLD) * 1.1))
    top_bar(canvas, 0, alpha=smoothstep(0.3, 0.9, p) * (1 - smoothstep(9.9, 10.3, p)))
    if p > 0.4:
        caption_block(canvas, t, hook_times(sec), (255, 200, 130), y=0.80, px=74)
    return canvas


def scene_tip(t, sec):
    p = t - sec["start"]
    idx = sec["idx"]
    vs = sec["vo_start"]
    hot, deep = TIP_COL[idx]
    final = idx == 7
    inten = {1: 0.78, 2: 0.82, 3: 0.50, 4: 0.86, 5: 0.66, 6: 0.62, 7: 0.58}[idx]
    canvas = backdrop(t, sec, hot=hot, intensity=inten,
                      dark=(9, 4, 4) if final else (5, 6, 11))
    canvas += dust_layer(t, seed=idx, alpha=0.34, color=hot)
    hero(canvas, t, idx, alpha=0.16 + 0.02 * idx, scale=0.72, color=hot, cy=0.225)
    canvas += grid_layer(t, hot, alpha=0.13 if not final else 0.2)

    # ---------------- number + attribution block (no overlap)
    intro = smoothstep(0.0, 0.26, p)
    np_ = float(np.clip((p - 0.01) / 0.24, 0, 1))
    punch = 1.0 + 0.45 * (1 - ease_out_cubic(np_))
    num_hi = tuple(np.clip(np.array([255, 240, 190], np.float32) * 0.98, 0, 255).tolist())
    num_lo = tuple(np.clip(np.array([226, 168, 62], np.float32) * 0.95, 0, 255).tolist())
    numL = TextLayer(f"{idx:02d}", "fonts/Anton-Regular.ttf", 250,
                     gradient=(np.array(num_hi, np.float32), np.array(num_lo, np.float32)))
    numL.stamp_centered(canvas, W / 2, H * 0.175, alpha=intro, scale=punch,
                        glow=0.45, glow_color=deep, glow_sigma=34)
    # ring around the numeral
    rr = int(150 * punch)
    cv2.ellipse(canvas, (int(W / 2), int(H * 0.175)), (rr, rr), 0, -90,
                -90 + 360 * ease_out_cubic(np_),
                tuple((np.array(hot) * 0.85).tolist()), 3, cv2.LINE_AA)
    cv2.ellipse(canvas, (int(W / 2), int(H * 0.175)), (rr, rr), 0, 0, 360,
                tuple((np.array(hot) * 0.16).tolist()), 1, cv2.LINE_AA)

    who = layer(sec["who"], "fonts/Tajawal-ExtraBold.ttf", 86)
    wa = smoothstep(0.10, 0.42, p)
    who.stamp_centered(canvas, W / 2, H * 0.335, alpha=wa,
                       color=tuple(hot), glow=0.42, glow_color=deep, glow_sigma=28)
    en = layer(sec["who_en"], "fonts/BebasNeue-Regular.ttf", 42)
    en.stamp_centered(canvas, W / 2, H * 0.335 + 78, alpha=wa * 0.5,
                      color=tuple(mix_color(np.array(IVORY), np.array(hot), 0.4)))
    rule(canvas, W / 2, H * 0.335 + 132, 230, ease_out_cubic(smoothstep(0.22, 0.75, p)),
         alpha=0.7, color=hot, thickness=3)

    ar = ["", "النصيحة الأولى", "النصيحة الثانية", "النصيحة الثالثة", "النصيحة الرابعة",
          "النصيحة الخامسة", "النصيحة السادسة", "النصيحة السابعة"][idx]
    eye = layer(ar, "fonts/Tajawal-Bold.ttf", 40)
    eye.stamp_centered(canvas, W / 2, 152, alpha=wa * 0.45, color=tuple(hot))

    # ---------------- quote: two lines, word-synced reveal
    ys = np.arange(H, dtype=np.float32) / H
    band = np.exp(-(((ys - 0.515) / 0.085) ** 2))[:, None, None]
    canvas *= (1 - 0.26 * band)
    times = tip_words_times(sec)
    q1, q2 = sec["quote"], sec["quote2"]
    f1 = "fonts/Tajawal-ExtraBold.ttf"
    px1 = fit_px(q1, 980, 112, f1, 64)
    ha = np.array(hot, np.float32)
    c_hi = tuple(np.clip(ha * 0.85 + np.array([60, 48, 30], np.float32), 0, 255).tolist())
    c_lo = tuple(np.clip(ha * 0.62 + np.array([26, 18, 8], np.float32), 0, 255).tolist())
    l1 = Line(q1, f1, px1, grad=(c_hi, c_lo))
    qa = smoothstep(0.60, 1.0, p)
    l1.draw(canvas, W / 2, H * 0.475, alpha=qa, reveal=(p - 0.60) * 1.4,
            glow=0.34, glow_color=deep, glow_sigma=30)
    f2 = "fonts/Tajawal-Bold.ttf"
    px2 = fit_px(q2, 1000, 76, f2, 48)
    c2_hi = tuple(mix_color(np.array(IVORY), ha * 0.9, 0.35).tolist())
    c2_lo = tuple(mix_color(np.array(IVORY) * 0.82, ha * 0.75, 0.35).tolist())
    l2 = Line(q2, f2, px2, grad=(c2_hi, c2_lo))
    qa2 = smoothstep(1.30, 1.85, p)
    l2.draw(canvas, W / 2, H * 0.565, alpha=qa2 * 0.92, reveal=(p - 1.30) * 1.5, rise=22)

    # ---------------- per-tip ambient FX (visual variety between tips)
    if idx == 1:
        fx_rings(canvas, t, hot, cy=H * 0.30, count=4)
    elif idx == 2:
        pass  # grid already present
    elif idx == 3:
        pass  # blue tone + rain removed for clarity
    elif idx == 4:
        fx_dots(canvas, t, hot, step=110)
    elif idx == 6:
        fx_rings(canvas, t * 1.6, hot, cy=H * 0.30, count=6)
    # ---------------- big captions (muted viewers)
    caption_block(canvas, t, times, hot, y=0.775, px=74)
    # ---------------- bottom progress
    prog = (idx - 1 + np.clip(p / (sec["end"] - sec["start"]), 0, 1)) / 7
    bw = 340
    x0, y0 = int(W / 2 - bw / 2), int(H * 0.885)
    cv2.line(canvas, (x0, y0), (x0 + bw, y0), tuple((np.array(IVORY) * 0.14).tolist()), 3,
             cv2.LINE_AA)
    cv2.line(canvas, (x0, y0), (x0 + int(bw * prog), y0), tuple((np.array(hot) * 0.9).tolist()),
             3, cv2.LINE_AA)
    cv2.circle(canvas, (x0 + int(bw * prog), y0), 6, tuple((np.array(hot)).tolist()), -1,
               cv2.LINE_AA)
    lab = layer(f"{idx} / 7", "fonts/BebasNeue-Regular.ttf", 36)
    lab.stamp_centered(canvas, W / 2, y0 + 46, alpha=0.45,
                       color=tuple((np.array(hot) * 0.85).tolist()))
    top_bar(canvas, 0, alpha=0.0)

    # ---------------- FX
    if p < 0.14:
        radial_flash(canvas, W / 2, H * 0.305, 560, hot, (0.14 - p) / 0.14 * 0.42)
    if final:
        # the emotional spike: heartbeat pulses & slow darkening
        beat = 0.0
        for hb in (0.35, 1.45, 2.35, 3.22, 4.1, 5.0):
            if hb <= p < hb + 0.5:
                beat = math.exp(-(p - hb) * 9)
        canvas *= (1 - 0.16 * beat)
        canvas += np.exp(-((np.arange(H) - H * 0.5) / (H * 0.5)) ** 2)[:, None, None] * \
            np.array([14, 2, 0], np.float32) * (0.4 + 0.6 * beat)
        # red pulse ring
        if p < 3.4:
            r = 240 + (p * 260) % 460
            cv2.circle(canvas, (int(W / 2), int(H * 0.305)), int(r),
                       tuple((np.array([255, 70, 30]) * (0.18 * (1 - (r - 240) / 460))).tolist()),
                       2, cv2.LINE_AA)
    return canvas


def scene_outro(t, sec):
    p = t - sec["start"]
    canvas = backdrop(t, sec, hot=(255, 200, 120), intensity=0.62, seed=21, dark=(6, 5, 9))
    canvas += dust_layer(t, seed=9, alpha=0.5)
    hero(canvas, t, 0, alpha=0.22, scale=0.78, cy=0.215, color=(255, 198, 120))
    canvas += grid_layer(t, (255, 195, 120), alpha=0.18)
    ys = np.arange(H, dtype=np.float32) / H
    band = np.exp(-(((ys - 0.47) / 0.10) ** 2))[:, None, None]
    canvas *= (1 - 0.30 * band)

    a1 = smoothstep(0.10, 0.60, p)
    l1 = Line("احفظ هذه الجمل", "fonts/Tajawal-ExtraBold.ttf", 100)
    l1.draw(canvas, W / 2, H * 0.415, alpha=a1, reveal=(p - 0.10) * 1.6, glow=0.45,
            glow_color=(255, 170, 60), glow_sigma=32)
    a2 = smoothstep(1.25, 1.8, p)
    l2 = Line("شارك الفيديو مع شخص واحد", "fonts/Tajawal-Bold.ttf", 62)
    l2.draw(canvas, W / 2, H * 0.495, alpha=a2 * 0.95, reveal=(p - 1.25) * 1.5,
            color=tuple(IVORY))
    a3 = smoothstep(1.95, 2.5, p)
    l3 = Line("يحتاج أن يسمعها اليوم", "fonts/Tajawal-Bold.ttf", 62)
    l3.draw(canvas, W / 2, H * 0.55, alpha=a3 * 0.95, reveal=(p - 1.95) * 1.5,
            color=tuple(IVORY))

    # CTA button
    a4 = smoothstep(3.3, 4.0, p)
    if a4 > 0.01:
        box_a = a4
        bx, by = W / 2, H * 0.655
        bw, bh = 520, 126
        overlay = canvas.copy()
        cv2.rectangle(overlay, (int(bx - bw / 2), int(by - bh / 2)),
                      (int(bx + bw / 2), int(by + bh / 2)), (255, 255, 255), -1)
        canvas[:] = canvas * (1 - 0.12 * box_a) + overlay * (0.12 * box_a)
        cv2.rectangle(canvas, (int(bx - bw / 2), int(by - bh / 2)),
                      (int(bx + bw / 2), int(by + bh / 2)), tuple(GOLD.tolist()), 4,
                      cv2.LINE_AA)
        cta = layer("سأبدأ", "fonts/Tajawal-ExtraBold.ttf", 78)
        cta.stamp_centered(canvas, bx, by, alpha=box_a, color=tuple(GOLD_LIGHT),
                           glow=0.4, glow_color=(255, 170, 60), glow_sigma=22)
    a5 = smoothstep(4.6, 5.3, p)
    if a5 > 0.01:
        cm = layer("اكتب في التعليقات: سأبدأ", "fonts/Tajawal-Bold.ttf", 46)
        cm.stamp_centered(canvas, W / 2, H * 0.745, alpha=a5 * 0.72, color=tuple(IVORY))
    # follow cue
    a6 = smoothstep(5.6, 6.2, p)
    if a6 > 0.01:
        fu = layer("تابع للجزء الثاني", "fonts/Tajawal-Bold.ttf", 42)
        fu.stamp_centered(canvas, W / 2, H * 0.815, alpha=a6 * 0.55, color=tuple(GOLD))
    return canvas


# ------------------------------------------------------------------ compositing
def apply_grade(canvas):
    canvas = gfx.bloom(np.clip(canvas, 0, 255), threshold=140, strength=0.92, sigma=16)
    canvas = np.clip(canvas, 0, 255) / 255.0
    canvas = canvas * 0.98 / (1 + canvas * 0.16)
    canvas *= BOUNCE
    canvas *= VIG
    canvas = np.clip(canvas, 0, 1) ** (1 / 1.04)
    return canvas


def render_frame(fi, total_frames, sec_of_frame):
    t = fi / FPS
    sec = sec_of_frame(fi)
    if sec["kind"] == "hook":
        canvas = scene_hook(t, sec)
        p = t - sec["start"]
        if p > 9.7:
            z = 1 + 0.07 * smoothstep(9.7, 10.3, p)
            canvas = cv2.resize(canvas, None, fx=z, fy=z, interpolation=cv2.INTER_LINEAR)
            y0 = (canvas.shape[0] - H) // 2
            x0 = (canvas.shape[1] - W) // 2
            canvas = canvas[y0:y0 + H, x0:x0 + W]
        if p < 0.2:
            canvas += (0.2 - p) / 0.2 * 26
    elif sec["kind"] == "tip":
        canvas = scene_tip(t, sec)
        p = t - sec["start"]
        if p < 0.26:
            rng = np.random.default_rng(int(t * 1000))
            canvas = gfx.glitch_slice(canvas, t, rng, amount=(0.26 - p) / 0.26 * 0.85)
            canvas = np.roll(canvas, int(16 * (0.26 - p) / 0.26), axis=1)
    else:
        canvas = scene_outro(t, sec)

    p_sec = t - sec["start"]
    if p_sec < 0.20:
        canvas = chroma_shift(canvas, 8 * (0.20 - p_sec) / 0.20)

    # slow push-in across each tip for constant motion
    if sec["kind"] in ("tip", "outro"):
        span = max(1.0, sec["end"] - sec["start"])
        z = 1.0 + 0.045 * min(1.0, (t - sec["start"]) / span)
        if z > 1.001:
            canvas = cv2.resize(canvas, None, fx=z, fy=z, interpolation=cv2.INTER_LINEAR)
            y0 = (canvas.shape[0] - H) // 2
            x0 = (canvas.shape[1] - W) // 2
            canvas = canvas[y0:y0 + H, x0:x0 + W]
    canvas = apply_grade(canvas)
    rng = np.random.default_rng(11 + fi % 7)
    add_grain(canvas, rng, 0.010)
    if t < 0.32:
        canvas *= smoothstep(0.0, 0.32, t)
    if t > TOTAL - 1.0:
        canvas *= 1 - smoothstep(TOTAL - 1.0, TOTAL - 0.04, t)
    return (np.clip(canvas, 0, 1) * 255).astype(np.uint8)


# ------------------------------------------------------------------ encode
def _work(fi):
    return render_frame(fi, _N_FRAMES, _resolve_sec).tobytes()


def _resolve_sec(fi):
    t = fi / FPS
    bounds = _BOUNDS
    for i, (a, b, s) in enumerate(bounds):
        if t < b or i == len(bounds) - 1:
            return s
    return bounds[-1][2]


_N_FRAMES = 0
_BOUNDS = []


def main(out_path="mjod_video.mp4", workers=2, start=None, end=None,
         audio="build/mix.wav", crf=18):
    from multiprocessing import Pool
    global _N_FRAMES, _BOUNDS
    n_frames = int(TOTAL * FPS)
    _N_FRAMES = n_frames
    _BOUNDS = [(s["start"], s["end"], s) for s in SECTIONS]

    lo = int(start * FPS) if start is not None else 0
    hi = int(end * FPS) if end is not None else n_frames

    cmd = ["ffmpeg", "-y", "-loglevel", "error",
           "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
           "-thread_queue_size", "512", "-i", "-"]
    if audio and os.path.exists(audio):
        cmd += ["-i", audio]
    cmd += ["-c:v", "libx264", "-preset", "slow", "-crf", str(crf),
            "-pix_fmt", "yuv420p", "-profile:v", "high", "-level", "4.2",
            "-x264-params", "keyint=48:min-keyint=24:ref=4:aq-mode=3",
            "-color_primaries", "bt709", "-color_trc", "bt709", "-colorspace", "bt709"]
    if audio and os.path.exists(audio):
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
    out = sys.argv[3] if len(sys.argv) > 3 else "mjod_video.mp4"
    main(out_path=out, start=float(a) if a else None, end=float(b) if b else None)
