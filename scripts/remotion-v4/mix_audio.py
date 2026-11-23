#!/usr/bin/env python3
"""Mix original Foley under narration with a voice-keyed ducking envelope."""
from __future__ import annotations
import argparse
import wave
from pathlib import Path
import numpy as np


def read_wav(path: Path):
    with wave.open(str(path), 'rb') as f:
        channels, width, rate, frames = f.getnchannels(), f.getsampwidth(), f.getframerate(), f.getnframes()
        if width != 2:
            raise ValueError(f'{path}: expected 16-bit PCM WAV')
        data = np.frombuffer(f.readframes(frames), dtype='<i2').astype(np.float32) / 32768.0
    return data.reshape(-1, channels), rate


def write_wav(path: Path, samples: np.ndarray, rate: int):
    pcm = (np.clip(samples, -0.999, 0.999) * 32767).astype('<i2')
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), 'wb') as f:
        f.setnchannels(2)
        f.setsampwidth(2)
        f.setframerate(rate)
        f.writeframes(pcm.tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--voice', type=Path, required=True)
    ap.add_argument('--foley', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    args = ap.parse_args()
    voice, vrate = read_wav(args.voice)
    foley, frate = read_wav(args.foley)
    if vrate != frate:
        raise ValueError(f'Sample rate mismatch: narration={vrate}, Foley={frate}')
    if voice.shape[1] == 1:
        voice = np.repeat(voice, 2, axis=1)
    if foley.shape[1] == 1:
        foley = np.repeat(foley, 2, axis=1)
    if voice.shape[1] != 2 or foley.shape[1] != 2:
        raise ValueError('Expected mono or stereo tracks')

    # Match precisely to the designed Foley timeline, padding narration only if needed.
    count = len(foley)
    if len(voice) < count:
        voice = np.pad(voice, ((0, count - len(voice)), (0, 0)))
    else:
        voice = voice[:count]

    # Estimate voice energy in 10 ms blocks. Attack/release smoothing avoids audible pumping.
    block = max(1, round(vrate * 0.010))
    usable = (count // block) * block
    energy = np.sqrt(np.mean(voice[:usable].reshape(-1, block, 2) ** 2, axis=(1, 2)) + 1e-12)
    db = 20 * np.log10(np.maximum(energy, 1e-6))
    activity = 1.0 / (1.0 + np.exp(-np.clip((db + 37.0) / 4.5, -30, 30)))
    target = 0.90 - 0.64 * activity   # effects at 26% under strong speech, up to 90% in pauses
    envelope = np.empty_like(target)
    current = 0.90
    attack = np.exp(-block / (vrate * 0.045))
    release = np.exp(-block / (vrate * 0.42))
    for i, goal in enumerate(target):
        coef = attack if goal < current else release
        current = goal + coef * (current - goal)
        envelope[i] = current
    if usable < count:
        envelope = np.pad(envelope, (0, int(np.ceil((count - usable) / block))), mode='edge')
    gain = np.repeat(envelope, block)[:count, None]

    # Keep the narration unchanged; lower only the Foley if the combined peak needs headroom.
    foley_gain = 1.0
    mixed = voice + foley * gain
    while float(np.max(np.abs(mixed))) > 0.97 and foley_gain > 0.08:
        foley_gain *= 0.85
        mixed = voice + foley * gain * foley_gain
    if float(np.max(np.abs(mixed))) > 0.999:
        voice_gain = 0.97 / max(float(np.max(np.abs(mixed))), 1e-6)
        mixed *= voice_gain
    write_wav(args.output, mixed, vrate)
    print(f'Mixed {count / vrate:.3f}s: speech-keyed Foley ducking, final peak {np.max(np.abs(mixed)):.3f}')


if __name__ == '__main__':
    main()
