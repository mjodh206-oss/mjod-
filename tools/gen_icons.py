#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
مولّد أيقونات تطبيق «مَحلولة»
يرسم الشعار (عملة ذهبية + حرف ميم + علامة صح) على خلفية زمردية متدرجة
ويُخرج كل المقاسات المطلوبة لأندرويد (legacy + adaptive + splash).
"""
import math
import os
import sys
from PIL import Image, ImageDraw, ImageFilter

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
RES = os.path.normpath(RES)

BRAND_DARK = (4, 34, 31)
BRAND_MID = (7, 59, 54)
BRAND = (10, 79, 71)
BRAND_LIGHT = (18, 133, 119)
TEAL = (79, 198, 176)
GOLD = (232, 184, 75)
GOLD_D = (212, 160, 23)
INK = (7, 59, 54)
WHITE = (255, 255, 255)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rounded_rect_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def paint_gradient(size, c_top_left, c_bottom_right, mask=None, alpha=255, h=None):
    """تدرّج قطري ناعم عبر bilinear interpolation."""
    h = h or size
    img = Image.new("RGBA", (size, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(size):
            t = (x / max(1, size - 1) + y / max(1, h - 1)) / 2.0
            c = lerp(c_top_left, c_bottom_right, t)
            px[x, y] = (c[0], c[1], c[2], alpha)
    if mask is not None:
        img.putalpha(mask)
    return img


def cubic(p0, p1, p2, p3, steps=64):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0]
        y = u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


FONT_PATH = os.path.join(os.path.dirname(RES), "assets", "fonts", "plex_arabic_Bold.ttf")
_font_cache = {}


def _font(px):
    if px not in _font_cache:
        from PIL import ImageFont
        _font_cache[px] = ImageFont.truetype(FONT_PATH, px)
    return _font_cache[px]


def draw_meem(img, cx, cy, s, color):
    """يرسم حرف (م) الحقيقي من خط IBM Plex Sans Arabic Bold."""
    px = int(s * 1.9)
    f = _font(px)
    bbox = f.getbbox("م")
    gw, gh = bbox[2] - bbox[0], bbox[3] - bbox[1]
    layer = Image.new("RGBA", (int(gw) + 8, int(gh) + 8), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.text((4 - bbox[0], 4 - bbox[1]), "م", font=f, fill=color + (255,))
    layer = layer.resize((int(layer.width * s / max(gw, gh) * 1.0),
                          int(layer.height * s / max(gw, gh) * 1.0)), Image.LANCZOS)
    img.alpha_composite(layer, (int(cx - layer.width / 2), int(cy - layer.height / 2)))


def draw_badge(img, cx, cy, r):
    """شارة توثيق زمرّدية بعلامة صح بيضاء."""
    layer = Image.new("RGBA", (img.width, img.height), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)
    ri = r * 0.83
    d.ellipse([cx - ri, cy - ri, cx + ri, cy + ri], fill=(14, 107, 94))
    w = max(2, int(r * 0.30))
    d.line([(cx - r * 0.42, cy + 0.02 * r), (cx - r * 0.10, cy + 0.34 * r),
            (cx + r * 0.46, cy - 0.28 * r)], fill=WHITE, width=w, joint="curve")
    img.alpha_composite(layer)


def logo_mark(size, ring=True, glow=False):
    """شعار دائري بخلفية شفافة."""
    S = size * 4  # super-sampling
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = S / 2
    R = S * 0.47
    if ring:
        d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=TEAL)
        d.ellipse([cx - R * 0.90, cy - R * 0.90, cx + R * 0.90, cy + R * 0.90], fill=BRAND_MID)
    coin_r = R * (0.78 if ring else 0.92)
    d.ellipse([cx - coin_r, cy - coin_r, cx + coin_r, cy + coin_r], fill=GOLD)
    inner = coin_r * 0.86
    d.ellipse([cx - inner, cy - inner, cx + inner, cy + inner], fill=GOLD_D)
    # لمعة علوية
    shine = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shine)
    sd.pieslice([cx - inner, cy - inner, cx + inner, cy + inner], 200, 340, fill=(255, 255, 255, 46))
    img = Image.alpha_composite(img, shine)
    draw_meem(img, cx, cy + S * 0.015, S * 0.46, INK)
    draw_badge(img, cx + S * 0.30, cy + S * 0.30, S * 0.155)
    img = img.resize((size, size), Image.LANCZOS)
    if glow:
        g = Image.new("RGBA", (size * 2, size * 2), (0, 0, 0, 0))
        g.paste(img, (size // 2, size // 2), img)
        halo = Image.new("RGBA", (size * 2, size * 2), (0, 0, 0, 0))
        hd = ImageDraw.Draw(halo)
        hd.ellipse([size * 0.45, size * 0.45, size * 1.55, size * 1.55], fill=(79, 198, 176, 70))
        halo = halo.filter(ImageFilter.GaussianBlur(size * 0.09))
        out = Image.alpha_composite(halo, g)
        img = out.crop((size // 2, size // 2, size // 2 + size, size // 2 + size))
    return img


def legacy_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg = paint_gradient(size, BRAND, BRAND_DARK, mask=rounded_rect_mask(size, int(size * 0.225)))
    img = Image.alpha_composite(img, bg)
    d = ImageDraw.Draw(img)
    # موجة سفلية خفيفة
    wave = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    wd = ImageDraw.Draw(wave)
    pts = [(0, size)] + cubic((0, size * 0.80), (size * 0.35, size * 0.66),
                               (size * 0.62, size * 0.92), (size, size * 0.70), 80) + [(size, size)]
    wd.polygon(pts, fill=(18, 133, 119, 70))
    img = Image.alpha_composite(img, wave)
    mark = logo_mark(int(size * 0.78), ring=False)
    img.alpha_composite(mark, ((size - mark.width) // 2, (size - mark.height) // 2 - int(size * 0.01)))
    return img


def adaptive_fg(size):
    """طبقة أمامية 108dp: الشعار داخل المنطقة الآمنة (66dp)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mark = logo_mark(int(size * 66 / 108 * 1.04), ring=False)
    img.alpha_composite(mark, ((size - mark.width) // 2, (size - mark.height) // 2))
    return img


def adaptive_bg(size):
    img = paint_gradient(size, BRAND, BRAND_DARK)
    d = ImageDraw.Draw(img)
    wave = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    wd = ImageDraw.Draw(wave)
    pts = [(0, size)] + cubic((0, size * 0.86), (size * 0.34, size * 0.70),
                              (size * 0.64, size * 0.98), (size, size * 0.76), 90) + [(size, size)]
    wd.polygon(pts, fill=(18, 133, 119, 90))
    pts2 = [(0, size)] + cubic((0, size * 0.94), (size * 0.40, size * 0.84),
                               (size * 0.70, size * 1.02), (size, size * 0.90), 90) + [(size, size)]
    wd.polygon(pts2, fill=(79, 198, 176, 55))
    img = Image.alpha_composite(img, wave)
    # نقاط ذهبية خفيفة
    for (fx, fy, fr, al) in [(0.16, 0.22, 0.020, 40), (0.83, 0.26, 0.014, 34),
                             (0.24, 0.78, 0.012, 30), (0.78, 0.72, 0.018, 26),
                             (0.52, 0.12, 0.010, 30)]:
        d.ellipse([size * fx - size * fr, size * fy - size * fr,
                   size * fx + size * fr, size * fy + size * fr], fill=(232, 184, 75, al))
    return img


def round_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg = paint_gradient(size, BRAND, BRAND_DARK, mask=rounded_rect_mask(size, size // 2))
    img = Image.alpha_composite(img, bg)
    mark = logo_mark(int(size * 0.74), ring=False)
    img.alpha_composite(mark, ((size - mark.width) // 2, (size - mark.height) // 2))
    return img


def save(img, *rel):
    path = os.path.join(RES, *rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", optimize=True)
    print("  ✓", os.path.relpath(path, RES))


def main():
    print("توليد أيقونات مَحلولة …")
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for d, s in densities.items():
        save(legacy_icon(s), f"mipmap-{d}", "ic_launcher.png")
        save(round_icon(s), f"mipmap-{d}", "ic_launcher_round.png")
    # Adaptive (play store + anydpi)
    save(logo_mark(512, ring=False), "mipmap-xxxhdpi", "logo_mark.png")
    save(logo_mark(1024, ring=False, glow=True), "..", "brand", "logo_1024.png")
    save(legacy_icon(512), "..", "brand", "icon_512.png")
    # خلفية splash عالية الدقة
    sp = Image.new("RGBA", (1080, 1920), (0, 0, 0, 0))
    sp = Image.alpha_composite(sp, paint_gradient(1080, BRAND_MID, BRAND_DARK, h=1920))
    d = ImageDraw.Draw(sp)
    for (fx, fy, fr, al) in [(0.12, 0.18, 0.05, 16), (0.88, 0.30, 0.07, 14),
                             (0.20, 0.82, 0.06, 14), (0.85, 0.75, 0.045, 16)]:
        d.ellipse([1080 * fx - 1080 * fr, 1920 * fy - 1080 * fr,
                   1080 * fx + 1080 * fr, 1920 * fy + 1080 * fr], fill=(79, 198, 176, al))
    mark = logo_mark(430, ring=False, glow=True)
    sp.alpha_composite(mark, ((1080 - 430) // 2, 700))
    save(sp.convert("RGB"), "..", "brand", "splash_preview.jpg")
    print("تم ✅")


if __name__ == "__main__":
    main()
