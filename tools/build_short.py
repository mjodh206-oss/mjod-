#!/usr/bin/env python3
"""Render a finished 9:16 YouTube Short from an episode JSON + background + VO audio.

Outputs, all gitignored (generated media stays out of Git):
    content/videos/<id>.mp4        H.264/AAC, burned-in captions, 1080x1920
    content/videos/<id>.srt        full VO subtitles for accessibility / Studio
    assets/thumbnails/<id>.jpg     3-word thumbnail text over the background

The beat timeline is scaled to the *actual* voiceover duration so captions stay in sync.
One still frame per beat + a concat list keeps encoding cheap (no giant frame buffers).

Usage:
    python tools/build_short.py --episode ep-001
    python tools/build_short.py --all
    python tools/build_short.py --all --dry-run     # show the plan only
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import timedelta
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
EPISODE_DIR = ROOT / "content" / "episodes"
BG_DIR = ROOT / "assets" / "backgrounds"
AUDIO_DIR = ROOT / "assets" / "audio"
VIDEO_DIR = ROOT / "content" / "videos"
THUMB_DIR = ROOT / "assets" / "thumbnails"
W, H = 1080, 1920
FONTS = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf",
    "/Library/Fonts/Arial Bold.ttf",
]
DURATION_RE = re.compile(r"Duration: (\d+):(\d+):(\d+\.\d+)")
TIME_RE = re.compile(r"^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)$")


def deps() -> tuple[Any, str]:
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError as exc:
        raise SystemExit(
            "Pillow is required: python3 -m pip install Pillow imageio-ffmpeg"
        ) from exc
    ffmpeg = shutil.which("ffmpeg") or os.environ.get("FFMPEG_PATH")
    if not ffmpeg:
        try:
            import imageio_ffmpeg
            ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
        except ImportError as exc:
            raise SystemExit(
                "No ffmpeg found. Install one: python3 -m pip install imageio-ffmpeg "
                "(bundles a static binary), or set FFMPEG_PATH."
            ) from exc
    return (Image, ImageDraw, ImageFont), ffmpeg


def font(ImageFont, size: int):
    for path in FONTS:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size=size)


def cover(img, zoom: float = 1.0, pan: int = 0):
    """Resize + center-crop to exactly 1080x1920 (no letterboxing), with an
    optional slow push-in / pan so each beat gets a fresh framing."""
    from PIL import Image as _Image
    src_ratio = img.width / img.height
    dst_ratio = W / H
    if src_ratio > dst_ratio:
        # source is wider than the target: match height, crop the sides
        new_h, new_w = H, math.ceil(H * src_ratio)
        img = img.resize((new_w, new_h), _Image.LANCZOS)
        left = (new_w - W) // 2
        img = img.crop((left, 0, left + W, H))
    else:
        # source is narrower than the target: match width, crop top/bottom
        new_w, new_h = W, math.ceil(W / src_ratio)
        img = img.resize((new_w, new_h), _Image.LANCZOS)
        top = (new_h - H) // 2
        img = img.crop((0, top, W, top + H))
    if zoom > 1.0 or pan:
        zw, zh = math.ceil(W * zoom), math.ceil(H * zoom)
        img = img.resize((zw, zh), _Image.LANCZOS)
        left = max(0, min(zw - W, (zw - W) // 2 + pan))
        top = max(0, (zh - H) // 2)
        img = img.crop((left, top, left + W, top + H))
    return img.convert("RGB")


def feathered_band(overlay, y0: int, y1: int, max_alpha: int, feather: int = 100):
    """Soft-edged dark band so captions read without a harsh rectangle."""
    from PIL import ImageDraw as _ImageDraw
    d = _ImageDraw.Draw(overlay)
    for y in range(y0, y1):
        t = min(1.0, (y - y0) / feather, (y1 - y) / feather)
        d.line([(0, y), (W, y)], fill=(0, 0, 0, int(max_alpha * max(0.0, t))))


def wrap(draw, text: str, fnt, max_width: int) -> list[str]:
    lines, current = [], ""
    for word in text.split():
        trial = (current + " " + word).strip()
        if draw.textlength(trial, font=fnt) <= max_width or not current:
            current = trial
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def draw_caption(base, libs, caption: str, series: str, index: int,
                 total: int, is_hook: bool) -> Any:
    Image, ImageDraw, ImageFont = libs
    img = base.copy()
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    # soft dark band behind the caption, upper third
    feathered_band(overlay, 180, 980, 165)
    d.rectangle([(0, 1740), (W, H)], fill=(0, 0, 0, 150))
    img = Image.alpha_composite(img.convert("RGBA"), overlay)
    d = ImageDraw.Draw(img)

    series_font = font(ImageFont, 40)
    d.text((70, 90), series.upper(), font=series_font, fill=(255, 210, 90, 255),
           stroke_width=2, stroke_fill=(0, 0, 0, 255))

    size = 96 if is_hook else 78
    cap_font = font(ImageFont, size)
    lines = wrap(d, caption.upper(), cap_font, W - 150)
    line_h = size + 18
    block_h = len(lines) * line_h
    y = 250 + (650 - block_h) // 2
    for line in lines:
        tw = d.textlength(line, font=cap_font)
        d.text(((W - tw) / 2, y), line, font=cap_font, fill=(255, 255, 255, 255),
               stroke_width=6, stroke_fill=(0, 0, 0, 255))
        y += line_h

    # progress bar = cheap pattern-interrupt cue for the editor
    bar_w = (W - 140) * (index + 1) / total
    d.rectangle([(70, 1810), (70 + bar_w, 1832)], fill=(255, 210, 90, 255))
    d.text((70, 1850), f"{index + 1:02d} / {total:02d}", font=font(ImageFont, 30),
           fill=(220, 220, 220, 255))
    return img.convert("RGB")


def thumbnail(libs, bg_path: Path, text: str, out: Path) -> Path:
    Image, ImageDraw, ImageFont = libs
    img = cover(Image.open(bg_path))
    img = Image.blend(img, Image.new("RGB", (W, H), (0, 0, 0)), 0.30).convert("RGBA")
    d = ImageDraw.Draw(img)
    fnt = font(ImageFont, 130)
    lines = wrap(d, text.upper(), fnt, W - 140)
    line_h = 150
    y = 620
    for line in lines:
        tw = d.textlength(line, font=fnt)
        d.text(((W - tw) / 2, y), line, font=fnt, fill=(255, 255, 255, 255),
               stroke_width=10, stroke_fill=(0, 0, 0, 255))
        y += line_h
    out.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(out, "JPEG", quality=92)
    return out


def probe_duration(ffmpeg: str, audio: Path) -> float:
    result = subprocess.run(
        [ffmpeg, "-hide_banner", "-i", str(audio), "-f", "null", "-"],
        capture_output=True, text=True, check=False,
    )
    m = DURATION_RE.search(result.stderr)
    if not m:
        return 45.0
    hh, mm, ss = m.groups()
    return int(hh) * 3600 + int(mm) * 60 + float(ss)


def srt_time(seconds: float) -> str:
    td = timedelta(seconds=max(0.0, seconds))
    total = td.total_seconds()
    h = int(total // 3600)
    m = int((total % 3600) // 60)
    s = int(total % 60)
    ms = int((total - int(total)) * 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def write_srt(ep: dict, beats: list[tuple[float, float]], out: Path) -> Path:
    lines = []
    for i, ((start, end), beat) in enumerate(zip(beats, ep["beats"]), 1):
        lines += [str(i), f"{srt_time(start)} --> {srt_time(end)}", beat["vo"].strip(), ""]
    out.write_text("\n".join(lines), encoding="utf-8")
    return out


def build(ep: dict, libs, ffmpeg: str, dry_run: bool = False) -> dict[str, Any]:
    eid = ep["id"]
    bg = BG_DIR / f"{eid}.png"
    audio = AUDIO_DIR / f"{eid}.mp3"
    mp4 = VIDEO_DIR / f"{eid}.mp4"
    srt = VIDEO_DIR / f"{eid}.srt"
    thumb = THUMB_DIR / f"{eid}.jpg"
    missing = [str(p) for p in (bg, audio) if not p.exists()]
    if missing:
        raise FileNotFoundError(f"{eid}: missing assets: {', '.join(missing)}")

    adur = probe_duration(ffmpeg, audio)
    scale = max(0.6, min(1.8, adur / ep["duration_seconds"]))
    segments: list[tuple[float, float, dict]] = []
    cursor = 0.0
    for beat in ep["beats"]:
        m = TIME_RE.match(beat["t"])
        start, end = float(m.group(1)), float(m.group(2))
        length = (end - start) * scale
        segments.append((cursor, cursor + length, beat))
        cursor += length
    # small tail so the audio is never cut by -shortest
    total = cursor + 0.6
    if dry_run:
        return {"id": eid, "audio_s": round(adur, 1), "video_s": round(total, 1),
                "beats": len(segments), "mp4": str(mp4.relative_to(ROOT))}

    Image, ImageDraw, ImageFont = libs
    source = Image.open(bg)
    VIDEO_DIR.mkdir(parents=True, exist_ok=True)
    THUMB_DIR.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix=f"short-{eid}-") as tmp:
        tmpdir = Path(tmp)
        concat = []
        for i, (_, _, beat) in enumerate(segments):
            # slow push-in + alternating pan = a fresh framing on every beat
            zoom = 1.0 + 0.014 * i
            pan = 24 if i % 2 else -24
            base = cover(source, zoom=zoom, pan=pan)
            base = Image.blend(base, Image.new("RGB", (W, H), (6, 8, 14)), 0.12)
            frame = draw_caption(base, libs, beat["on_screen"], ep["series"],
                                 i, len(segments), is_hook=(i == 0))
            path = tmpdir / f"frame_{i:02d}.png"
            frame.save(path, "PNG", optimize=True)
            concat.append(f"file '{path}'")
            concat.append(f"duration {segments[i][1] - segments[i][0]:.3f}")
        concat.append(f"file '{tmpdir / ('frame_%02d.png' % (len(segments) - 1))}'")
        concat.append("duration 0.600")
        concat.append(f"file '{tmpdir / ('frame_%02d.png' % (len(segments) - 1))}'")
        listfile = tmpdir / "frames.txt"
        listfile.write_text("\n".join(concat) + "\n", encoding="utf-8")

        cmd = [
            ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
            "-f", "concat", "-safe", "0", "-i", str(listfile),
            "-i", str(audio),
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
            "-pix_fmt", "yuv420p", "-r", "30",
            "-c:a", "aac", "-b:a", "160k", "-ar", "44100",
            "-shortest", "-movflags", "+faststart", str(mp4),
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, check=False)
        if result.returncode != 0:
            raise RuntimeError(f"ffmpeg failed for {eid}: {result.stderr[-600:]}")

    thumbnail(libs, bg, ep["thumbnail"]["text"], thumb)
    times = [(s, e) for s, e, _ in segments]
    write_srt(ep, times, srt)
    return {
        "id": eid,
        "audio_s": round(adur, 2),
        "video_s": round(total, 2),
        "beats": len(segments),
        "mp4": str(mp4.relative_to(ROOT)),
        "srt": str(srt.relative_to(ROOT)),
        "thumbnail": str(thumb.relative_to(ROOT)),
        "size_mb": round(mp4.stat().st_size / 1e6, 2),
    }


def load_episodes(only: str | None) -> list[dict]:
    files = sorted(EPISODE_DIR.glob("ep-*.json"))
    out = []
    for p in files:
        ep = json.loads(p.read_text(encoding="utf-8"))
        if only and ep["id"] != only:
            continue
        out.append(ep)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="Render 9:16 Shorts from the content pack")
    ap.add_argument("--episode", help="build one episode (e.g. ep-001)")
    ap.add_argument("--all", action="store_true", help="build every episode with assets")
    ap.add_argument("--dry-run", action="store_true", help="print the plan without encoding")
    args = ap.parse_args()
    if not args.episode and not args.all:
        ap.error("pass --episode ep-001 or --all")
    libs, ffmpeg = deps()
    episodes = load_episodes(args.episode)
    if not episodes:
        print("No episodes found", file=sys.stderr)
        return 2
    results = []
    for ep in episodes:
        try:
            info = build(ep, libs, ffmpeg, args.dry_run)
            results.append(info)
            print(f"✓ {ep['id']}: {info['video_s']}s video · {info['beats']} captioned beats"
                  + (f" · {info['size_mb']} MB" if "size_mb" in info else " (dry run)"))
        except (FileNotFoundError, RuntimeError) as exc:
            print(f"! {ep['id']}: {exc}", file=sys.stderr)
            results.append({"id": ep["id"], "error": str(exc)})
    print(json.dumps(results, indent=2))
    return 1 if any("error" in r for r in results) else 0


if __name__ == "__main__":
    sys.exit(main())
