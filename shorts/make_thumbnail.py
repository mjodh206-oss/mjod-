#!/usr/bin/env python3
"""1280x720 YouTube thumbnail."""
import arabic_reshaper
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance

W, H = 1280, 720
ASSETS = '/home/user/mjod-/shorts/assets'
F700 = f'{ASSETS}/fonts/Kufi-Merged-700.ttf'
F400 = f'{ASSETS}/fonts/Kufi-Merged-400.ttf'
ACCENT = (34, 211, 238, 255)
PURPLE = (139, 92, 246, 255)
WHITE = (255, 255, 255, 255)
MUTED = (205, 208, 228, 255)


def font(p, s):
    return ImageFont.truetype(p, s)


def shape(t):
    return get_display(arabic_reshaper.reshape(t))


def cover_crop(img, w, h):
    iw, ih = img.size
    s = max(w / iw, h / ih)
    nw, nh = int(iw * s + 0.5), int(ih * s + 0.5)
    img = img.resize((nw, nh), Image.LANCZOS)
    x, y = (nw - w) // 2, (nh - h) // 2
    return img.crop((x, y, x + w, y + h))


im = Image.open(f'{ASSETS}/bg_hook.png').convert('RGBA')
im = cover_crop(im, W, H)
im = ImageEnhance.Brightness(im).enhance(0.55)
ov = Image.new('L', (W, H), 0)
d = ImageDraw.Draw(ov)
for y in range(H):
    a = int(170 * abs(y / H - 0.45) / 0.55)
    d.line([(0, y), (W, y)], fill=a)
im = Image.composite(Image.new('RGBA', (W, H), (6, 4, 14, 255)), im, ov)


def glow(t, f, x, y, color, blur=40):
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(layer).text((x, y), shape(t), font=f, fill=color, anchor='mm')
    im.alpha_composite(layer.filter(ImageFilter.GaussianBlur(blur)))


d = ImageDraw.Draw(im)
chip = "دليل سريع للذكاء الاصطناعي"
f = font(F700, 38)
cw = d.textlength(shape(chip), font=f) + 80
d.rounded_rectangle([(W - cw) // 2, 66, (W + cw) // 2, 126], radius=30,
                    fill=(255, 255, 255, 26))
d.text((W // 2, 96), shape(chip), font=f, fill=WHITE, anchor='mm')

glow("7", font(F700, 290), W // 2, 330, ACCENT, blur=50)
d.text((W // 2, 330), shape("7"), font=font(F700, 290), fill=WHITE, anchor='mm')
d.text((W // 2, 565), shape("نصائح"), font=font(F700, 118), fill=WHITE, anchor='mm')
d.text((W // 2, 668), shape("Dify + Stable Diffusion"), font=font(F700, 46),
       fill=ACCENT, anchor='mm')
im.convert('RGB').save('/home/user/mjod-/shorts/thumbnail_1280x720.png')
print('thumbnail saved')
