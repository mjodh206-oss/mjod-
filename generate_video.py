#!/usr/bin/env python3
"""
High-Retention Arabic Short Video Generator (9:16 Vertical, 1080x1920 @ 30fps)
Supports both `edge-tts` (`--tts edge-tts`) and prebuilt audio (`--tts prebuilt`).
"""

import argparse
import asyncio
import math
import os
import re
import subprocess
import wave
from dataclasses import dataclass
from typing import List, Tuple

import imageio_ffmpeg
import numpy as np
from arabic_reshaper import ArabicReshaper
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

WIDTH = 1080
HEIGHT = 1920
FPS = 30
SAMPLE_RATE = 44100

RESAPER = ArabicReshaper({"use_unshaped_instead_of_isolated": True})


def shape_ar(text: str) -> str:
    return get_display(RESAPER.reshape(text))


@dataclass
class CaptionCard:
    lines: List[Tuple[str, str]]  # (arabic_text, hex_color)
    weight: float = 1.0


@dataclass
class BeatSpec:
    beat_id: str
    image_path: str
    prebuilt_audio: str
    spoken_text: str
    timer_label: str
    oxygen_pct: int
    accent_color: str
    motion_mode: str  # hook, tremor, blackout, quake, shockwave, loop
    captions: List[CaptionCard]


BEATS: List[BeatSpec] = [
    BeatSpec(
        beat_id="beat_01_hook",
        image_path="assets/images/scene_01_hook.png",
        prebuilt_audio="assets/audio/beat_01_hook.mp3",
        spoken_text="لو اختفى الأكسجين لخمس ثوانٍ فقط، ستظن أنك تستطيع حبس أنفاسك، لكن الحقيقة مرعبة!",
        timer_label="00 ثانية",
        oxygen_pct=21,
        accent_color="#00F0FF",
        motion_mode="hook",
        captions=[
            CaptionCard([("لو اختفى الأكسجين", "#FFFFFF"), ("لخمس ثوانٍ فقط!", "#00F0FF")], 1.2),
            CaptionCard([("ستظن أنك تستطيع", "#FFFFFF"), ("حبس أنفاسك...", "#FFD166")], 1.1),
            CaptionCard([("لكن الحقيقة", "#FFFFFF"), ("مرعبة!", "#FF3B30")], 0.8),
        ],
    ),
    BeatSpec(
        beat_id="beat_02_concrete",
        image_path="assets/images/scene_02_concrete.png",
        prebuilt_audio="assets/audio/beat_02_concrete.mp3",
        spoken_text="في الثانية الأولى، كل مبنى خرساني وجسر حولك سيتفتت إلى غبار، لأن الأكسجين هو الغراء الذي يربط جزيئات الإسمنت.",
        timer_label="01 ثانية",
        oxygen_pct=0,
        accent_color="#FFB800",
        motion_mode="tremor",
        captions=[
            CaptionCard([("في الثانية الأولى", "#FFB800"), ("كل مبنى وجسر حولك", "#FFFFFF")], 1.15),
            CaptionCard([("سيتفتت إلى غبار!", "#FF5A36")], 0.9),
            CaptionCard([("لأن الأكسجين هو الغراء", "#FFFFFF"), ("الذي يربط الإسمنت", "#00F0FF")], 1.25),
        ],
    ),
    BeatSpec(
        beat_id="beat_03_sky",
        image_path="assets/images/scene_03_sky.png",
        prebuilt_audio="assets/audio/beat_03_sky.mp3",
        spoken_text="في الثانية الثانية، تختفي طبقة الأوزون فتحترق البشرة، وتنقلب السماء الزرقاء فوراً إلى ظلامٍ دامس.",
        timer_label="02 ثانية",
        oxygen_pct=0,
        accent_color="#A855F7",
        motion_mode="blackout",
        captions=[
            CaptionCard([("في الثانية الثانية", "#FFB800"), ("تختفي طبقة الأوزون", "#FFFFFF")], 1.1),
            CaptionCard([("فتحترق البشرة فوراً", "#FF5A36")], 0.85),
            CaptionCard([("وتنقلب السماء الزرقاء", "#00F0FF"), ("إلى ظلامٍ دامس!", "#FFFFFF")], 1.2),
        ],
    ),
    BeatSpec(
        beat_id="beat_04_crust",
        image_path="assets/images/scene_04_crust.png",
        prebuilt_audio="assets/audio/beat_04_crust.mp3",
        spoken_text="في الثانية الثالثة، تنهار القشرة الأرضية تحت قدميك، لأن نصف وزن الصخور في كوكبنا من الأكسجين!",
        timer_label="03 ثوانٍ",
        oxygen_pct=0,
        accent_color="#FF5A36",
        motion_mode="quake",
        captions=[
            CaptionCard([("في الثانية الثالثة", "#FF5A36"), ("تنهار القشرة الأرضية", "#FFFFFF")], 1.15),
            CaptionCard([("تحت قدميك فوراً!", "#FFD166")], 0.8),
            CaptionCard([("لأن نصف وزن الصخور", "#FFFFFF"), ("من الأكسجين!", "#00F0FF")], 1.15),
        ],
    ),
    BeatSpec(
        beat_id="beat_05_engines",
        image_path="assets/images/scene_05_engines.png",
        prebuilt_audio="assets/audio/beat_05_engines.mp3",
        spoken_text="وفي الثانية الخامسة، تتوقف كل المحركات في السماء والأرض، وتنفجر طبلة أذنك من هبوط الضغط الفوري.",
        timer_label="05 ثوانٍ",
        oxygen_pct=0,
        accent_color="#FF3B30",
        motion_mode="shockwave",
        captions=[
            CaptionCard([("وفي الثانية الخامسة", "#FF3B30"), ("تتوقف كل المحركات", "#FFFFFF")], 1.15),
            CaptionCard([("في السماء والأرض!", "#FFD166")], 0.85),
            CaptionCard([("وتنفجر طبلة أذنك", "#FF3B30"), ("من هبوط الضغط الفوري", "#FFFFFF")], 1.25),
        ],
    ),
    BeatSpec(
        beat_id="beat_06_loop",
        image_path="assets/images/scene_06_loop.png",
        prebuilt_audio="assets/audio/beat_06_loop.mp3",
        spoken_text="أرأيت لماذا لا يكفي حبس أنفاسك؟ لأنه ببساطة...",
        timer_label="إعادة",
        oxygen_pct=0,
        accent_color="#00F0FF",
        motion_mode="loop",
        captions=[
            CaptionCard([("أرأيت لماذا لا يكفي", "#FFFFFF"), ("حبس أنفاسك؟", "#00F0FF")], 1.3),
            CaptionCard([("لأنه ببساطة...", "#FFD166")], 0.8),
        ],
    ),
]


def ensure_fonts() -> Tuple[str, str]:
    font_900 = ".cache/fonts/Cairo-Full-900.ttf"
    font_700 = ".cache/fonts/Cairo-Full-700.ttf"
    if os.path.exists(font_900) and os.path.exists(font_700):
        return font_900, font_700
    raise FileNotFoundError(
        "Missing Cairo-Full-900.ttf / Cairo-Full-700.ttf in .cache/fonts/. Run setup first."
    )


async def synth_edge_tts(beats: List[BeatSpec], voice: str, out_dir: str) -> List[str]:
    import edge_tts

    os.makedirs(out_dir, exist_ok=True)
    paths = []
    for b in beats:
        out_mp3 = os.path.join(out_dir, f"{b.beat_id}.mp3")
        comm = edge_tts.Communicate(b.spoken_text, voice=voice, rate="+6%")
        await comm.save(out_mp3)
        paths.append(out_mp3)
    return paths


def prepare_trimmed_wav(ffmpeg: str, src_audio: str, dst_wav: str, tempo: float = 1.06) -> float:
    os.makedirs(os.path.dirname(dst_wav), exist_ok=True)
    cmd = [
        ffmpeg,
        "-y",
        "-i",
        src_audio,
        "-af",
        (
            "silenceremove=start_periods=1:start_duration=0.02:start_threshold=-42dB,"
            "areverse,"
            "silenceremove=start_periods=1:start_duration=0.02:start_threshold=-42dB,"
            "areverse,"
            f"atempo={tempo},"
            "loudnorm=I=-14:TP=-1.5:LRA=7"
        ),
        "-ar",
        str(SAMPLE_RATE),
        "-ac",
        "2",
        dst_wav,
    ]
    subprocess.run(cmd, check=True, capture_output=True)
    res = subprocess.run([ffmpeg, "-i", dst_wav], capture_output=True, text=True)
    m = re.search(r"Duration:\s*(\d+):(\d+):(\d+\.\d+)", res.stderr)
    if not m:
        raise RuntimeError(f"Could not read duration of {dst_wav}")
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))


def read_wav_float(path: str) -> np.ndarray:
    with wave.open(path, "rb") as wf:
        n_frames = wf.getnframes()
        raw = wf.readframes(n_frames)
        data = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
        return data.reshape(-1, wf.getnchannels())


def write_wav_float(path: str, audio: np.ndarray) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    clipped = np.clip(audio, -0.98, 0.98)
    pcm = (clipped * 32767.0).astype(np.int16)
    with wave.open(path, "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(pcm.tobytes())


def align_captions_to_wav(wav_path: str, captions: List[CaptionCard], beat_dur: float) -> List[Tuple[float, float, CaptionCard]]:
    """Snap caption boundaries to natural speech energy dips near proportional weights."""
    audio = read_wav_float(wav_path)
    mono = np.mean(np.abs(audio), axis=1)
    hop = int(SAMPLE_RATE * 0.01)  # 10ms bins
    n_bins = max(1, len(mono) // hop)
    env = np.array([mono[i * hop : (i + 1) * hop].mean() for i in range(n_bins)])
    if len(env) > 5:
        kernel = np.ones(5) / 5.0
        env = np.convolve(env, kernel, mode="same")

    total_w = sum(c.weight for c in captions)
    boundaries = [0.0]
    cum_w = 0.0
    for idx, c in enumerate(captions[:-1]):
        cum_w += c.weight
        target_t = beat_dur * (cum_w / total_w)
        # Search +/- 0.35s around target_t for lowest vocal energy dip
        lo_bin = max(1, int((target_t - 0.35) / 0.01))
        hi_bin = min(len(env) - 2, int((target_t + 0.35) / 0.01))
        if hi_bin > lo_bin:
            best_bin = lo_bin + int(np.argmin(env[lo_bin:hi_bin]))
            snapped_t = best_bin * 0.01
        else:
            snapped_t = target_t
        snapped_t = max(boundaries[-1] + 0.35, min(beat_dur - 0.35, snapped_t))
        boundaries.append(snapped_t)
    boundaries.append(beat_dur)

    result = []
    for i, c in enumerate(captions):
        result.append((boundaries[i], boundaries[i + 1], c))
    return result


def build_soundtrack(
    beat_wavs: List[Tuple[float, float, str]], total_dur: float, out_wav: str
) -> None:
    """Mix crisp narration with synthesized sub-bass sci-fi drone, transition booms/whooshes, and heartbeat ticks."""
    n_samples = int(math.ceil(total_dur * SAMPLE_RATE))
    voice_track = np.zeros((n_samples, 2), dtype=np.float32)

    for start_t, _end_t, wav_p in beat_wavs:
        seg = read_wav_float(wav_p)
        s0 = int(start_t * SAMPLE_RATE)
        s1 = min(n_samples, s0 + len(seg))
        voice_track[s0:s1] += seg[: s1 - s0]

    # Ambient sci-fi sub-bass drone (55 Hz + 82.4 Hz + subtle stereo shimmer)
    t = np.arange(n_samples, dtype=np.float32) / SAMPLE_RATE
    drone_l = (
        0.55 * np.sin(2 * np.pi * 55.0 * t)
        + 0.30 * np.sin(2 * np.pi * 82.41 * t + 0.4)
        + 0.15 * np.sin(2 * np.pi * 110.0 * t + 0.1 * np.sin(2 * np.pi * 0.25 * t))
    )
    drone_r = (
        0.55 * np.sin(2 * np.pi * 55.0 * t + 0.2)
        + 0.30 * np.sin(2 * np.pi * 82.41 * t + 0.9)
        + 0.15 * np.sin(2 * np.pi * 110.0 * t + 0.1 * np.cos(2 * np.pi * 0.25 * t))
    )
    # Slow breathing LFO
    lfo = 0.72 + 0.28 * np.sin(2 * np.pi * (2.0 / total_dur) * t)
    # Smooth 0.25s edge taper for seamless looping
    edge = int(0.25 * SAMPLE_RATE)
    loop_env = np.ones(n_samples, dtype=np.float32)
    loop_env[:edge] = np.linspace(0.35, 1.0, edge)
    loop_env[-edge:] = np.linspace(1.0, 0.35, edge)

    sfx_track = np.stack([drone_l * lfo * loop_env, drone_r * lfo * loop_env], axis=1) * 0.085

    # Add sub-drop impact + whoosh on every scene cut
    rng = np.random.default_rng(42)
    for idx, (start_t, _end_t, _wav_p) in enumerate(beat_wavs):
        s0 = int(start_t * SAMPLE_RATE)
        # Impact boom (0.45s exponential pitch drop 110Hz -> 36Hz)
        boom_len = int(0.48 * SAMPLE_RATE)
        bt = np.arange(boom_len, dtype=np.float32) / SAMPLE_RATE
        freq_sweep = 36.0 + 78.0 * np.exp(-bt * 12.0)
        phase = 2 * np.pi * np.cumsum(freq_sweep) / SAMPLE_RATE
        boom = np.sin(phase) * np.exp(-bt * 7.5) * (0.28 if idx == 0 else 0.24)
        # Subtle air whoosh noise burst
        noise = rng.normal(0.0, 1.0, boom_len).astype(np.float32)
        # Simple 1-pole lowpass for warm whoosh
        whoosh = np.zeros_like(noise)
        alpha = 0.08
        acc = 0.0
        for i in range(boom_len):
            acc = acc + alpha * (noise[i] - acc)
            whoosh[i] = acc
        whoosh *= np.exp(-bt * 9.0) * 0.18

        hit = boom + whoosh
        end_idx = min(n_samples, s0 + boom_len)
        take = end_idx - s0
        sfx_track[s0:end_idx, 0] += hit[:take]
        sfx_track[s0:end_idx, 1] += hit[:take]

    # Tension clock tick every 1.0 second
    tick_len = int(0.055 * SAMPLE_RATE)
    tt = np.arange(tick_len, dtype=np.float32) / SAMPLE_RATE
    tick_wave = np.sin(2 * np.pi * 980.0 * tt) * np.exp(-tt * 75.0) * 0.09
    sec = 1.0
    while sec < total_dur - 0.3:
        s0 = int(sec * SAMPLE_RATE)
        end_idx = min(n_samples, s0 + tick_len)
        take = end_idx - s0
        sfx_track[s0:end_idx, 0] += tick_wave[:take]
        sfx_track[s0:end_idx, 1] += tick_wave[:take]
        sec += 1.0

    # Sidechain-like ducking of SFX under active voice
    voice_amp = np.mean(np.abs(voice_track), axis=1)
    win = int(0.08 * SAMPLE_RATE)
    smooth_amp = np.convolve(voice_amp, np.ones(win, dtype=np.float32) / win, mode="same")
    duck = 1.0 - 0.55 * np.clip(smooth_amp * 5.0, 0.0, 1.0)
    sfx_track *= duck[:, None]

    mixed = voice_track * 0.95 + sfx_track
    write_wav_float(out_wav, mixed)


def hex_to_rgba(h: str, alpha: int = 255) -> Tuple[int, int, int, int]:
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


def render_caption_layer(card: CaptionCard, font: ImageFont.FreeTypeFont) -> Image.Image:
    """Pre-render crisp caption card on a transparent RGBA canvas for fast compositing."""
    canvas = Image.new("RGBA", (WIDTH, 420), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    line_h = 104
    total_h = len(card.lines) * line_h
    y = (420 - total_h) // 2

    for text, color in card.lines:
        shaped = shape_ar(text)
        bbox = draw.textbbox((0, 0), shaped, font=font)
        tw = bbox[2] - bbox[0]
        x = (WIDTH - tw) // 2
        # Soft outer shadow
        draw.text((x, y + 6), shaped, font=font, fill=(0, 0, 0, 175), stroke_width=14, stroke_fill=(0, 0, 0, 175))
        # Crisp dark border + bright fill
        draw.text((x, y), shaped, font=font, fill=hex_to_rgba(color), stroke_width=8, stroke_fill=(5, 8, 16, 245))
        y += line_h

    return canvas


def build_gradient_vignette() -> Image.Image:
    """Top & bottom dark gradient bands so HUD and captions always pop cleanly."""
    arr = np.zeros((HEIGHT, WIDTH, 4), dtype=np.uint8)
    y = np.arange(HEIGHT, dtype=np.float32)[:, None]
    top_alpha = np.clip((320.0 - y) / 320.0, 0.0, 1.0) ** 1.4 * 185.0
    bot_alpha = np.clip((y - 1100.0) / 820.0, 0.0, 1.0) ** 1.3 * 215.0
    alpha = np.clip(top_alpha + bot_alpha, 0, 230).astype(np.uint8)
    arr[:, :, 0] = 4
    arr[:, :, 1] = 7
    arr[:, :, 2] = 14
    arr[:, :, 3] = np.broadcast_to(alpha, (HEIGHT, WIDTH))
    return Image.fromarray(arr, mode="RGBA")


def render_hud_overlay(timer_label: str, oxygen_pct: int, accent_hex: str, font_hud: ImageFont.FreeTypeFont, pulse: float) -> Image.Image:
    """Draw dual glassmorphic HUD telemetry pills (zero BiDi punctuation clashes)."""
    hud = Image.new("RGBA", (WIDTH, 220), (0, 0, 0, 0))
    draw = ImageDraw.Draw(hud)
    accent = hex_to_rgba(accent_hex, int(190 + 60 * pulse))

    # Right pill: Oxygen level
    ox_text = shape_ar(f"الأكسجين: %{oxygen_pct}" if oxygen_pct > 0 else "الأكسجين: منعدم")
    # Actually let's keep explicit clean digits in separate badge or pure Arabic + clean %
    ox_text = shape_ar("الأكسجين: 21%" if oxygen_pct > 0 else "الأكسجين: 0%")
    # Fix neutral % placement cleanly by keeping two separate spans or Arabic label
    ox_label = shape_ar("مستوى الأكسجين:")
    ox_val = f"{oxygen_pct}%"
    b1 = draw.textbbox((0, 0), ox_label, font=font_hud)
    b2 = draw.textbbox((0, 0), ox_val, font=font_hud)
    w1, w2 = b1[2] - b1[0], b2[2] - b2[0]
    pill_w = w1 + w2 + 76
    rx1 = WIDTH - 54 - pill_w
    rx2 = WIDTH - 54
    draw.rounded_rectangle([rx1, 86, rx2, 160], radius=34, fill=(8, 13, 24, 210), outline=accent, width=3)
    ox_col = (0, 240, 255, 255) if oxygen_pct > 0 else (255, 68, 58, 255)
    draw.text((rx1 + 26, 97), ox_val, font=font_hud, fill=ox_col)
    draw.text((rx1 + 26 + w2 + 16, 97), ox_label, font=font_hud, fill=(245, 248, 255, 255))

    # Left pill: Countdown Timer
    t_text = shape_ar(timer_label)
    tb = draw.textbbox((0, 0), t_text, font=font_hud)
    tw = tb[2] - tb[0]
    lx1 = 54
    lx2 = lx1 + tw + 68
    draw.rounded_rectangle([lx1, 86, lx2, 160], radius=34, fill=(8, 13, 24, 210), outline=accent, width=3)
    # Glowing indicator dot
    dot_col = (255, 59, 48, int(170 + 85 * pulse)) if oxygen_pct == 0 else (0, 240, 255, 255)
    draw.ellipse([lx1 + 20, 114, lx1 + 38, 132], fill=dot_col)
    draw.text((lx1 + 48, 97), t_text, font=font_hud, fill=(255, 255, 255, 255))

    return hud


def generate_video(tts_mode: str = "prebuilt", edge_voice: str = "ar-SA-HamedNeural", output_path: str = "output/oxygen_5_seconds_retention.mp4") -> None:
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    font_900_path, font_700_path = ensure_fonts()
    font_cap = ImageFont.truetype(font_900_path, 78)
    font_hud = ImageFont.truetype(font_700_path, 34)

    os.makedirs(".cache/work_audio", exist_ok=True)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    # 1. Obtain speech audio per beat
    if tts_mode == "edge-tts":
        raw_paths = asyncio.run(synth_edge_tts(BEATS, edge_voice, ".cache/edge_tts_audio"))
    else:
        raw_paths = [b.prebuilt_audio for b in BEATS]

    # 2. Trim silence, normalize, align captions
    beat_timeline = []
    t_cursor = 0.0
    beat_wavs = []
    for b, raw_p in zip(BEATS, raw_paths):
        wav_p = os.path.join(".cache/work_audio", f"{b.beat_id}.wav")
        dur = prepare_trimmed_wav(ffmpeg, raw_p, wav_p, tempo=1.06)
        aligned_caps = align_captions_to_wav(wav_p, b.captions, dur)
        beat_timeline.append((t_cursor, t_cursor + dur, dur, b, aligned_caps))
        beat_wavs.append((t_cursor, t_cursor + dur, wav_p))
        t_cursor += dur

    total_dur = t_cursor
    total_frames = int(round(total_dur * FPS))
    print(f"Total video duration: {total_dur:.2f}s ({total_frames} frames @ {FPS}fps)")

    # 3. Build mixed soundtrack (narration + sci-fi drone + impacts + countdown ticks)
    mixed_wav = ".cache/work_audio/final_mix.wav"
    build_soundtrack(beat_wavs, total_dur, mixed_wav)

    # 4. Preload 1.24x oversized images for ultra-fast smooth Ken Burns & camera shake
    over_w, over_h = int(WIDTH * 1.24), int(HEIGHT * 1.24)
    base_images = {}
    for b in BEATS:
        im = Image.open(b.image_path).convert("RGB").resize((over_w, over_h), Image.Resampling.LANCZOS)
        base_images[b.beat_id] = im

    # Pre-render caption RGBA layers
    caption_layers = {}
    for b in BEATS:
        for idx, card in enumerate(b.captions):
            caption_layers[(b.beat_id, idx)] = render_caption_layer(card, font_cap)

    vignette = build_gradient_vignette()

    # Pre-generate deterministic floating oxygen/ember particles
    rng = np.random.default_rng(7)
    n_particles = 42
    p_x = rng.uniform(0, WIDTH, n_particles)
    p_y = rng.uniform(0, HEIGHT, n_particles)
    p_vy = rng.uniform(-90, -25, n_particles)
    p_vx = rng.uniform(-14, 14, n_particles)
    p_r = rng.uniform(2.0, 5.5, n_particles)
    p_col = rng.choice([0, 1], n_particles, p=[0.65, 0.35])

    # 5. Stream raw RGB frames directly into FFmpeg H.264 encoder + mixed audio
    cmd = [
        ffmpeg,
        "-y",
        "-f",
        "rawvideo",
        "-vcodec",
        "rawvideo",
        "-s",
        f"{WIDTH}x{HEIGHT}",
        "-pix_fmt",
        "rgb24",
        "-r",
        str(FPS),
        "-i",
        "-",
        "-i",
        mixed_wav,
        "-c:v",
        "libx264",
        "-preset",
        "medium",
        "-crf",
        "17",
        "-pix_fmt",
        "yuv420p",
        "-c:a",
        "aac",
        "-b:a",
        "192k",
        "-shortest",
        "-movflags",
        "+faststart",
        output_path,
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stderr=subprocess.PIPE)

    hud_cache = {}

    for frame_idx in range(total_frames):
        t = frame_idx / FPS
        progress = min(1.0, t / total_dur)

        # Find active beat
        active = beat_timeline[-1]
        beat_idx = len(beat_timeline) - 1
        for i, item in enumerate(beat_timeline):
            if item[0] <= t < item[1]:
                active = item
                beat_idx = i
                break

        b_start, _b_end, b_dur, beat, aligned_caps = active
        local_t = max(0.0, t - b_start)
        local_p = min(1.0, local_t / max(0.001, b_dur))

        # Dynamic camera transform (zoom + pan + impact shake)
        zoom = 1.04 + 0.11 * local_p
        dx, dy = 0.0, 0.0
        flash = 0.0

        if beat.motion_mode == "hook":
            # Punch-in zoom on first 0.5s + micro shake at ~4.8s ("مرعبة!")
            zoom = 1.20 - 0.14 * min(1.0, local_t / 0.55) + 0.05 * local_p
            if local_t < 0.25:
                flash = (1.0 - local_t / 0.25) * 0.22
            if 4.4 <= local_t <= 5.2:
                shake = math.sin(local_t * 55.0) * 14.0 * (1.0 - abs(local_t - 4.8) / 0.4)
                dx, dy = shake, shake * 0.6
        elif beat.motion_mode == "tremor":
            zoom = 1.04 + 0.12 * local_p
            if local_t < 0.35:
                dx = math.sin(local_t * 70.0) * 18.0 * (1.0 - local_t / 0.35)
                dy = math.cos(local_t * 62.0) * 14.0 * (1.0 - local_t / 0.35)
            else:
                dy = math.sin(local_t * 18.0) * 3.5
        elif beat.motion_mode == "blackout":
            zoom = 1.14 - 0.09 * local_p
            dy = -35.0 * local_p
            if local_t < 0.22:
                flash = (1.0 - local_t / 0.22) * 0.45
        elif beat.motion_mode == "quake":
            zoom = 1.05 + 0.13 * local_p
            amp = 16.0 if local_t < 0.6 else 5.5
            dx = math.sin(local_t * 52.0) * amp
            dy = math.cos(local_t * 44.0) * amp * 0.8
        elif beat.motion_mode == "shockwave":
            zoom = 1.04 + 0.16 * local_p
            if local_t < 0.35:
                flash = (1.0 - local_t / 0.35) * 0.35
                dx = math.sin(local_t * 65.0) * 12.0 * (1.0 - local_t / 0.35)
        elif beat.motion_mode == "loop":
            # Pull back smoothly toward opening energy
            zoom = 1.14 - 0.10 * local_p

        # Crop from 1.24x oversized image
        crop_w = int(over_w / zoom)
        crop_h = int(over_h / zoom)
        cx = int(over_w / 2 + dx)
        cy = int(over_h / 2 + dy)
        x0 = max(0, min(over_w - crop_w, cx - crop_w // 2))
        y0 = max(0, min(over_h - crop_h, cy - crop_h // 2))

        frame = base_images[beat.beat_id].crop((x0, y0, x0 + crop_w, y0 + crop_h)).resize(
            (WIDTH, HEIGHT), Image.Resampling.BILINEAR
        )

        # Impact flash at cut
        if flash > 0.01:
            frame = ImageEnhance.Brightness(frame).enhance(1.0 + flash)

        frame_rgba = frame.convert("RGBA")

        # Vignette overlay
        frame_rgba.alpha_composite(vignette)

        # Floating glowing particles (wrap seamlessly in Y)
        p_layer = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
        p_draw = ImageDraw.Draw(p_layer)
        for pi in range(n_particles):
            px = (p_x[pi] + p_vx[pi] * t) % WIDTH
            py = (p_y[pi] + p_vy[pi] * t) % HEIGHT
            pr = p_r[pi]
            col = (0, 240, 255, 115) if p_col[pi] == 0 else (255, 165, 60, 115)
            p_draw.ellipse([px - pr, py - pr, px + pr, py + pr], fill=col)
        frame_rgba.alpha_composite(p_layer)

        # Oxygen level drops right when hook says "لو اختفى" -> 0% by 1.8s
        ox_pct = 21 if (beat_idx == 0 and local_t < 1.2) else 0
        pulse_step = int((t * 6.0) % 6)
        pulse = 0.5 + 0.5 * math.sin(pulse_step / 6.0 * 2 * math.pi)
        hud_key = (beat.timer_label, ox_pct, beat.accent_color, pulse_step)
        if hud_key not in hud_cache:
            hud_cache[hud_key] = render_hud_overlay(beat.timer_label, ox_pct, beat.accent_color, font_hud, pulse)
        frame_rgba.alpha_composite(hud_cache[hud_key], (0, 0))

        # Active kinetic caption with spring pop-in
        cap_idx = len(aligned_caps) - 1
        c_start, c_end = aligned_caps[-1][0], aligned_caps[-1][1]
        for ci, (cs, ce, _card) in enumerate(aligned_caps):
            if cs <= local_t < ce:
                cap_idx = ci
                c_start, c_end = cs, ce
                break
        cap_age = max(0.0, local_t - c_start)
        cap_img = caption_layers[(beat.beat_id, cap_idx)]
        if cap_age < 0.14:
            p = cap_age / 0.14
            # Overshoot spring scale 0.85 -> 1.05 -> 1.00
            scale = 0.85 + 0.20 * math.sin(p * math.pi * 0.72)
            sw = max(1, int(WIDTH * scale))
            sh = max(1, int(420 * scale))
            scaled = cap_img.resize((sw, sh), Image.Resampling.BILINEAR)
            frame_rgba.alpha_composite(scaled, ((WIDTH - sw) // 2, 1280 + (420 - sh) // 2))
        else:
            frame_rgba.alpha_composite(cap_img, (0, 1280))

        # Top retention progress bar (cyan -> amber -> crimson)
        prog_draw = ImageDraw.Draw(frame_rgba)
        bar_w = int(WIDTH * progress)
        if progress < 0.5:
            bar_col = (0, 240, 255, 240)
        elif progress < 0.8:
            bar_col = (255, 184, 0, 240)
        else:
            bar_col = (255, 59, 48, 245)
        prog_draw.rectangle([0, 0, WIDTH, 10], fill=(255, 255, 255, 35))
        if bar_w > 0:
            prog_draw.rectangle([0, 0, bar_w, 10], fill=bar_col)

        proc.stdin.write(frame_rgba.convert("RGB").tobytes())

    proc.stdin.close()
    stderr_out = proc.stderr.read().decode("utf-8", errors="ignore")
    ret = proc.wait()
    if ret != 0:
        raise RuntimeError(f"FFmpeg failed with code {ret}:\n{stderr_out[-1500:]}")
    print(f"Successfully generated {output_path}")


def main():
    parser = argparse.ArgumentParser(description="Generate 100%-retention Arabic Short video.")
    parser.add_argument(
        "--tts",
        choices=["prebuilt", "edge-tts"],
        default="prebuilt",
        help="TTS engine: 'edge-tts' (requires internet access to Bing TTS) or 'prebuilt' (uses assets/audio/).",
    )
    parser.add_argument("--voice", default="ar-SA-HamedNeural", help="edge-tts Arabic voice name.")
    parser.add_argument("--out", default="output/oxygen_5_seconds_retention.mp4", help="Output MP4 path.")
    args = parser.parse_args()
    generate_video(tts_mode=args.tts, edge_voice=args.voice, output_path=args.out)


if __name__ == "__main__":
    main()
