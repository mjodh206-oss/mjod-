#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Builds the voice-over timeline + full sound design for the kinetic-type Short.
  * VO: paced, EQ'd, de-essed, compressed (ffmpeg), then placed line by line
  * Music: synthesized 92 BPM dark-cinematic bed (kick / clap / hats / sub / pad)
  * SFX: impacts, whooshes, word ticks, riser, tape-stop
  * Mix: sidechain ducking of music under voice, soft-clip, -14 LUFS on mux
Writes build/kt/timeline.json (used by the renderer) + build/kt/mix.wav
"""
import json, os, wave, subprocess, sys
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
KT = os.path.join(ROOT, "build", "kt")
SR = 48000
FF = "/usr/local/lib/python3.11/dist-packages/imageio_ffmpeg/binaries/ffmpeg-linux-x86_64-v9.0.2"

TEXTS = [
    "في قاعدة واحدة… ومحدش بيقولها لك.",
    "الفرق مش في اللي بتكسبه. الفرق في اللي بتشتريه باللي كسبته.",
    "كل جنيه بتصرفه… يا بيشتغل معاك، يا إنت بتشتغل عشانه.",
    "اللي بيشتري منظر… بيشتري حاجة بتخلص، وبيقسّط عليها سنين.",
    "واللي بيشتري أصل… بيشتري حاجة بتفضل تجيب، وإنت نايم.",
    "عشان كده ناس دخلها ضعف دخل غيرها… وعايشة أضعف منهم.",
    "القاعدة: اشتري اللي يفضل يجيب، ما تشتريش اللي بياخد.",
    "اكتب في الكومنت أول أصل هتشتريه… واحفظ الفيديو.",
]
# per-line: tempo, lead-in, tail
PACE = [(1.02, 0.45, 0.55), (1.05, 0.30, 0.45), (1.05, 0.28, 0.40), (1.05, 0.28, 0.45),
        (1.04, 0.28, 0.45), (1.04, 0.30, 0.45), (0.98, 0.40, 0.75), (1.02, 0.55, 1.85)]

def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode:
        print("FAILED:", " ".join(cmd)[:300]); print(r.stderr[-1500:]); sys.exit(1)

def rd(path):
    w = wave.open(path)
    a = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
    return a

def speak_bounds(a):
    hop = int(SR * 0.01)
    env = np.abs(a[: len(a)//hop*hop].reshape(-1, hop)).max(axis=1)
    idx = np.where(env > 0.02)[0]
    if not len(idx): return 0.0, len(a)/SR
    return max(0, (idx[0]-3)*0.01), min(len(a)/SR, (idx[-1]+4)*0.01)

# ------------------------------------------------------------------ VO
voices = []
for i, (txt, (tempo, lead, tail)) in enumerate(zip(TEXTS, PACE), 1):
    src = os.path.join(KT, "vo", f"k{i}.mp3")
    dst = os.path.join(KT, f"v{i}.wav")
    chain = (
        f"atempo={tempo},highpass=f=95,"
        "equalizer=f=220:t=q:w=1.2:g=1.5,"      # body
        "equalizer=f=3200:t=q:w=1.6:g=2.2,"     # presence
        "deesser=i=0.45:m=0.6,"
        "acompressor=threshold=-19dB:ratio=3.2:attack=5:release=170:makeup=2,"
        "alimiter=limit=0.95,volume=0.94"
    )
    run([FF, "-hide_banner", "-loglevel", "error", "-y", "-i", src,
         "-ac", "1", "-ar", str(SR), "-af", chain, dst])
    a = rd(dst)
    s0, s1 = speak_bounds(a)
    voices.append(dict(a=a, s0=s0, s1=s1, lead=lead, tail=tail))
    print(f"k{i}: {len(a)/SR:5.2f}s  speech {s1-s0:5.2f}s")

# ------------------------------------------------------------------ timeline
lines, cursor, total = [], 0.0, 0.0
for i, (txt, v) in enumerate(zip(TEXTS, voices), 1):
    start = cursor + v["lead"]
    seg = v["a"][int(v["s0"]*SR): int(v["s1"]*SR)]
    dur = len(seg)/SR
    # word timings weighted by character count
    words = txt.replace("…", " … ").split()
    weights = np.array([max(1, len(w)) for w in words], dtype=float)
    weights /= weights.sum()
    acc, ws = 0.0, []
    for w, wt in zip(words, weights):
        ws.append(dict(w=w, t=round(start + acc*dur, 3), d=round(wt*dur, 3)))
        acc += wt
    end = start + dur
    lines.append(dict(i=i, text=txt, start=round(start, 3), end=round(end, 3),
                      lead=round(v["lead"], 2), words=ws))
    cursor = end + v["tail"]
    total = max(total, end)
TOTAL = round(total + 1.2, 2)
print("TOTAL", TOTAL, "s")

n = int(SR * TOTAL)
T = np.arange(n) / SR

# ------------------------------------------------------------------ music bed (92 BPM)
BPM = 92.0
BEAT = 60.0 / BPM
BAR = BEAT * 4
bed = np.zeros(n)

def env_ad(n_, a, d, curve=4.0):
    e = np.ones(n_)
    na, nd = int(a*SR), int(d*SR)
    if na: e[:na] = np.linspace(0, 1, na)
    if nd and nd < n_: e[-nd:] *= np.exp(-np.linspace(0, curve, nd))
    return e

def add(buf, t0, sig):
    i0 = int(t0*SR)
    if i0 < 0:
        sig = sig[-i0:]
        i0 = 0
    if i0 >= n or len(sig) == 0: return
    ln = min(len(sig), n - i0)
    buf[i0:i0+ln] += sig[:ln]

def kick(dur=0.42, f0=58, f1=38, gain=0.9):
    m = int(dur*SR); tt = np.arange(m)/SR
    f = f1 + (f0-f1)*np.exp(-tt*22)
    ph = 2*np.pi*np.cumsum(f)/SR
    body = np.sin(ph)*np.exp(-tt*7.5)
    click = np.random.default_rng(1).standard_normal(m)*np.exp(-tt*380)*0.25
    return gain*(body+click)

def clap(dur=0.26, gain=0.42):
    rng = np.random.default_rng(7); m = int(dur*SR); tt = np.arange(m)/SR
    nz = rng.standard_normal(m)
    k = np.hanning(9); nz = np.convolve(nz, k/np.sum(k), mode="same")
    nz -= np.convolve(nz, np.ones(40)/40, mode="same")
    return gain*nz*np.exp(-tt*26)

def hat(dur=0.055, gain=0.13):
    rng = np.random.default_rng(3); m = int(dur*SR); tt = np.arange(m)/SR
    nz = rng.standard_normal(m); nz -= np.convolve(nz, np.ones(9)/9, mode="same")
    return gain*nz*np.exp(-tt*85)

def sub(freq, dur, gain=0.30):
    m = int(dur*SR); tt = np.arange(m)/SR
    return gain*np.sin(2*np.pi*freq*tt)*np.exp(-tt*2.2)*env_ad(m, 0.012, 0.05, 1.0)

# arrangement: intro (no drums) for line 1, full beat from line 2, drop beat on the rule (line 7)
FULL_FROM = lines[1]["start"] - 0.15
DROP_AT = lines[6]["start"] - 0.12
RETURN_AT = lines[6]["start"] + 1.9
bars = int(TOTAL/BAR) + 2
for b in range(bars):
    t_bar = b*BAR
    drums_on = (t_bar >= FULL_FROM) and not (DROP_AT <= t_bar < RETURN_AT)
    # kick
    if drums_on:
        for bt in (0, 2):
            add(bed, t_bar + bt*BEAT, kick())
        # syncopated ghost kick
        add(bed, t_bar + 2.75*BEAT, kick(0.3, 52, 36, 0.45))
    else:
        add(bed, t_bar, kick(0.5, 48, 30, 0.42))
    # clap
    if drums_on:
        for bt in (1, 3):
            add(bed, t_bar + bt*BEAT, clap())
    # hats
    if drums_on:
        for k16 in range(16):
            tt = t_bar + k16*(BEAT/4)
            if k16 % 4 == 2: add(bed, tt, hat(0.05, 0.16))
            else: add(bed, tt, hat(0.04, 0.055))
    # sub bass pattern (A1 / F1 / G1)
    root = [55.0, 43.65, 49.0, 55.0][b % 4]
    for k8 in range(8):
        if (k8 % 2 == 0) or b % 2 == 1:
            add(bed, t_bar + k8*(BEAT/2), sub(root, BEAT*0.45, 0.21 if drums_on else 0.14))

# pad (soft sustained fifths, low level)
for b in range(bars):
    t_bar = b*BAR
    root = [55.0, 43.65, 49.0, 55.0][b % 4]
    m = int(BAR*SR); tt = np.arange(m)/SR
    pad = sum(np.sin(2*np.pi*root*r*tt + i) * g for i,(r,g) in enumerate([(2,0.05),(3,0.035),(4,0.022)]))
    pad *= np.minimum(1, tt/0.5) * np.minimum(1, (BAR-tt)/0.6)
    add(bed, t_bar, pad)

# ------------------------------------------------------------------ SFX
def whoosh(dur=0.55, gain=0.30, up=True):
    rng = np.random.default_rng(11); m = int(dur*SR); tt = np.arange(m)/SR
    nz = rng.standard_normal(m)
    nz -= np.convolve(nz, np.ones(30)/30, mode="same")
    sweep = np.linspace(0.18, 1.0, m) if up else np.linspace(1.0, 0.18, m)
    e = np.sin(np.linspace(0, np.pi, m))**1.6
    return gain*nz*sweep*e

def impact(gain=0.85):
    m = int(1.1*SR); tt = np.arange(m)/SR
    body = np.sin(2*np.pi*(38+30*np.exp(-tt*18))*tt)*np.exp(-tt*5.5)
    tr = np.random.default_rng(13).standard_normal(m)*np.exp(-tt*90)*0.35
    return gain*(body+tr)

def tick(freq=2100, gain=0.10):
    m = int(0.07*SR); tt = np.arange(m)/SR
    return gain*(np.sin(2*np.pi*freq*tt)*np.exp(-tt*90) +
                 0.4*np.random.default_rng(17).standard_normal(m)*np.exp(-tt*300))

def riser(dur=1.5, gain=0.32):
    m = int(dur*SR); tt = np.arange(m)/SR
    nz = np.random.default_rng(19).standard_normal(m)
    nz -= np.convolve(nz, np.ones(24)/24, mode="same")
    amp = (tt/dur)**2.2
    tone = np.sin(2*np.pi*(220 + 900*(tt/dur)**1.5)*tt)*0.35
    return gain*(nz*0.9 + tone)*amp

sfx = np.zeros(n)
for ln in lines:
    add(sfx, ln["start"] - ln["lead"] - 0.05, whoosh(0.5, 0.26))
for idx in (0, 6, 7):
    add(sfx, lines[idx]["start"] - 0.28, impact(0.9 if idx == 6 else 0.7))
# word ticks (skip punctuation)
for ln in lines:
    for k, w in enumerate(ln["words"]):
        if w["w"] in ("…", "،", ":", "."): continue
        add(sfx, w["t"] - 0.01, tick(1900 + (k % 4)*260, 0.055 if ln["i"] > 1 else 0.075))
# riser into the rule
add(sfx, lines[6]["start"] - 1.45, riser(1.45, 0.34))
# confirm pop on CTA
add(sfx, lines[7]["start"] + 0.15, tick(1250, 0.16))

# ------------------------------------------------------------------ voice track
voice = np.zeros(n)
for ln, v in zip(lines, voices):
    seg = v["a"][int(v["s0"]*SR): int(v["s1"]*SR)].copy()
    seg /= (np.max(np.abs(seg)) + 1e-9)
    i0 = int(ln["start"]*SR); ln_ = min(len(seg), n-i0)
    # 8 ms fade edges to avoid clicks
    f = int(0.008*SR)
    seg[:f] *= np.linspace(0, 1, f); seg[-f:] *= np.linspace(1, 0, f)
    voice[i0:i0+ln_] += seg[:ln_]*1.0

# ------------------------------------------------------------------ duck music under voice
hop = 480
env = np.array([np.max(np.abs(voice[i:i+hop])) for i in range(0, n, hop)])
env = np.convolve(env, np.ones(9)/9, mode="same")
gate = np.clip((env - 0.02) / 0.09, 0, 1)
gate = np.repeat(gate, hop)[:n]
if len(gate) < n: gate = np.pad(gate, (0, n-len(gate)), mode="edge")
duck = 1.0 - 0.56*gate
music = bed * 0.40 * duck
fx = sfx * 0.82

mix = voice + music + fx
# tape-stop-ish tail on the music at the very end
tail_n = int(0.9*SR)
mix[-tail_n:] *= np.linspace(1, 0, tail_n)**1.2

peak = np.max(np.abs(mix))
if peak > 0.98:
    mix = np.tanh(mix*1.15)/1.15
mix = np.clip(mix, -1, 1)
st = np.stack([mix, mix*0.985], axis=1)

with wave.open(os.path.join(KT, "mix.wav"), "w") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((st*32767).astype(np.int16).tobytes())

json.dump(dict(total=TOTAL, bpm=BPM, lines=lines), open(os.path.join(KT, "timeline.json"), "w"),
          ensure_ascii=False, indent=1)
print("mix ok", round(len(mix)/SR, 2), "s  peak", round(float(np.max(np.abs(mix))), 3))
