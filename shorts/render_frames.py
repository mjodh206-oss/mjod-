#!/usr/bin/env python3
"""Render 9 vertical 1080x1920 frames for the YouTube Short."""
import os
import arabic_reshaper
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance
from fontTools.ttLib import TTFont

W, H = 1080, 1920
ASSETS = '/home/user/mjod-/shorts/assets'
FRAMES = '/home/user/mjod-/shorts/frames'
os.makedirs(FRAMES, exist_ok=True)

F700 = os.path.join(ASSETS, 'fonts', 'Kufi-Merged-700.ttf')
F400 = os.path.join(ASSETS, 'fonts', 'Kufi-Merged-400.ttf')
CMAP = TTFont(F700).getBestCmap()

ACCENT = (34, 211, 238, 255)      # cyan
PURPLE = (139, 92, 246, 255)      # violet
WHITE = (255, 255, 255, 255)
MUTED = (205, 208, 228, 255)
DIM = (160, 165, 190, 255)
MISSING = set()


def font(path, size):
    return ImageFont.truetype(path, size)


def shape(t):
    return get_display(arabic_reshaper.reshape(t))


def check(t):
    for c in t:
        if c != ' ' and ord(c) not in CMAP:
            MISSING.add(c)


def cover_crop(img, w, h):
    iw, ih = img.size
    s = max(w / iw, h / ih)
    nw, nh = int(iw * s + 0.5), int(ih * s + 0.5)
    img = img.resize((nw, nh), Image.LANCZOS)
    x, y = (nw - w) // 2, (nh - h) // 2
    return img.crop((x, y, x + w, y + h))


def bg(path, darken=0.45):
    im = Image.open(path).convert('RGBA')
    im = cover_crop(im, W, H)
    if darken:
        im = ImageEnhance.Brightness(im).enhance(1 - darken)
    ov = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(ov)
    for y in range(H):
        t = y / H
        a = int(150 * abs(t - 0.42) / 0.58)
        d.line([(0, y), (W, y)], fill=a)
    black = Image.new('RGBA', (W, H), (6, 4, 14, 255))
    im = Image.composite(black, im, ov)
    return im


def measure(draw, t, f):
    return draw.textlength(shape(t), font=f)


def wrap(draw, t, f, maxw):
    words = t.split(' ')
    lines, cur = [], ''
    for w in words:
        cand = w if not cur else cur + ' ' + w
        if measure(draw, cand, f) <= maxw:
            cur = cand
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def draw_par(draw, t, f, y_top, line_h, fill, maxw=W - 180, x=W // 2):
    check(t)
    cy = y_top + line_h / 2
    for ln in wrap(draw, t, f, maxw):
        draw.text((x, cy), shape(ln), font=f, fill=fill, anchor='mm')
        cy += line_h
    return cy - line_h / 2


def chip(draw, t, f, y, bg_fill=(255, 255, 255, 26), fg=WHITE, pad_x=46, pad_y=20):
    check(t)
    w = draw.textlength(shape(t), font=f) + pad_x * 2
    h = f.size + pad_y * 2
    x0, y0 = (W - w) // 2, y - h // 2
    draw.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=h // 2, fill=bg_fill)
    draw.text((W // 2, y), shape(t), font=f, fill=fg, anchor='mm')


def glow(base, t, f, x, y, color, blur=42):
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.text((x, y), shape(t), font=f, fill=color, anchor='mm')
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    base.alpha_composite(layer)


def progress(draw, idx, total=7, y=1690):
    seg_w, gap = 120, 12
    total_w = total * seg_w + (total - 1) * gap
    x = (W - total_w) // 2
    for i in range(total):
        fill = ACCENT if i < idx else (255, 255, 255, 55)
        x0 = x + i * (seg_w + gap)
        draw.rounded_rectangle([x0, y, x0 + seg_w, y + 12], radius=6, fill=fill)


TIPS = {
    1: "ابدأ مع Dify — ابنِ تطبيق كامل بدون كود من قالب جاهز",
    2: "وصّل Dify مع Stable Diffusion — ولّد صور من داخل تطبيقك",
    3: "دايمًا استخدم Negative\u00a0Prompt — نتائج أنظف من أول مرة",
    4: "استخدم ControlNet — تحكّم كامل بالوضعية والإضاءة",
    5: "جرّب موديلات LoRA\u00a0وCheckpoint — كل ستايل بيختلف",
    6: "شغّل Batch وثبّت Seed — وفّر وقتك وقارن بسرعة",
    7: "انشر شغلك على Dify — الذكاء الاصطناعي يسوّق لك",
}
TOOL = {
    1: "DIFY", 2: "DIFY + STABLE DIFFUSION", 3: "STABLE DIFFUSION",
    4: "STABLE DIFFUSION", 5: "STABLE DIFFUSION", 6: "STABLE DIFFUSION",
    7: "DIFY",
}

# ---------- Hook ----------
im = bg(os.path.join(ASSETS, 'bg_hook.png'), darken=0.42)
draw_par(ImageDraw.Draw(im), "لسه بتستخدم الذكاء الاصطناعي بشكل عادي؟",
         font(F400, 62), 250, 92, MUTED)
glow(im, "7", font(F700, 420), W // 2, 800, ACCENT, blur=48)
d = ImageDraw.Draw(im)
d.text((W // 2, 800), shape("7"), font=font(F700, 420), fill=WHITE, anchor='mm')
draw_par(d, "نصائح", font(F700, 148), 1050, 190, WHITE)
draw_par(d, "توفّر لك ساعات كل يوم", font(F400, 64), 1290, 96, MUTED)
d.text((W // 2, 1465), shape("Dify + Stable Diffusion"), font=font(F700, 58),
       fill=ACCENT, anchor='mm')
chip(d, "النصيحة رقم 7 مفاجأة… كمّل للآخر", font(F700, 50), 1620,
     bg_fill=PURPLE)
im.convert('RGB').save(f'{FRAMES}/scene_01.png')

# ---------- Tips ----------
for i in range(1, 8):
    im = bg(os.path.join(ASSETS, 'bg_tips.png'), darken=0.5)
    d = ImageDraw.Draw(im)
    chip(d, f"النصيحة {i} من 7", font(F700, 44), 140,
         bg_fill=(255, 255, 255, 22))
    cx, cy, r = W // 2, 620, 160
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=PURPLE)
    d.ellipse([cx - r + 16, cy - r + 16, cx + r - 16, cy + r - 16],
              fill=(22, 13, 45, 255))
    d.text((cx, cy + 6), str(i), font=font(F700, 195), fill=WHITE, anchor='mm')
    draw_par(d, TIPS[i], font(F700, 58), 960, 94, WHITE)
    chip(d, TOOL[i], font(F700, 44), 1590, bg_fill=(255, 255, 255, 24))
    progress(d, i)
    d.text((W // 2, 1775), shape("Dify + Stable Diffusion"), font=font(F400, 32),
           fill=DIM, anchor='mm')
    im.convert('RGB').save(f'{FRAMES}/scene_{i+1:02d}.png')

# ---------- CTA ----------
im = bg(os.path.join(ASSETS, 'bg_cta.png'), darken=0.42)
d = ImageDraw.Draw(im)
chip(d, "جاهز تجرّب؟", font(F700, 46), 185, bg_fill=(255, 255, 255, 22))
d.text((W // 2, 470), shape("احفظ  •  شارك  •  تابعني"), font=font(F700, 74),
       fill=WHITE, anchor='mm')
draw_par(d, "جرّب الأداتين وقولي تجربتك بالتعليقات", font(F400, 60), 640, 94, MUTED)
d.text((W // 2, 950), shape("Dify + Stable Diffusion"), font=font(F700, 66),
       fill=ACCENT, anchor='mm')
chip(d, "الذكاء الاصطناعي يسوّق لك", font(F700, 48), 1180, bg_fill=PURPLE)
im.convert('RGB').save(f'{FRAMES}/scene_09.png')

print('frames rendered:', sorted(os.listdir(FRAMES)))
print('missing glyphs:', ''.join(sorted(MISSING)) or 'none')
