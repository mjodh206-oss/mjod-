#!/usr/bin/env python3
"""Render the Mary Celeste episode with animated shots (moving ship, waves, map, rain and papers).

Requires Pillow, NumPy, arabic-reshaper, python-bidi, and imageio-ffmpeg.
The ship and ocean plates are original generated art; this script animates/composites them.
"""
from __future__ import annotations
import argparse
import math
import re
import subprocess
from pathlib import Path

import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont, ImageOps
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = Path(__file__).resolve().parents[1]
W, H, FPS = 960, 540, 20
FONT_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_R = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def rtl(text: str) -> str:
    return get_display(arabic_reshaper.reshape(text))


def fit(path: Path, size=(W, H)) -> Image.Image:
    return ImageOps.fit(Image.open(path).convert("RGB"), size, method=Image.Resampling.LANCZOS)


def load_captions(path: Path):
    if not path.exists():
        return []
    cues = []
    for block in path.read_text(encoding="utf-8").strip().split("\n\n"):
        lines = block.splitlines()
        if len(lines) < 3 or "-->" not in lines[1]:
            continue
        def seconds(value):
            hms, ms = value.strip().split(",")
            h, m, s = map(int, hms.split(":"))
            return h * 3600 + m * 60 + s + int(ms) / 1000
        a, b = lines[1].split("-->")
        cues.append((seconds(a), seconds(b), "\n".join(lines[2:])))
    return cues


def draw_brand_and_caption(frame: Image.Image, label: str, t: float, captions):
    frame = frame.convert("RGBA")
    d = ImageDraw.Draw(frame, "RGBA")
    ivory = (247, 241, 226, 245)
    gold = (224, 184, 105, 245)
    # Small persistent channel bug and honest illustration label.
    d.rounded_rectangle((W - 207, 15, W - 14, 53), radius=15,
                        fill=(7, 15, 27, 205), outline=(204, 164, 93, 190), width=1)
    d.text((W - 110, 34), rtl("وراء الحكاية"), font=ImageFont.truetype(FONT_B, 17),
           anchor="mm", fill=ivory)
    d.rounded_rectangle((16, 15, 163, 47), radius=12,
                        fill=(7, 15, 27, 190), outline=(204, 164, 93, 125), width=1)
    d.text((89, 31), rtl("تصوير توضيحي"), font=ImageFont.truetype(FONT_R, 14),
           anchor="mm", fill=(241, 220, 177, 245))
    # Compact chapter tag below the disclosure; keep the waterline clear.
    d.rounded_rectangle((18, 56, 365, 91), radius=12,
                        fill=(7, 15, 27, 200), outline=(204, 164, 93, 130), width=1)
    d.text((191, 73), rtl(label), font=ImageFont.truetype(FONT_B, 17),
           anchor="mm", fill=ivory)
    # Burn in the approved Arabic captions so viewers do not need a separate upload.
    caption = next((text for a, b, text in captions if a <= t < b), "")
    if caption:
        lines = caption.splitlines()
        box_h = 58 if len(lines) > 1 else 38
        d.rounded_rectangle((76, H - 116 - box_h, W - 76, H - 105), radius=13,
                            fill=(3, 8, 16, 218))
        font = ImageFont.truetype(FONT_B, 19)
        ys = [H - 137 - (len(lines) - 1) * 22 + i * 22 for i in range(len(lines))]
        for line, y in zip(lines, ys):
            d.text((W // 2, y), rtl(line), font=font, anchor="mm", fill=ivory,
                   stroke_width=1, stroke_fill=(0, 0, 0, 180))
    return frame.convert("RGB")


def key_ship(path: Path) -> Image.Image:
    im = Image.open(path).convert("RGB")
    a = np.asarray(im).astype(np.float32)
    r, g, b = a[:, :, 0], a[:, :, 1], a[:, :, 2]
    dominance = g - np.maximum(r, b)
    # Green-screen matte with a soft anti-aliased edge.
    alpha = np.clip((55.0 - dominance) / 35.0, 0.0, 1.0)
    bg = np.array([64.0, 185.0, 50.0], dtype=np.float32)
    af = alpha[:, :, None]
    fg = np.where(af > 0.05, (a - (1.0 - af) * bg) / np.maximum(af, 0.05), 0.0)
    rgba = np.dstack((np.clip(fg, 0, 255), alpha * 255)).astype(np.uint8)
    cut = Image.fromarray(rgba, "RGBA")
    bbox = cut.getbbox()
    if bbox:
        cut = cut.crop(bbox)
    return cut


class MotionRenderer:
    def __init__(self):
        self.ocean_path = ROOT / "brand/motion/atlantic-night-sea.png"
        self.ship_path = ROOT / "brand/motion/brigantine-key.png"
        self.deck_path = ROOT / "brand/episode-01-visuals/empty-deck.png"
        self.docs_path = ROOT / "brand/episode-01-visuals/inquiry-documents.png"
        self.map_path = ROOT / "brand/episode-01-visuals/atlantic-chart.png"
        self.audio_path = ROOT / "audio/episode-01/episode-01-full-voiceover.mp3"
        self.captions = load_captions(ROOT / "deliverables/episode-01-subtitles-ar.srt")
        self.ocean = np.asarray(fit(self.ocean_path)).copy()
        self.ship = key_ship(self.ship_path)
        self.deck = fit(self.deck_path)
        self.docs = fit(self.docs_path)
        self.chart = fit(self.map_path)
        # Render ship at a consistent hero scale; leave enough water in the foreground.
        target_w = 705
        target_h = int(self.ship.height * target_w / self.ship.width)
        self.ship = self.ship.resize((target_w, target_h), Image.Resampling.LANCZOS)
        self.font_title = ImageFont.truetype(FONT_B, 25)

    def animated_ocean(self, t: float, direction=1.0):
        base = self.ocean
        warped = base.copy()
        x = np.arange(W)
        for y in range(255, H):
            dx = int(9 * math.sin(y * 0.045 + t * 1.9) +
                     4 * math.sin(y * 0.095 - t * 1.15) + direction * t * 8.0)
            sy = int(np.clip(y + 2 * math.sin(x[W // 2] * 0.02 + t + y * 0.04), 0, H - 1))
            warped[y, :] = np.roll(base[sy, :], dx, axis=0)
        # Clouds slide gently in the opposite direction to the water.
        for y in range(0, 255):
            dx = int(2 * math.sin(y / 45 + t * 0.14) - direction * t * 0.9)
            warped[y, :] = np.roll(base[y, :], dx, axis=0)
        return Image.fromarray(warped, "RGB")

    def ship_scene(self, i: int, n: int, mode: str):
        t = i / FPS
        scene_len = max(n / FPS, 1)
        bg = self.animated_ocean(t, direction=-1 if mode == "recede" else 1).convert("RGBA")
        # Fine animated water glints.
        glints = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glints, "RGBA")
        for k in range(70):
            x0 = int((k * 131 + t * (25 + k % 9) + 9 * math.sin(t + k)) % W)
            y0 = 302 + (k * 29) % 220
            length = 10 + (k * 13) % 44
            alpha = 18 + (k * 7) % 34
            gd.line((x0, y0, min(W - 1, x0 + length), y0 + 1),
                    fill=(194, 219, 232, alpha), width=1)
        bg = Image.alpha_composite(bg, glints)
        # Ship drifts across the frame, rolls and bobs with the moving sea.
        p = i / max(n - 1, 1)
        if mode == "recede":
            scale = 0.92 - 0.35 * p
            x = int(102 + 300 * p)
            y = int(7 + 62 * p + 5 * math.sin(t * 2.0))
            heading = 0.85 * math.sin(t * 1.1)
        elif mode == "storm":
            scale = 1.0 - 0.06 * p
            x = int(115 + 75 * math.sin(t * 0.19))
            y = int(8 + 7 * math.sin(t * 2.15))
            heading = 1.25 * math.sin(t * 1.45)
        else:
            scale = 0.96 - 0.08 * p
            x = int(88 + 95 * p + 30 * math.sin(t * 0.18))
            y = int(5 + 7 * math.sin(t * 2.0))
            heading = 0.95 * math.sin(t * 1.55)
        sw = max(1, int(705 * scale)); sh = max(1, int(self.ship.height * sw / self.ship.width))
        boat = self.ship.resize((sw, sh), Image.Resampling.LANCZOS)
        boat = boat.rotate(heading, resample=Image.Resampling.BICUBIC, expand=True)
        # Soft shadow just below the keel.
        shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0)); sd = ImageDraw.Draw(shadow, "RGBA")
        sd.ellipse((x + 90, 414 + int(4 * math.sin(t * 2)), x + sw - 25, 454 + int(4 * math.sin(t * 2))),
                   fill=(3, 10, 18, 95))
        shadow = shadow.filter(ImageFilter.GaussianBlur(10))
        bg = Image.alpha_composite(bg, shadow)
        bg.alpha_composite(boat, (x, y))
        # A moving foreground swell passes in front of the hull, giving depth and real water motion.
        water = np.asarray(self.animated_ocean(t + 0.7, direction=1)).astype(np.float32)
        scene = np.asarray(bg.convert("RGB")).astype(np.float32)
        xx = np.arange(W)[None, :]
        yy = np.arange(H)[:, None]
        crest = 420 + 8 * np.sin(xx / 42 + t * 1.8) + 3 * np.sin(xx / 17 - t * 1.4)
        alpha = np.clip((yy - crest + 2) / 10, 0, 0.62)[:, :, None]
        scene = scene * (1 - alpha) + water * alpha
        frame = Image.fromarray(np.clip(scene, 0, 255).astype(np.uint8), "RGB")
        # Crest foam moves laterally, not as a static overlay.
        foam = Image.new("RGBA", (W, H), (0, 0, 0, 0)); fd = ImageDraw.Draw(foam, "RGBA")
        for k in range(18):
            x0 = int((k * 97 + t * 38) % W)
            y0 = int(426 + 8 * math.sin(x0 / 42 + t * 1.8))
            fd.arc((x0 - 20, y0 - 8, x0 + 35, y0 + 12), 185, 330,
                   fill=(214, 232, 237, 65), width=2)
        frame = Image.alpha_composite(frame.convert("RGBA"), foam).convert("RGB")
        label = "السفينة تمضي وحدها" if mode == "intro" else ("فرضيات لا أجوبة مؤكدة" if mode == "storm" else "السفينة تبتعد في الضباب")
        return draw_brand_and_caption(frame, label, t + (0 if mode == "intro" else 158.04 if mode == "storm" else 195.1), self.captions)

    def route_scene(self, i: int, n: int):
        t = i / FPS
        # Slow virtual camera drift over the chart.
        scale = 1.025 + 0.018 * math.sin(t * 0.08)
        big = self.chart.resize((int(W * scale), int(H * scale)), Image.Resampling.LANCZOS)
        ox = (big.width - W) // 2 + int(8 * math.sin(t * 0.18))
        oy = (big.height - H) // 2 + int(4 * math.cos(t * 0.16))
        frame = big.crop((ox, oy, ox + W, oy + H)).convert("RGBA")
        tint = Image.new("RGBA", (W, H), (4, 9, 18, 45)); frame = Image.alpha_composite(frame, tint)
        d = ImageDraw.Draw(frame, "RGBA")
        points = [(145, 380), (340, 350), (535, 324), (735, 255), (820, 206)]
        # A glowing route is revealed over time, with an animated ship marker.
        p = min(1.0, i / max(n - 1, 1))
        lengths = [math.dist(points[j], points[j + 1]) for j in range(len(points) - 1)]
        total = sum(lengths); remain = total * p
        marker = points[0]
        for j, length in enumerate(lengths):
            a, b = points[j], points[j + 1]
            if remain >= length:
                d.line((a, b), fill=(214, 177, 103, 235), width=5)
                marker = b; remain -= length
            else:
                f = max(0, remain / length)
                end = (int(a[0] + (b[0] - a[0]) * f), int(a[1] + (b[1] - a[1]) * f))
                d.line((a, end), fill=(238, 203, 128, 250), width=5)
                marker = end; break
        for pt, text in zip([points[0], points[2], points[-1]], ["نيويورك", "الأزور", "جنوة"]):
            d.ellipse((pt[0] - 7, pt[1] - 7, pt[0] + 7, pt[1] + 7), fill=(242, 206, 137, 255))
            d.text((pt[0], pt[1] + 24), rtl(text), font=ImageFont.truetype(FONT_B, 16), anchor="mm", fill=(250, 239, 213, 255), stroke_width=2, stroke_fill=(0, 0, 0, 170))
        # Moving marker: tiny brass sailboat icon.
        mx, my = marker
        d.ellipse((mx - 13, my - 13, mx + 13, my + 13), fill=(7, 14, 23, 190), outline=(246, 205, 126, 240), width=2)
        d.polygon([(mx - 7, my + 5), (mx + 8, my + 5), (mx + 2, my + 9)], fill=(245, 206, 125, 255))
        d.polygon([(mx - 1, my + 3), (mx - 1, my - 7), (mx + 5, my + 3)], fill=(248, 239, 219, 255))
        d.polygon([(mx - 3, my + 3), (mx - 3, my - 4), (mx - 7, my + 3)], fill=(223, 187, 116, 255))
        # Pulsing gold locator ring.
        radius = 16 + int(6 * (0.5 + 0.5 * math.sin(t * 3)))
        d.ellipse((mx - radius, my - radius, mx + radius, my + radius), outline=(233, 190, 111, 130), width=2)
        frame = frame.convert("RGB")
        return draw_brand_and_caption(frame, "مسار الرحلة عبر الأطلسي", t + 26.65, self.captions)

    def deck_scene(self, i: int, n: int):
        t = i / FPS
        # The camera rocks with the swells; rain and spray continue in front of the deck.
        scale = 1.045 + 0.012 * math.sin(t * 0.45)
        big = self.deck.resize((int(W * scale), int(H * scale)), Image.Resampling.LANCZOS)
        ox = (big.width - W) // 2 + int(5 * math.sin(t * 1.8))
        oy = (big.height - H) // 2 + int(4 * math.cos(t * 1.6))
        frame = big.crop((ox, oy, ox + W, oy + H)).convert("RGBA")
        rain = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(rain, "RGBA")
        for k in range(85):
            x = (k * 131 + int(t * (55 + k % 13))) % W
            y = (k * 79 + int(t * (100 + k % 17))) % H
            d.line((x, y, x - 5, y + 15 + (k % 8)), fill=(202, 218, 228, 68), width=1)
        frame = Image.alpha_composite(frame, rain)
        # A pulse of cold reflected light passes across the wet planks.
        glow = Image.new("RGBA", (W, H), (0, 0, 0, 0)); gd = ImageDraw.Draw(glow, "RGBA")
        cx = int(W * (0.25 + 0.5 * (0.5 + 0.5 * math.sin(t * 0.14))))
        gd.ellipse((cx - 130, 270, cx + 130, 520), fill=(108, 151, 177, 14 + int(10 * math.sin(t * 2))))
        frame = Image.alpha_composite(frame, glow.filter(ImageFilter.GaussianBlur(36)))
        return draw_brand_and_caption(frame.convert("RGB"), "على سطح سفينة مهجورة", t + 67.3, self.captions)

    def document_scene(self, i: int, n: int):
        t = i / FPS
        scale = 1.02 + 0.025 * min(1, t / 30) + 0.006 * math.sin(t * 0.23)
        big = self.docs.resize((int(W * scale), int(H * scale)), Image.Resampling.LANCZOS)
        ox = (big.width - W) // 2 + int(10 * math.sin(t * 0.12))
        oy = (big.height - H) // 2 + int(5 * math.cos(t * 0.17))
        frame = big.crop((ox, oy, ox + W, oy + H)).convert("RGBA")
        # A file page slides into view, turns slightly, then settles on the desk.
        page = Image.new("RGBA", (300, 188), (0, 0, 0, 0)); pd = ImageDraw.Draw(page, "RGBA")
        pd.rounded_rectangle((5, 5, 294, 182), radius=5, fill=(221, 205, 164, 245), outline=(114, 86, 49, 220), width=2)
        pd.text((150, 27), rtl("ملف التحقيق"), font=ImageFont.truetype(FONT_B, 18), anchor="mm", fill=(45, 39, 30, 255))
        pd.line((30, 52, 270, 52), fill=(105, 91, 68, 200), width=2)
        for row in range(5):
            width = 180 + (row * 17) % 70
            pd.line((34, 74 + row * 17, 34 + width, 74 + row * 17), fill=(110, 97, 76, 170), width=2)
        pd.polygon([(266, 6), (294, 34), (266, 34)], fill=(178, 158, 119, 230))
        # animate a page entering from below-left
        progress = min(1.0, t / 2.3)
        px = int(-320 + progress * 520 + 14 * math.sin(t * 0.5))
        py = int(300 + 15 * math.sin(t * 0.4))
        rotated = page.rotate(3 * math.sin(t * 0.65), resample=Image.Resampling.BICUBIC, expand=True)
        alpha = int(238 * min(1.0, t / 1.2))
        if alpha > 0:
            rotated.putalpha(rotated.getchannel("A").point(lambda a: a * alpha // 255))
            frame.alpha_composite(rotated, (px, py))
        # Flickering desk-lamp warmth adds visible life to the still paperwork.
        light = Image.new("RGBA", (W, H), (0, 0, 0, 0)); ld = ImageDraw.Draw(light, "RGBA")
        strength = 12 + int(7 * (0.5 + 0.5 * math.sin(t * 7.0)))
        ld.ellipse((-70, -80, 350, 300), fill=(250, 194, 111, strength))
        frame = Image.alpha_composite(frame, light.filter(ImageFilter.GaussianBlur(55)))
        return draw_brand_and_caption(frame.convert("RGB"), "تحقيق بلا تفسير نهائي", t + 108.8, self.captions)

    def render(self, output: Path, test_seconds: float = 0):
        ff = imageio_ffmpeg.get_ffmpeg_exe()
        # Frame counts match the three narration segments and their short pauses.
        scenes = [
            ("ship", 533), ("route", 813), ("deck", 830),
            ("documents", 984), ("storm", 741), ("recede", 606),
        ]
        if test_seconds:
            scenes = [("ship", max(1, round(test_seconds * FPS)))]
        total = sum(n for _, n in scenes)
        cmd = [ff, "-hide_banner", "-loglevel", "error", "-y",
               "-f", "rawvideo", "-pixel_format", "rgb24", "-video_size", f"{W}x{H}",
               "-framerate", str(FPS), "-i", "-"]
        if not test_seconds:
            cmd += ["-i", str(self.audio_path)]
        cmd += ["-vf", "scale=1280:720:flags=lanczos,format=yuv420p",
                "-c:v", "libx264", "-preset", "medium", "-crf", "21", "-r", str(FPS)]
        if not test_seconds:
            cmd += ["-map", "0:v:0", "-map", "1:a:0", "-c:a", "aac", "-b:a", "160k", "-shortest",
                    "-metadata", "title=Mary Celeste — animated cut | Wara al-Hikaya",
                    "-metadata", "comment=Animated ship, moving ocean, map route, rain and paper motion"]
        else:
            cmd += ["-an"]
        cmd += ["-movflags", "+faststart", str(output)]
        proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
        global_frame = 0
        try:
            for mode, count in scenes:
                for i in range(count):
                    if mode == "ship":
                        frame = self.ship_scene(i, count, "intro")
                    elif mode == "route":
                        frame = self.route_scene(i, count)
                    elif mode == "deck":
                        frame = self.deck_scene(i, count)
                    elif mode == "documents":
                        frame = self.document_scene(i, count)
                    elif mode == "storm":
                        frame = self.ship_scene(i, count, "storm")
                    else:
                        frame = self.ship_scene(i, count, "recede")
                    proc.stdin.write(np.asarray(frame, dtype=np.uint8).tobytes())
                    global_frame += 1
                    if global_frame % 500 == 0:
                        print(f"rendered {global_frame}/{total} frames", flush=True)
        finally:
            if proc.stdin:
                proc.stdin.close()
        code = proc.wait()
        if code:
            raise RuntimeError(f"ffmpeg exited with status {code}")
        print(f"Wrote {output} ({global_frame} frames at {FPS} fps)")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=str(ROOT / "Mary-Celeste-main.mp4"))
    parser.add_argument("--test-seconds", type=float, default=0,
                        help="Render a silent short ship-motion test instead of the full episode")
    args = parser.parse_args()
    MotionRenderer().render(Path(args.output), args.test_seconds)

if __name__ == "__main__":
    main()
