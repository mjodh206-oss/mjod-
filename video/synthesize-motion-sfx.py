#!/usr/bin/env python3
"""Generate quiet, original procedural Foley for the V4 animation edits.

Effects are designed around the V4 narration/SRT timing, with a low ocean bed,
subtle transition sweeps, paper/rigging details, and short boarding/pump accents.
No external samples or music are used. NumPy is required.
"""
from __future__ import annotations
import argparse
import sys
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from synthesize_mary_celeste_sfx import FS, RNG, add_pan, make_event, shaped_noise, write_wav


def gate(t: np.ndarray, start: float, end: float, fade: float = 1.5) -> np.ndarray:
    env = np.zeros_like(t, dtype=np.float32)
    inside = (t >= start) & (t < end)
    env[inside] = 1.0
    a = (t >= start) & (t < start + fade)
    env[a] *= np.sin((t[a] - start) / fade * np.pi / 2) ** 2
    b = (t >= end - fade) & (t < end)
    env[b] *= np.sin((end - t[b]) / fade * np.pi / 2) ** 2
    return env


def build(seconds: float, short: bool) -> np.ndarray:
    count = round(seconds * FS)
    t = np.arange(count, dtype=np.float32) / FS
    track = np.zeros((count, 2), dtype=np.float32)

    # A restrained stereo sea bed; wind rises gently only during the storm section.
    sea = shaped_noise(count, 'sea')
    swell = 0.65 + 0.35 * (0.5 + 0.5 * np.sin(2 * np.pi * t / (6.8 if short else 8.2)))
    sea *= swell * (0.018 if not short else 0.020)
    track[:, 0] += sea
    track[:, 1] += np.roll(sea, int(FS * 0.022)) * 0.95

    wind = shaped_noise(count, 'wind')
    if short:
        wind_env = gate(t, 0, seconds, 2.0) * 0.28
        wind_env += gate(t, 25.0, min(31.5, seconds), 1.0) * 0.30
    else:
        wind_env = gate(t, 0, 54, 2.5) * 0.24
        wind_env += gate(t, 54, 70, 2.0) * 0.68
        wind_env += gate(t, 70, 114, 2.0) * 0.30
        wind_env += gate(t, 114, 150, 2.0) * 0.16
        wind_env += gate(t, 150, 216, 2.0) * 0.11
        wind_env += gate(t, 216, seconds, 2.0) * 0.24
    wind *= wind_env * 0.013
    track[:, 0] += wind
    track[:, 1] += np.roll(wind, int(FS * 0.031)) * 0.92

    if short:
        events = [
            (0.5, 'splash', 2.0, 0.075, -0.2), (3.1, 'creak', 1.2, 0.052, 0.25),
            (6.9, 'swoosh', 0.8, 0.050, 0.0), (8.5, 'rope', 1.1, 0.060, -0.25),
            (14.5, 'swoosh', 0.8, 0.045, 0.2), (16.4, 'tap', 0.45, 0.050, -0.15),
            (22.8, 'paper', 0.9, 0.054, -0.3), (25.0, 'splash', 1.6, 0.055, 0.25),
            (30.4, 'swoosh', 0.9, 0.058, 0.0), (34.2, 'paper', 0.85, 0.045, -0.2),
            (37.0, 'tap', 0.40, 0.045, 0.18), (41.0, 'swoosh', 1.2, 0.060, 0.0),
            (43.1, 'splash', 1.8, 0.065, 0.2),
        ]
    else:
        events = [
            # Opening, route reveal, voyage and cargo beats.
            (0.6, 'splash', 2.2, 0.070, -0.20), (3.6, 'creak', 1.35, 0.050, 0.25),
            (8.0, 'swoosh', 0.95, 0.052, 0.0), (9.1, 'paper', 1.0, 0.048, -0.35),
            (14.0, 'tap', 0.45, 0.042, 0.20), (22.5, 'paper', 0.85, 0.042, 0.30),
            (34.8, 'swoosh', 0.85, 0.045, 0.0), (43.8, 'creak', 1.1, 0.045, -0.25),
            (50.0, 'tap', 0.42, 0.040, 0.25),
            # Weather and the last ship's-log entry.
            (54.0, 'swoosh', 1.0, 0.058, -0.15), (56.5, 'splash', 2.8, 0.078, 0.25),
            (59.2, 'paper', 1.15, 0.055, -0.30), (63.2, 'tap', 0.45, 0.050, 0.10),
            (66.2, 'creak', 1.2, 0.045, -0.20),
            # De Gratia sighting; small boat and sailors on the ladder.
            (67.2, 'swoosh', 1.25, 0.065, 0.0), (70.0, 'splash', 2.2, 0.060, -0.25),
            (76.0, 'rope', 1.3, 0.055, 0.25), (81.2, 'swoosh', 0.9, 0.055, 0.0),
            (82.5, 'rope', 1.5, 0.065, -0.2), (84.2, 'tap', 0.32, 0.047, 0.1),
            (86.1, 'tap', 0.32, 0.047, -0.1), (88.0, 'splash', 2.2, 0.072, 0.25),
            # Empty deck, pump, waterline, and investigation papers.
            (90.0, 'swoosh', 0.9, 0.052, 0.0), (92.2, 'creak', 1.6, 0.055, -0.2),
            (96.0, 'splash', 1.8, 0.045, 0.18), (105.5, 'paper', 1.2, 0.064, -0.35),
            (108.0, 'swoosh', 0.85, 0.048, 0.0), (115.0, 'tap', 0.4, 0.045, 0.25),
            (125.7, 'paper', 1.1, 0.050, 0.35), (128.2, 'swoosh', 0.9, 0.050, 0.0),
            # Myth-busting, then three distinct hypothesis graphics.
            (132.0, 'paper', 1.0, 0.045, -0.2), (148.0, 'swoosh', 1.1, 0.055, 0.0),
            (153.0, 'tap', 0.35, 0.043, -0.25), (168.5, 'tap', 0.35, 0.043, 0.20),
            (184.0, 'paper', 0.85, 0.042, -0.20), (201.0, 'tap', 0.35, 0.038, 0.22),
            # Quiet, warm ending swell.
            (214.5, 'swoosh', 1.45, 0.058, 0.0), (218.5, 'creak', 1.2, 0.047, 0.25),
            (221.0, 'splash', 2.8, 0.064, -0.15),
        ]
    for when, kind, length, gain, pan in events:
        if when < seconds:
            add_pan(track, make_event(kind, min(length, seconds - when)), when, pan, gain)

    fade_n = min(int(FS * 1.8), count // 2)
    fade = np.ones(count, dtype=np.float32)
    fade[:fade_n] = np.linspace(0, 1, fade_n, dtype=np.float32)
    fade[-fade_n:] = np.linspace(1, 0, fade_n, dtype=np.float32)
    track *= fade[:, None]
    peak = max(float(np.max(np.abs(track))), 1e-5)
    if peak > 0.32:
        track *= 0.32 / peak
    return np.clip(track, -0.99, 0.99)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--main-seconds', type=float, default=225.38)
    parser.add_argument('--short-seconds', type=float, default=45.5)
    parser.add_argument('--out-dir', type=Path, default=ROOT / 'audio/episode-01')
    args = parser.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    write_wav(args.out_dir / 'v4-motion-sfx-main.wav', build(args.main_seconds, False))
    write_wav(args.out_dir / 'v4-motion-sfx-short.wav', build(args.short_seconds, True))
    print('Wrote V4 motion-synchronized Foley WAVs.')

if __name__ == '__main__':
    main()
