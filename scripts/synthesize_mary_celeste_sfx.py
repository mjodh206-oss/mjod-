#!/usr/bin/env python3
"""Create original, royalty-free procedural foley beds for the Mary Celeste edits.

This intentionally avoids downloaded samples. It synthesizes waves, wind, hull/rope
creaks, paper rustles, marker sweeps and a quiet stamp tap, then writes stereo WAV.
Requires NumPy only; the rendered video mixes these beds beneath the selected narration.
"""
from __future__ import annotations
import argparse
from pathlib import Path
import wave
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
FS = 24000
RNG = np.random.default_rng(1872)


def shaped_noise(n: int, kind: str, rng=RNG):
    x = rng.standard_normal(n).astype(np.float32)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / FS)
    if kind == "sea":
        hp = 1.0 - np.exp(-(f / 55.0) ** 2)
        lp = np.exp(-(f / 1800.0) ** 2)
        response = hp * lp
    elif kind == "wind":
        hp = 1.0 - np.exp(-(f / 24.0) ** 2)
        response = hp * np.exp(-(f / 520.0) ** 2)
    elif kind == "rustle":
        response = (1.0 - np.exp(-(f / 700.0) ** 2)) * np.exp(-(f / 5200.0) ** 2)
    elif kind == "swoosh":
        response = (1.0 - np.exp(-(f / 90.0) ** 2)) * np.exp(-(f / 3500.0) ** 2)
    else:
        response = np.ones_like(f)
    y = np.fft.irfft(X * response, n=n).astype(np.float32)
    y /= max(float(np.sqrt(np.mean(y * y))), 1e-5)
    return y


def smooth_gate(t, start, end, fade=1.2):
    env = np.zeros_like(t, dtype=np.float32)
    inside = (t >= start) & (t < end)
    env[inside] = 1.0
    a = (t >= start) & (t < start + fade)
    env[a] *= np.sin((t[a] - start) / fade * np.pi / 2) ** 2
    b = (t >= end - fade) & (t < end)
    env[b] *= np.sin((end - t[b]) / fade * np.pi / 2) ** 2
    return env


def add_pan(track, event, start_s, pan=0.0, gain=1.0):
    start = max(0, int(start_s * FS))
    stop = min(len(track), start + len(event))
    if stop <= start:
        return
    e = event[:stop - start] * gain
    left = np.sqrt((1.0 - pan) * 0.5)
    right = np.sqrt((1.0 + pan) * 0.5)
    track[start:stop, 0] += e * left
    track[start:stop, 1] += e * right


def make_event(kind: str, seconds: float, rng=RNG):
    n = int(seconds * FS)
    t = np.arange(n, dtype=np.float32) / FS
    if kind == "creak":
        f0 = rng.uniform(88, 145); f1 = rng.uniform(175, 310)
        phase = 2 * np.pi * (f0 * t + (f1 - f0) * t * t / (2 * seconds))
        tone = np.sin(phase) + 0.32 * np.sin(phase * 1.97 + 0.4)
        grit = shaped_noise(n, "rustle", rng)
        raw = 0.76 * tone + 0.24 * grit
        env = np.sin(np.pi * np.clip(t / seconds, 0, 1)) ** 1.7
        env *= 0.65 + 0.35 * np.sin(2 * np.pi * 2.2 * t) ** 2
    elif kind == "tap":
        raw = np.sin(2 * np.pi * 620 * t) + 0.35 * np.sin(2 * np.pi * 980 * t)
        env = np.exp(-t * 12.0)
    elif kind == "knock":
        f0 = rng.uniform(76, 112)
        raw = np.sin(2 * np.pi * f0 * t) + .28 * np.sin(2 * np.pi * f0 * 2.6 * t)
        raw += .12 * shaped_noise(n, "rustle", rng)
        env = np.exp(-t * 8.5) * np.clip(t / .012, 0, 1)
    elif kind == "bell":
        f0 = rng.uniform(560, 760)
        raw = np.sin(2 * np.pi * f0 * t) + .42 * np.sin(2 * np.pi * f0 * 2.71 * t + .2)
        raw += .16 * np.sin(2 * np.pi * f0 * 4.08 * t + .7)
        env = np.exp(-t * 3.4) * np.clip(t / .008, 0, 1)
    elif kind == "rattle":
        raw = shaped_noise(n, "rustle", rng)
        env = (.45 + .55 * np.sin(2 * np.pi * 15 * t) ** 8) * np.exp(-t * 2.4)
    elif kind in ("paper", "swoosh", "splash"):
        noise_kind = "rustle" if kind == "paper" else ("sea" if kind == "splash" else "swoosh")
        raw = shaped_noise(n, noise_kind, rng)
        if kind == "paper":
            env = np.minimum(1, t / 0.035) * np.exp(-t * 3.8)
            env *= 0.7 + 0.3 * np.sin(2 * np.pi * 11 * t) ** 2
        elif kind == "splash":
            env = np.sin(np.pi * np.clip(t / seconds, 0, 1)) ** 1.2
            env *= 0.45 + 0.55 * np.sin(2 * np.pi * 1.7 * t) ** 2
        else:
            env = np.sin(np.pi * np.clip(t / seconds, 0, 1)) ** 1.5
    else:  # rope squeak
        raw = np.sin(2 * np.pi * (110 * t + 36 * t * t)) + 0.2 * shaped_noise(n, "wind", rng)
        env = np.sin(np.pi * np.clip(t / seconds, 0, 1)) ** 2
    raw = raw * env
    raw /= max(float(np.max(np.abs(raw))), 1e-5)
    return raw.astype(np.float32)


def build(duration: float, short=False):
    n = round(duration * FS)
    track = np.zeros((n, 2), dtype=np.float32)
    t = np.arange(n, dtype=np.float32) / FS
    sea = shaped_noise(n, "sea")
    # Continuous low ocean bed with slow swells, kept well below narration.
    swell = 0.48 + 0.30 * (0.5 + 0.5 * np.sin(2 * np.pi * t / (6.7 if short else 8.4)))
    swell *= 0.84 + 0.16 * np.sin(2 * np.pi * t / 15.0 + 1.3)
    sea *= swell * (0.033 if not short else 0.038)
    track[:, 0] += sea
    track[:, 1] += np.roll(sea, int(FS * 0.021)) * 0.96
    wind = shaped_noise(n, "wind")
    if short:
        wind_env = smooth_gate(t, 0, duration, fade=2.0) * 0.45
    else:
        wind_env = np.zeros_like(t)
        sections = [(0,34.1,.50),(34.1,97.8,.23),(97.8,158.97,.50),
                    (158.97,211.2,.13),(211.2,271.79,.62),(271.79,duration,.20)]
        for a,b,v in sections:
            wind_env += smooth_gate(t,a,b,fade=2.2) * v
    wind *= wind_env * 0.027
    track[:, 0] += wind
    track[:, 1] += np.roll(wind, int(FS * 0.037)) * 0.91

    if short:
        events = [(0.5,"splash",2.4,.12,-.2),(5.0,"creak",1.5,.11,.3),
                  (14.8,"swoosh",1.1,.10,0.0),(25.5,"paper",1.2,.10,-.4),
                  (34.0,"rope",1.4,.10,.4),(39.0,"splash",2.0,.11,0.0)]
    else:
        events = [
            (1.0,"splash",2.8,.12,-.25),(5.5,"creak",1.7,.10,.35),(19.0,"rope",1.5,.09,-.3),
            (30.0,"splash",2.5,.10,.25),(35.0,"paper",1.0,.08,-.4),(53.0,"swoosh",1.0,.07,.3),
            (61.5,"paper",1.2,.07,-.4),(72.0,"splash",3.0,.11,.2),
            (99.0,"splash",3.0,.13,-.25),(104.0,"creak",1.8,.12,.35),(121.0,"rope",1.7,.10,-.3),
            (138.0,"splash",2.4,.11,.3),(151.0,"creak",1.5,.09,0),
            (160.0,"paper",1.3,.12,-.4),(177.0,"paper",1.1,.09,.25),(194.0,"tap",.65,.12,-.2),(204.0,"paper",1.0,.08,.4),
            (212.0,"swoosh",2.2,.13,0),(220.0,"creak",1.7,.12,.3),(232.0,"splash",3.4,.14,-.25),
            (246.0,"rope",1.9,.10,.35),(262.0,"swoosh",2.0,.12,-.3),
            (273.0,"splash",3.5,.10,0),(289.0,"creak",1.4,.07,.25),
        ]
    for when,kind,secs,gain,pan in events:
        if when < duration:
            add_pan(track,make_event(kind,secs),when,pan,gain)
    # Gentle global fade and avoid digital overs.
    fade = np.ones(n,dtype=np.float32)
    fade_n = min(int(FS*2.0),n//2)
    fade[:fade_n] = np.linspace(0,1,fade_n,dtype=np.float32)
    fade[-fade_n:] = np.linspace(1,0,fade_n,dtype=np.float32)
    track *= fade[:,None]
    peak=max(float(np.max(np.abs(track))),1e-5)
    if peak > 0.55: track *= 0.55/peak
    return np.clip(track,-0.99,0.99)


def write_wav(path: Path, samples):
    pcm=(samples*32767).astype('<i2')
    path.parent.mkdir(parents=True,exist_ok=True)
    with wave.open(str(path),'wb') as wf:
        wf.setnchannels(2); wf.setsampwidth(2); wf.setframerate(FS)
        wf.writeframes(pcm.tobytes())


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--main-seconds',type=float,default=302.87)
    ap.add_argument('--short-seconds',type=float,default=41.92)
    ap.add_argument('--out-dir',default=str(ROOT/'audio/episode-01'))
    args=ap.parse_args(); d=Path(args.out_dir)
    write_wav(d/'v2-sfx-main.wav',build(args.main_seconds,False))
    write_wav(d/'v2-sfx-short.wav',build(args.short_seconds,True))
    print('Wrote original procedural foley WAVs.')

if __name__=='__main__': main()
