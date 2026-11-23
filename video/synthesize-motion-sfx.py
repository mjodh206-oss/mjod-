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
            (7.8, 'bell', 0.8, 0.040, 0.0), (8.8, 'tap', 0.35, 0.045, -0.2),
            (15.7, 'swoosh', 0.9, 0.055, 0.0), (16.5, 'knock', 0.65, 0.048, -0.1),
            (23.8, 'paper', 1.0, 0.060, -0.3), (24.8, 'swoosh', 0.8, 0.055, 0.15),
            (30.0, 'swoosh', 0.9, 0.062, 0.0), (33.5, 'bell', 0.8, 0.035, 0.2),
            (38.0, 'splash', 1.4, 0.050, -0.2), (42.0, 'swoosh', 1.0, 0.060, 0.0),
            (43.3, 'splash', 1.8, 0.065, 0.2),
        ]
    else:
        events = [
            # Opening and illustrated route map.
            (0.6, 'splash', 2.2, 0.068, -0.20), (3.6, 'creak', 1.35, 0.048, 0.25),
            (8.0, 'swoosh', 1.0, 0.062, 0.0), (9.4, 'bell', 1.3, 0.035, -0.25),
            (18.5, 'tap', 0.35, 0.040, 0.20), (27.5, 'bell', 0.9, 0.035, 0.30),
            # Cargo ledger: a soft wooden knock and barrel rattle.
            (34.0, 'swoosh', 0.9, 0.058, 0.0), (35.4, 'knock', 0.7, 0.053, -0.22),
            (40.5, 'rattle', 1.1, 0.044, 0.20), (49.5, 'knock', 0.6, 0.039, 0.22),
            # Close-up of the ship's final log entry.
            (55.8, 'paper', 1.4, 0.063, -0.35), (60.7, 'tap', 0.36, 0.042, 0.18),
            (65.8, 'swoosh', 0.9, 0.055, 0.0),
            # De Gratia discovery and the missing boat.
            (67.0, 'swoosh', 1.2, 0.064, 0.0), (70.0, 'splash', 2.5, 0.057, -0.25),
            (76.0, 'rope', 1.35, 0.055, 0.25), (82.5, 'knock', 0.65, 0.044, -0.15),
            (88.0, 'splash', 2.2, 0.068, 0.25), (100.8, 'swoosh', 1.0, 0.058, 0.0),
            # Investigation table: evidence cards, magnifier, and pump diagram.
            (102.2, 'knock', 0.6, 0.047, 0.0), (104.0, 'paper', 1.35, 0.062, -0.35),
            (111.0, 'tap', 0.38, 0.042, 0.22), (124.5, 'paper', 1.0, 0.052, 0.35),
            (137.2, 'bell', 0.8, 0.032, -0.15), (149.5, 'swoosh', 1.1, 0.057, 0.0),
            # Hypotheses: three distinct, soft transition accents.
            (151.2, 'knock', 0.7, 0.044, -0.25), (172.5, 'swoosh', 0.9, 0.044, 0.2),
            (194.0, 'tap', 0.35, 0.038, -0.2), (215.8, 'swoosh', 1.5, 0.057, 0.0),
            # Quiet closing swell, never masking the final line.
            (219.5, 'creak', 1.1, 0.042, 0.25), (222.0, 'splash', 2.3, 0.052, -0.15),
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
    write_wav(args.out_dir / 'v6-motion-sfx-main.wav', build(args.main_seconds, False))
    write_wav(args.out_dir / 'v6-motion-sfx-short.wav', build(args.short_seconds, True))
    print('Wrote V6 scene-specific motion Foley WAVs.')

if __name__ == '__main__':
    main()
