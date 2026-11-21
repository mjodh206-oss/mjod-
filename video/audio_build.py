"""Procedural sound design & score.

No sample libraries: every hit, riser, drone and pulse is synthesised, then
mixed under the voiceover with sidechain ducking and a mastered limiter.
"""
import json
import math
import os

import numpy as np
import soundfile as sf
from scipy import signal

SR = 48000
rng = np.random.default_rng(7)


# ------------------------------------------------------------------ utils
def env_ad(n, attack, decay, sr=SR):
    a = max(1, int(attack * sr))
    e = np.ones(n)
    e[:a] = np.linspace(0, 1, a) ** 1.5
    d = np.arange(n - a)
    e[a:] = np.exp(-d / (decay * sr))
    return e


def env_ar(n, attack, release, sr=SR):
    a = max(1, int(attack * sr))
    r = max(1, int(release * sr))
    e = np.ones(n)
    e[:a] = np.linspace(0, 1, a) ** 2
    if r < n:
        e[-r:] = np.linspace(1, 0, r) ** 2
    return e


def bandpass(x, lo, hi, order=2):
    sos = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], btype="band", output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x, f, order=2):
    sos = signal.butter(order, f / (SR / 2), btype="low", output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, f, order=2):
    sos = signal.butter(order, f / (SR / 2), btype="high", output="sos")
    return signal.sosfilt(sos, x)


def stereo(x, pan=0.0):
    """Equal-power pan; pan -1=left, +1=right."""
    a = (pan + 1) * math.pi / 4
    return np.stack([x * math.cos(a), x * math.sin(a)], -1)


def reverb(x, amount=0.35, decay=1.9, predelay=0.02, damp=4200, seed=3):
    """Cheap convolution reverb with a synthetic exponential-noise impulse."""
    r = np.random.default_rng(seed)
    n = int(SR * decay)
    t = np.arange(n) / SR
    ir = r.normal(0, 1, n) * np.exp(-t / (decay / 5.5))
    ir = lowpass(ir, damp)
    ir[: int(predelay * SR)] = 0
    ir /= np.abs(ir).max() + 1e-9
    wet = signal.fftconvolve(x, ir, mode="full")[: len(x)]
    wet /= (np.abs(wet).max() + 1e-9)
    return x * (1 - amount) + wet * amount


def soft_clip(x, drive=1.0):
    return np.tanh(x * drive) / np.tanh(drive)


# ------------------------------------------------------------------ instruments
def boom(dur=1.6, f0=72, f1=34, drive=1.4):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-t * 9)
    ph = 2 * np.pi * np.cumsum(f) / SR
    body = np.sin(ph) * np.exp(-t * 2.4)
    click = bandpass(rng.normal(0, 1, n), 1500, 7000) * np.exp(-t * 90) * 0.5
    noise = lowpass(rng.normal(0, 1, n), 240) * np.exp(-t * 3.2) * 0.5
    x = soft_clip((body + click + noise) * drive, 1.2)
    return x * 0.9


def riser(dur=2.2, f_lo=180, f_hi=3200, shape=2.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    p = (t / dur) ** shape
    f = f_lo + (f_hi - f_lo) * p
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) * 0.5 + np.sin(ph * 1.5) * 0.22
    nz = rng.normal(0, 1, n)
    nz = bandpass(nz, 400, 9000) * 0.5
    block_n = 16
    sweep = np.zeros(n)
    for bi in range(block_n):
        a = int(bi * n / block_n)
        b = int((bi + 1) * n / block_n)
        if b <= a:
            continue
        cut = 300 + 9000 * (bi / block_n) ** 1.6
        sweep[a:b] = lowpass(nz[a:b], min(cut, nyq), 2) * 0.8
    x = (tone * 0.45 + sweep) * (p ** 1.3)
    return x / (np.abs(x).max() + 1e-9)


def impact(dur=2.4, drive=1.5):
    n = int(dur * SR)
    t = np.arange(n) / SR
    sub = boom(1.9, 82, 30, 1.6)
    pad = np.zeros(n)
    sub2 = np.zeros(n)
    sub2[: len(sub)] = sub[:n]
    body = lowpass(rng.normal(0, 1, n), 900) * np.exp(-t * 6) * 0.35
    metal = np.zeros(n)
    for f in (410, 617, 941, 1327, 2111):
        metal += np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) * np.exp(-t * (3.2 + f / 900))
    metal = highpass(metal, 300) * 0.12 * np.exp(-t * 1.0)
    x = sub2 * 1.0 + body + metal
    x = soft_clip(x * 1.3, drive)
    return x / (np.abs(x).max() + 1e-9)


def whoosh(dur=0.9, lo=200, hi=5200, curve=1.6):
    n = int(dur * SR)
    t = np.arange(n) / SR
    p = (t / dur) ** curve
    nz = rng.normal(0, 1, n)
    swept = np.zeros(n)
    blocks = 24
    for i in range(blocks):
        a, b = int(np.floor(i * n / blocks)), int(np.floor((i + 1) * n / blocks))
        if b <= a:
            continue
        tt = i / blocks
        f = lo + (hi - lo) * (tt ** curve)
        swept[a:b] = bandpass(nz[a:b], f * 0.6, min(nyq, f * 1.7))[a:b] if False else nz[a:b]
    # simple sweep via time-varying mix of two filtered versions
    low = lowpass(nz, lo)
    high = bandpass(nz, lo, hi)
    x = low * (1 - p) + high * p
    x *= np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.2
    return x / (np.abs(x).max() + 1e-9)


nyq = SR / 2 - 200


def tick(freq=2400, dur=0.06, decay=70):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * freq * t) * np.exp(-t * decay)
    x += bandpass(rng.normal(0, 1, n), 3000, 9000) * np.exp(-t * 120) * 0.4
    return x


def pulse_note(freq, dur=0.22, bright=0.35):
    n = int(dur * SR)
    t = np.arange(n) / SR
    e = np.exp(-t * 7.5) * (1 - np.exp(-t * 400))
    x = np.sin(2 * np.pi * freq * t) * e
    x += np.sin(2 * np.pi * freq * 2 * t) * e * bright * 0.5
    x += np.sin(2 * np.pi * freq * 3 * t) * e * bright * 0.25
    return x * 0.6


def riser_strings(dur=3.0, notes=(110, 164.8, 220, 277.2), swell=True):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for j, f in enumerate(notes):
        vib = 1 + 0.004 * np.sin(2 * np.pi * (4.6 + j * 0.4) * t)
        ph = 2 * np.pi * np.cumsum(f * vib) / SR
        saw = signal.sawtooth(ph) * 0.5
        x += saw / (j + 1.4)
    x = lowpass(x, 2600)
    x *= (t / dur) ** 2.1
    return x / (np.abs(x).max() + 1e-9)


def drone(dur, root=55, cut=520, motion=True):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for j, m in enumerate((1, 1.5, 2, 3, 4.5)):
        ph = 2 * np.pi * root * m * t
        x += (np.sin(ph) / (j + 1.2)) * (1 + 0.15 * np.sin(2 * np.pi * (0.07 + j * 0.013) * t))
    x = lowpass(x, cut)
    if motion:
        lfo = 1 + 0.28 * np.sin(2 * np.pi * 0.043 * t) + 0.14 * np.sin(2 * np.pi * 0.117 * t + 1.2)
        x *= lfo
    x *= env_ar(n, 1.5, 2.0)
    return x / (np.abs(x).max() + 1e-9)


def heartbeat(dur=1.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    a = boom(0.34, 60, 38, 1.1)
    b = boom(0.30, 55, 34, 1.1)
    x = np.zeros(n)
    x[: min(n, len(a))] += a[:n] * 0.9
    off = int(0.26 * SR)
    if off < n:
        x[off:off + len(b)] += b[: n - off] * 0.6
    return lowpass(x, 260)


def crowd_swell(dur=2.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = rng.normal(0, 1, n)
    x = bandpass(x, 300, 2600)
    x *= np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.6
    return x / (np.abs(x).max() + 1e-9)


def glass_shimmer(dur=1.6, freqs=(1760, 2300, 2950, 3520)):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for f in freqs:
        x += np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) * np.exp(-t * 4.0)
    return highpass(x, 800) / 6


# ------------------------------------------------------------------ music bed
def pad_chord(freqs, dur, cut=2200, attack=0.9, release=1.2, detune=0.004, bright=0.5):
    """Wide detuned saw pad — the emotional bed."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for j, f in enumerate(freqs):
        for det in (-detune, 0.0, detune):
            vib = 1 + 0.0022 * np.sin(2 * np.pi * (3.1 + j) * t + j)
            ph = 2 * np.pi * np.cumsum(f * (1 + det) * vib) / SR
            x += signal.sawtooth(ph) / (len(freqs) * 3)
    x = lowpass(x, min(cut, nyq), 2)
    x = highpass(x, 70)
    x *= env_ar(n, attack, release)
    return x / (np.abs(x).max() + 1e-9)


def pluck(freq, dur=0.7, decay=5.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    e = np.exp(-t * decay) * (1 - np.exp(-t * 600))
    x = (np.sin(2 * np.pi * freq * t) + 0.4 * np.sin(2 * np.pi * freq * 2 * t)
         + 0.16 * np.sin(2 * np.pi * freq * 3 * t))
    return x * e


CHORDS = [
    (110.00, [220.00, 261.63, 329.63]),   # Am
    (87.31,  [174.61, 220.00, 261.63]),   # F
    (130.81, [261.63, 329.63, 392.00]),   # C
    (98.00,  [196.00, 246.94, 293.66]),   # G
]

# part 3 is a different film: minor, slower, more menacing
CHORDS_P3 = [
    (73.42,  [146.83, 174.61, 220.00]),   # Dm
    (58.27,  [116.54, 146.83, 174.61]),   # Bb
    (87.31,  [174.61, 220.00, 261.63]),   # F
    (65.41,  [130.81, 164.81, 196.00]),   # C
]


def score(total, sections, bpm=88, chords=None, sub=1.0):
    """Adaptive bed: pads + drone + pulse + plucks, intensity follows sections."""
    n = int(total * SR) + SR
    chords = chords or CHORDS
    bed = np.zeros((n, 2), np.float32)
    beat = 60.0 / bpm
    bar = beat * 4

    def place(sig, at, gain=1.0, pan=0.0, wide=0.0):
        i = int(at * SR)
        if gain <= 0:
            return
        if i < 0:
            sig = sig[-i:]
            i = 0
        if i >= n:
            return
        s = sig[: n - i]
        if wide > 0:
            l = np.concatenate([s, np.zeros(int(wide * SR))])
            r = np.concatenate([np.zeros(int(wide * SR)), s])
            st = np.stack([l[: len(s)], r[: len(s)]], -1).astype(np.float32)
        else:
            st = stereo(s, pan).astype(np.float32)
        bed[i:i + len(st)] += st * gain

    # --- harmonic pad, chord per bar, brightness follows intensity
    t0 = 0.0
    bar_i = 0
    while t0 < total:
        root, notes = chords[bar_i % 4]
        intensity = 0.45
        for s in sections:
            if s["start"] - 0.2 <= t0 < s["end"]:
                intensity = {"hook": 0.75, "tip": 0.55, "outro": 0.95}[s["kind"]]
                if s.get("idx") == 7:
                    intensity = 1.0
        cut = 900 + 2400 * intensity
        freqs = tuple(notes) + (root * 2,)
        dur = bar + 0.7
        pad = pad_chord(freqs, dur, cut=cut, attack=0.35 if intensity > 0.7 else 0.8,
                        release=0.9, bright=intensity)
        place(pad, t0, 0.30 * (0.7 + 0.5 * intensity), wide=0.012)
        # octave shimmer high up
        sh = pad_chord(tuple(f * 2 for f in notes), dur, cut=5200, attack=0.6,
                       release=1.0)
        place(sh, t0, 0.055 * intensity, wide=0.02)
        t0 += bar
        bar_i += 1

    # --- low drone (root of chord, sub warmth)
    for k in range(int(math.ceil(total / 8.0))):
        seg = min(8.0, total - k * 8.0)
        if seg <= 0.3:
            break
        d = drone(seg + 1.4, root=chords[(k * 2) % 4][0] / 2, cut=300, motion=True)
        place(d, k * 8.0 - 0.7, 0.26, pan=0.0)

    # --- pulse: 8th-note plucks, denser in hotter sections
    for s in sections:
        dens = 0.5 if s["kind"] == "hook" else (1.0 if s["kind"] == "outro" else 2.0)
        if s["kind"] == "tip" and s["idx"] >= 5:
            dens = 2.0
        tt = s["start"]
        k = 0
        while tt < s["end"] - 0.2:
            root, notes = chords[int(tt / bar) % 4]
            strong = (k % 4 == 0)
            if strong:
                place(pulse_note(root, 0.34, 0.22), tt, 0.34, 0.0)
            note = notes[k % 3] * 2
            place(pluck(note, 0.55, 6.0), tt + (beat / 2 if k % 2 else 0), 0.055, 0.35)
            tt += beat * dens
            k += 1

    # --- transitions: riser into each tip, boom at each cut
    for s in sections[1:]:
        place(riser_strings(1.9, notes=(110, 164.8, 220, 277.2, 329.6)), s["start"] - 1.9,
              0.20, wide=0.01)
    return bed[: int(total * SR)]


# ------------------------------------------------------------------ build
def build(path_vo=None, out=None):
    from video.timeline import SECTIONS, TOTAL, IMPACTS, TRUTH, SPEED, EP

    path_vo = path_vo or EP["voice_dir"]
    out = out or (EP["build"] + "/mix.wav")
    os.makedirs(EP["build"], exist_ok=True)
    prefix = EP["prefix"]
    total = TOTAL
    n = int(total * SR) + SR * 2
    vo = np.zeros(n, np.float32)
    for s in SECTIONS:
        fn = os.path.join(path_vo, prefix + "_" + s["vo"].replace("vo_", "") + ".wav")
        if not os.path.exists(fn):
            fn = os.path.join(path_vo, s["vo"] + ".wav")
        a, sr = sf.read(fn)
        if a.ndim > 1:
            a = a.mean(1)
        i = int(s["vo_start"] * SR)
        vo[i:i + len(a)] = a[: n - i]

    sfx = np.zeros(n, np.float32)

    def add(x, at, gain=1.0, pan=0.0):
        i = int(at * SR)
        if i < 0:
            x = x[-i:]
            i = 0
        if i >= n:
            return
        s = x[: n - i]
        st = stereo(s, pan)
        m = st.mean(1) if st.ndim > 1 else st
        sfx[i:i + len(m)] += (m * gain).astype(np.float32)

    add(whoosh(1.0, 120, 4200), 0.06, 0.55, 0.0)
    add(impact(2.6, 1.6), 0.0, 0.85, 0.0)
    for s in SECTIONS:
        add(impact(2.2, 1.35), s["start"] + 0.02, 0.62, 0.0)
        add(whoosh(0.7, 260, 5600), s["start"] - 0.22, 0.28, -0.25)
        add(whoosh(0.75, 260, 5200), s["start"] + 0.02, 0.16, 0.3)
    # number slams
    for s in SECTIONS:
        if s["kind"] == "tip":
            add(boom(1.1, 90, 40, 1.2), s["vo_start"] + 0.12, 0.55, 0.0)
            add(tick(1900, 0.05, 90), s["vo_start"] + 0.12, 0.25, 0.2)
    # truth section — heartbeat + silence drop
    add(heartbeat(0.9), TRUTH["start"] + 0.35, 0.55)
    add(heartbeat(0.9), TRUTH["start"] + 1.45, 0.5)
    add(heartbeat(0.9), TRUTH["vo_start"] - 0.1, 0.6)
    add(riser(2.2, 90, 3000, 2.4), TRUTH["start"] + 1.05, 0.28)
    add(impact(3.0, 1.7), TRUTH["start"] + 0.05, 0.75)
    add(glass_shimmer(1.8), TRUTH["start"] + 0.25, 0.12, 0.3)
    # outro: crowd swell + final hit
    outro = [s for s in SECTIONS if s["kind"] == "outro"][0]
    add(crowd_swell(2.4), outro["vo_end"] - 0.2, 0.14)
    add(impact(3.4, 1.8), outro["vo_end"] + 0.12, 0.55)
    add(riser_strings(2.2, (110, 165, 220, 330)), outro["vo_end"] + 0.05, 0.22)

    sfx /= (np.abs(sfx).max() + 1e-9)
    _mood = EP.get("mood", 1)
    music = score(total, SECTIONS, bpm=80 if _mood == 3 else 88,
                  chords=CHORDS_P3 if _mood == 3 else CHORDS)
    music /= (np.abs(music).max() + 1e-9)

    # -------- ducking (music follows the voice)
    duck_depth = float(EP.get("duck", 0.62))
    win = int(0.02 * SR)
    vseg = np.abs(vo[: len(music)])
    vseg = vseg[: len(vseg) // win * win].reshape(-1, win).max(1)

    env = np.convolve(vseg, np.ones(9) / 9, mode="same")
    env = np.interp(np.arange(len(music)), np.arange(len(vseg)) * win, vseg)
    duck = 1.0 - duck_depth * np.clip(env / (env.max() + 1e-9) * 3.2, 0, 1)
    duck = np.convolve(duck, np.ones(int(0.12 * SR)) / int(0.12 * SR), mode="same")
    sfx = sfx[: len(music)]
    bed = float(EP.get("music", 0.34))      # <- per-episode music level
    mix = music * (bed * duck)[:, None]
    mix[:, 0] += sfx * 0.70
    mix[:, 1] += sfx * 0.70
    # voice: gentle compression + presence EQ + short stereo ambience
    vo_c = vo[: len(music)]
    # simple RMS compressor on the voice bus
    win = 1024
    hop = 256
    padded = np.concatenate([vo_c, np.zeros(win)])
    frames = np.lib.stride_tricks.sliding_window_view(padded, win)[::hop]
    rms = np.sqrt((frames ** 2).mean(1) + 1e-12)
    thresh = np.percentile(rms[rms > 1e-4], 55)
    gain_db = np.where(rms > thresh, -3.5 * (1 - thresh / np.maximum(rms, 1e-9)) * 2.2, 0)
    gain = np.clip(10 ** (gain_db / 20), 0.45, 1.0)
    gain = np.interp(np.arange(len(vo_c)), np.arange(len(gain)) * hop, gain)
    vo_c = vo_c * gain
    vo_c = highpass(vo_c, 82)
    # presence lift
    pres = bandpass(vo_c, 2400, 6200) * 0.16
    vo_c = vo_c + pres
    vo_c = soft_clip(vo_c * 1.05, 1.0)
    # stereo ambience (tiny room) keeps voice centred but alive
    amb = np.stack([reverb(vo_c, 0.055, decay=1.2, damp=5200, seed=11),
                    reverb(vo_c, 0.055, decay=1.2, damp=5200, seed=12)], -1)
    mix += vo_c[:, None] * 1.32 + amb * 0.42

    # master: gentle bus comp + limiter
    peak = np.abs(mix).max()
    mix = mix / (peak + 1e-9) * 1.25
    mix = soft_clip(mix, 1.1)
    mix = highpass(mix.T, 28).T
    mix /= (np.abs(mix).max() + 1e-9) * 1.02
    sf.write(out, mix.astype(np.float32), SR, subtype="PCM_24")
    # stems for inspection
    sf.write(EP["build"] + "/music.wav",
             (music / (np.abs(music).max() + 1e-9) * 0.8).astype(np.float32), SR)
    sf.write(EP["build"] + "/sfx.wav", (sfx * 0.9).astype(np.float32), SR)
    print("wrote", out, "dur", len(mix) / SR)
    return total


def build_voice_only(out=None):
    from video.timeline import SECTIONS, TOTAL, EP
    out = out or (EP["build"] + "/vo_full.wav")
    n = int(TOTAL * SR) + SR * 2
    vo = np.zeros(n, np.float32)
    for s in SECTIONS:
        fn = os.path.join(EP["voice_dir"], EP["prefix"] + "_" + s["vo"].replace("vo_", "") + ".wav")
        if not os.path.exists(fn):
            fn = os.path.join(EP["voice_dir"], s["vo"] + ".wav")
        a, sr = sf.read(fn)
        if a.ndim > 1:
            a = a.mean(1)
        i = int(s["vo_start"] * SR)
        vo[i:i + len(a)] = a[: n - i]
    sf.write(out, vo, SR)
    return vo


if __name__ == "__main__":
    build()
