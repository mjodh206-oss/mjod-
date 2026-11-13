#!/usr/bin/env python3
"""يولّد أيقونات QnS (Adaptive Icon + Monochrome + Legacy) وأيقونة الويب."""
import os, io, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops
from fontTools.ttLib import TTFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'android/app/src/main/res')
S = 1728  # master size (108dp * 16)

def font(size):
    f = TTFont(os.path.join(ROOT, 'www/fonts/playfair-display-latin-700-normal.woff2'))
    f.flavor = None
    b = io.BytesIO(); f.save(b); b.seek(0)
    return ImageFont.truetype(b, size)

def vgrad(size, stops):
    w, h = size
    g = Image.new('RGB', (1, h))
    px = g.load()
    for y in range(h):
        t = y / (h - 1)
        for i in range(len(stops) - 1):
            a, b = stops[i], stops[i + 1]
            if a[0] <= t <= b[0]:
                k = (t - a[0]) / (b[0] - a[0] or 1)
                px[0, y] = tuple(int(a[1][c] + (b[1][c] - a[1][c]) * k) for c in range(3))
                break
    return g.resize((w, h))

def background():
    # تدرج كحلي شعاعي
    img = Image.new('RGB', (S, S))
    small = Image.new('RGB', (216, 216)); px = small.load()
    cx, cy = 108, 80
    for y in range(216):
        for x in range(216):
            d = min(1, math.hypot(x - cx, y - cy) / 150)
            c0, c1 = (30, 52, 112), (5, 10, 28)
            px[x, y] = tuple(int(c0[i] + (c1[i] - c0[i]) * d ** 0.9) for i in range(3))
    img = small.resize((S, S), Image.BICUBIC).convert('RGBA')
    # انعكاس ضوئي قطري
    band = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(band)
    d.polygon([(S*0.08, 0), (S*0.50, 0), (S*0.10, S), (-S*0.32, S)], fill=46)
    d.polygon([(S*0.58, 0), (S*0.66, 0), (S*0.26, S), (S*0.18, S)], fill=26)
    band = band.filter(ImageFilter.GaussianBlur(S * 0.02))
    # الانعكاس يخفت نحو الأسفل
    fade = vgrad((S, S), [(0, (255,)*3), (0.75, (40,)*3), (1, (0,)*3)]).convert('L')
    band = ImageChops.multiply(band, fade)
    img.paste(Image.new('RGBA', (S, S), (255, 255, 255, 255)), (0, 0), band)
    # توهج ذهبي خافت بالأسفل
    glow = Image.new('L', (S, S), 0)
    ImageDraw.Draw(glow).ellipse([S*0.2, S*0.72, S*0.8, S*1.1], fill=60)
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.09))
    img.paste(Image.new('RGBA', (S, S), (212, 175, 55, 255)), (0, 0), glow)
    return img

def text_mask():
    """قناع الشعار: حروف QnS + خط زخرفي، ضمن المنطقة الآمنة (66dp)."""
    m = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(m)
    f = font(int(S * 0.275))
    txt = 'QnS'
    l, t, r, b = d.textbbox((0, 0), txt, font=f)
    w, h = r - l, b - t
    x = (S - w) / 2 - l
    y = S * 0.475 - h / 2 - t
    d.text((x, y), txt, font=f, fill=255)
    # خط زخرفي مع معيّن
    ly = S * 0.655; lw = S * 0.17; th = S * 0.0065
    d.rounded_rectangle([S/2 - lw, ly - th, S/2 - S*0.035, ly + th], radius=th, fill=255)
    d.rounded_rectangle([S/2 + S*0.035, ly - th, S/2 + lw, ly + th], radius=th, fill=255)
    q = S * 0.02
    d.polygon([(S/2, ly - q), (S/2 + q, ly), (S/2, ly + q), (S/2 - q, ly)], fill=255)
    return m

def foreground(mask):
    gold = vgrad((S, S), [(0, (255, 244, 190)), (0.36, (255, 240, 180)), (0.46, (236, 200, 96)),
                          (0.56, (201, 160, 48)), (0.66, (150, 112, 28)), (1, (150, 112, 28))]).convert('RGBA')
    # لمعة قطرية على الذهب
    sheen = Image.new('L', (S, S), 0)
    ImageDraw.Draw(sheen).polygon([(S*0.50, S*0.3), (S*0.56, S*0.3), (S*0.44, S*0.7), (S*0.38, S*0.7)], fill=150)
    sheen = sheen.filter(ImageFilter.GaussianBlur(S * 0.012))
    gold.paste(Image.new('RGBA', (S, S), (255, 255, 240, 255)), (0, 0), sheen)
    fg = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    # ظل
    sh = ImageChops.offset(mask, 0, int(S * 0.008)).filter(ImageFilter.GaussianBlur(S * 0.008))
    fg.paste(Image.new('RGBA', (S, S), (0, 0, 0, 255)), (0, 0), sh.point(lambda v: int(v * 0.6)))
    fg.paste(gold, (0, 0), mask)
    return fg

def save(img, path, size):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.resize((size, size), Image.LANCZOS).save(path, optimize=True)

def main():
    bg = background(); mask = text_mask(); fg = foreground(mask)
    mono = Image.new('RGBA', (S, S), (255, 255, 255, 0)); mono.putalpha(mask)
    full = Image.alpha_composite(bg, fg)
    # القديم: قص المنطقة المرئية (72dp من 108dp)
    c0 = int(S * 18 / 108); c1 = S - c0
    crop = full.crop((c0, c0, c1, c1)); cs = crop.size[0]
    rr = Image.new('L', (cs, cs), 0); ImageDraw.Draw(rr).rounded_rectangle([0, 0, cs, cs], radius=cs * 0.22, fill=255)
    circ = Image.new('L', (cs, cs), 0); ImageDraw.Draw(circ).ellipse([0, 0, cs, cs], fill=255)
    sq = crop.copy(); sq.putalpha(rr)
    rd = crop.copy(); rd.putalpha(circ)
    stat = mono.crop((int(S*0.2), int(S*0.2), int(S*0.8), int(S*0.8)))
    for name, k in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
        d = f'{RES}/mipmap-{name}'
        save(bg.convert('RGB'), f'{d}/ic_launcher_background.png', int(108 * k))
        save(fg, f'{d}/ic_launcher_foreground.png', int(108 * k))
        save(mono, f'{d}/ic_launcher_monochrome.png', int(108 * k))
        save(sq, f'{d}/ic_launcher.png', int(48 * k))
        save(rd, f'{d}/ic_launcher_round.png', int(48 * k))
        save(stat, f'{RES}/drawable-{name}/ic_stat_qns.png', int(24 * k))
    save(fg, f'{RES}/drawable-xxhdpi/splash_logo.png', 432)
    save(sq, f'{ROOT}/www/icon-512.png', 512)
    save(sq, f'{ROOT}/www/icon-192.png', 192)
    save(full, f'{ROOT}/tools/icon-preview.png', 512)

main()
