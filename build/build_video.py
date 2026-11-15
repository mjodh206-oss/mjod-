#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Vertical (1080x1920) YouTube-Shorts renderer.
Builds: per-scene Ken-Burns clips -> xfade timeline -> film grade -> Arabic ASS
typography (libass/HarfBuzz) -> synthesized cinematic bed + voice-over mix.

Everything is scripted so timings/text can be iterated in seconds.
"""
import json, os, subprocess, wave, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

FF = "/usr/local/lib/python3.11/dist-packages/imageio_ffmpeg/binaries/ffmpeg-linux-x86_64-v9.0.2"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
B = os.path.join(ROOT, "build")
FONTS = os.path.join(ROOT, "assets", "fonts")
OUT = os.path.join(ROOT, "output")
for d in (B, OUT, os.path.join(B, "clips"), os.path.join(B, "wav")):
    os.makedirs(d, exist_ok=True)

W, H, FPS = 1080, 1920, 30
XF = 0.30          # cross-fade duration
ATEMPO = 1.14      # voice pacing (shorts energy)
LEAD = 0.26        # silence before a line starts inside its scene
TAIL = 0.38        # breathing room after a line

GOLD = "&H4AB4E8&"      # #E8B44A in ASS BGR
WHITE = "&HFFFFFF&"
GREY = "&H00C8C8C8&"
OUTLINE = "&H00120E0C&"

# --------------------------------------------------------------------------- #
# 1. SCRIPT  (Egyptian Arabic)
# --------------------------------------------------------------------------- #
def g(txt):  # gold word inside a white line
    return "{\\c" + GOLD + "}" + txt + "{\\c" + WHITE + "}"


SCENES = [
    dict(
        img="scene1_hook.jpg", kicker=None, cy=700, lead=0.12,
        beats=[
            dict(lines=[("الغني بيشتري", 96, WHITE, 0), ("حاجات " + g("رخيصة"), 152, WHITE, 0)], w=22),
            dict(lines=[("والفقير بيشتري", 96, WHITE, 0), ("حاجات " + g("غالية"), 152, WHITE, 0)], w=24),
        ],
    ),
    dict(
        img="scene2_asset.jpg", kicker="السؤال الوحيد", cy=760,
        beats=[
            dict(lines=[("ومش قصدي إنه بخيل", 108, WHITE, 0)], w=16),
            dict(lines=[("قبل ما يدفع…", 88, GREY, 0), ("سؤال واحد", 140, GOLD, 0)], w=18),
            dict(lines=[("هترجّعلي", 88, GREY, 0), ("فلوس؟", 156, GOLD, 0)], w=19),
            dict(lines=[("ولا هتاخد", 88, GREY, 0), ("مني فلوس؟", 130, WHITE, 0)], w=23),
        ],
    ),
    dict(
        img="scene3_car.jpg", kicker="العربية", cy=585,
        beats=[
            dict(lines=[("الفقير", 132, GREY, 0), ("بيشتري منظر", 104, WHITE, 0)], w=19),
            dict(lines=[("الغني", 132, GOLD, 0), ("بيشتري أصل", 104, WHITE, 0)], w=21),
        ],
    ),
    dict(
        img="scene4_phone.jpg", kicker="التقسيط", cy=570,
        beats=[
            dict(lines=[("24", 250, GOLD, 0), ("شهر تقسيط", 100, WHITE, 0)], w=16),
            dict(lines=[("ده مش غنى", 150, WHITE, 0)], w=13),
            dict(lines=[("ده إيجار", 120, WHITE, 0), ("على المنظر", 136, GOLD, 0)], w=16),
            dict(lines=[("بتدفع كل شهر", 94, WHITE, 0), ("عشان تصدّق حاجة مش حقيقية", 74, GREY, 0)], w=30),
        ],
    ),
    dict(
        img="scene5_growth.jpg", kicker="الأصل", cy=540,
        beats=[
            dict(lines=[("شكل الغنى", 96, GREY, 0), ("بيختفي", 150, WHITE, 0)], w=18),
            dict(lines=[("الأصل", 150, GOLD, 0), ("بيشتغل وإنت نايم", 96, WHITE, 0)], w=24),
        ],
    ),
    dict(
        img="scene6_cta.jpg", kicker="القاعدة", cy=545, tail=1.3,
        beats=[
            dict(lines=[("اشتري اللي", 92, GREY, 0), ("بيجيب فلوس", 148, GOLD, 0)], w=18),
            dict(lines=[("مش اللي", 92, GREY, 0), ("بياخد فلوس", 138, WHITE, 0)], w=16),
            dict(lines=[("اكتب في الكومنت", 74, GREY, 0), ("أول أصل هتشتريه", 100, GOLD, 0)], w=20),
            dict(lines=[("واحفظ الفيديو", 112, WHITE, 10)], w=16),
        ],
    ),
]

# --------------------------------------------------------------------------- #
# 2. AUDIO: pace the voice-over, measure, build the timeline
# --------------------------------------------------------------------------- #
def run(cmd, quiet=True):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print("CMD FAILED:", " ".join(cmd)[:400])
        print(r.stderr[-3000:])
        sys.exit(1)
    return r


def read_wav(path):
    w = wave.open(path)
    a = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
    return a, w.getframerate()


def speech_len(a, sr):
    hop = int(sr * 0.01)
    env = np.abs(a[: len(a) // hop * hop].reshape(-1, hop)).max(axis=1)
    idx = np.where(env > 0.02)[0]
    return (idx[-1] + 8) * 0.01 - max(0, (idx[0] - 5)) * 0.01


voices = []
for i in range(1, len(SCENES) + 1):
    src = os.path.join(B, "vo", f"s{i}.mp3")
    dst = os.path.join(B, "wav", f"p{i}.wav")
    run([FF, "-hide_banner", "-loglevel", "error", "-y", "-i", src,
         "-af", f"atempo={ATEMPO},highpass=f=85,deesser=i=0.4,acompressor=threshold=-18dB:ratio=2.5:attack=6:release=180",
         "-ar", "48000", "-ac", "1", dst])
    a, sr = read_wav(dst)
    voices.append(dict(a=a, sr=sr, dur=len(a) / sr, speech=min(len(a) / sr, speech_len(a, sr))))

durs = [LEAD + v["speech"] + 0.18 + (s.get("tail", TAIL)) for v, s in zip(voices, SCENES)]
# first scene: tighter, hook must land instantly
durs[0] = 0.14 + voices[0]["speech"] + 0.22

starts = []      # absolute start of each clip on the timeline
t = 0.0
for d in durs:
    starts.append(t)
    t += d - XF
TOTAL = starts[-1] + durs[-1]
print("scene durations:", [round(x, 2) for x in durs])
print("total (before tail pad):", round(TOTAL, 2))

# voice start per scene (after its cross-fade settles)
vstart = []
for i, s in enumerate(SCENES):
    lead = s.get("lead", LEAD)
    vstart.append(starts[i] + (XF if i else 0) + lead)
TOTAL = max(TOTAL, vstart[-1] + voices[-1]["speech"] + 1.1)

# --------------------------------------------------------------------------- #
# 3. ASS subtitles
# --------------------------------------------------------------------------- #
def ts(x):
    h = int(x // 3600); m = int(x % 3600 // 60); s = x % 60
    return f"{h}:{m:02d}:{s:05.2f}"


ass = ["""﻿[Script Info]
ScriptType: v4.00+
PlayResX: 1080
PlayResY: 1920
WrapStyle: 2
ScaledBorderAndShadow: yes
YCbCr Matrix: TV.709

[V4+ Styles]
Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding
Style: Base,Cairo Black,96,&H00FFFFFF,&H00FFFFFF,&H00120E0C,&H96000000,0,0,0,0,100,100,0,0,1,8,6,5,50,50,0,1
Style: Kick,Almarai,44,&H004AB4E8,&H004AB4E8,&H00120E0C,&H96000000,0,0,0,0,100,100,6,0,1,3,2,5,50,50,0,1

[Events]
Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text"""]
events = []

events = []
for i, sc in enumerate(SCENES):
    v0 = vstart[i]
    span = voices[i]["speech"] * 0.96
    weights = [b.get("w", 20) for b in sc["beats"]]
    tot = sum(weights)
    acc, bounds = 0.0, []
    for w_ in weights:
        bounds.append((acc / tot, (acc + w_) / tot)); acc += w_
    b0s = [max(0.0, v0 - 0.10) + f0 * span for f0, f1 in bounds]
    b1s = b0s[1:] + [v0 + span + 0.62]
    k0 = max(0.0, v0 - 0.40)
    if sc.get("kicker"):
        events.append((k0, b1s[-1],
                       f"{{\\an5\\pos(540,292)\\fad(220,240)\\fs50\\fnAlmarai ExtraBold\\c{GOLD}\\bord6\\shad3\\spacing9}}{sc['kicker']}"))
        events.append((k0, b1s[-1],
                       f"{{\\an5\\pos(540,346)\\fad(220,240)\\p1\\c{GOLD}\\bord0\\shad0\\blur0.7}}m 0 0 l 90 0 l 90 3 l 0 3"))
    for bi, be in enumerate(sc["beats"]):
        b0, b1 = b0s[bi], b1s[bi]
        lines = be["lines"]
        gaps = [l[1] * 1.26 for l in lines]
        y = sc["cy"] - sum(gaps) / 2
        for li, (txt, size, color, dy) in enumerate(lines):
            yc = int(y + gaps[li] / 2 + dy)
            y += gaps[li]
            events.append((b0, b1,
                f"{{\\an5\\pos(540,{yc})\\fad(90,110)\\fs{size}\\fnCairo Black\\c{color}"
                f"\\bord{max(4, int(size*0.052))}\\shad{max(4, int(size*0.055))}"
                f"\\fscx95\\fscy95\\t(0,170,\\fscx100\\fscy100)}}{txt}"))

for b0, b1, txt in events:
    ass.append(f"Dialogue: 0,{ts(max(0,b0))},{ts(b1)},Base,,0,0,0,,{txt}")

ass_path = os.path.join(B, "subs.ass")
open(ass_path, "w", encoding="utf-8").write("\n".join(ass) + "\n")

# --------------------------------------------------------------------------- #
# 4. Legibility scrim (soft radial darkening where type lives)
# --------------------------------------------------------------------------- #
scrim = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(scrim)
d.ellipse([540 - 700, 660 - 560, 540 + 700, 660 + 560], fill=(0, 0, 0, 138))
scrim = scrim.filter(ImageFilter.GaussianBlur(150))
scrim.save(os.path.join(B, "scrim.png"))

top = Image.new("RGBA", (W, H), (0, 0, 0, 0))
td = ImageDraw.Draw(top)
for yy in range(0, 470):
    a = int(150 * (1 - yy / 470) ** 1.6)
    td.rectangle([0, yy, W, yy + 1], fill=(0, 0, 0, a))
top.save(os.path.join(B, "topshade.png"))

# --------------------------------------------------------------------------- #
# 5. Per-scene Ken-Burns clips
# --------------------------------------------------------------------------- #
moves = [  # (z0, z1, x_expr, y_expr)
    ("punch", 1.10, "iw/2-(iw/zoom/2)", "ih*0.30"),
    (1.12, 1.03, "iw/2-(iw/zoom/2)", "ih/2-(ih/zoom/2)"),
    (1.02, 1.13, "iw*0.52-(iw/zoom/2)", "ih*0.55"),
    (1.10, 1.01, "iw/2-(iw/zoom/2)", "ih*0.40"),
    (1.01, 1.12, "iw/2-(iw/zoom/2)", "ih*0.62"),
    (1.08, 1.00, "iw/2-(iw/zoom/2)", "ih/2-(ih/zoom/2)"),
]
clip_paths = []
for i, sc in enumerate(SCENES):
    d_ = durs[i] + 0.05
    z0, z1, xe, ye = moves[i]
    frames = int(round(d_ * FPS))
    if z0 == "punch":
        zexpr = f"if(lt(on\,11)\,1.13-0.012*on\,1.0+0.085*(on-11)/{max(1,frames-11)})"
    else:
        zexpr = f"{z0}+({z1}-{z0})*on/{max(1,frames-1)}"
    vf = (
        f"scale=1890:3360:flags=lanczos,"
        f"zoompan=z='{zexpr}':x='{xe}':y='{ye}':d=1:s=1080x1920:fps={FPS},"
        f"setsar=1,format=yuv420p"
    )
    out = os.path.join(B, "clips", f"c{i+1}.mp4")
    run([FF, "-hide_banner", "-loglevel", "error", "-y", "-loop", "1", "-framerate", str(FPS),
         "-i", os.path.join(ROOT, "assets", sc["img"]), "-vf", vf, "-t", f"{d_:.3f}",
         "-c:v", "libx264", "-preset", "medium", "-crf", "17", "-pix_fmt", "yuv420p", out])
    clip_paths.append(out)

# --------------------------------------------------------------------------- #
# 6. Timeline: cross-fades + film grade + type + progress bar
# --------------------------------------------------------------------------- #
inputs = []
for p in clip_paths:
    inputs += ["-i", p]
inputs += ["-i", os.path.join(B, "scrim.png"), "-i", os.path.join(B, "topshade.png")]

fc, prev = [], "[0:v]"
offset = 0.0
for i in range(1, len(clip_paths)):
    offset += durs[i - 1] - XF
    lbl = f"[x{i}]"
    fc.append(f"{prev}[{i}:v]xfade=transition=fade:duration={XF}:offset={offset:.4f}{lbl}")
    prev = lbl
scrim_idx = len(clip_paths)
fc.append(
    f"{prev}eq=contrast=1.07:saturation=1.12:brightness=-0.010:gamma=1.03,colorbalance=rs=.035:gs=.012:bs=-.03,"
    f"unsharp=5:5:0.35:5:5:0.0,"
    f"noise=alls=6:allf=t+u,"
    f"vignette=angle=PI/4.4,"
    f"format=rgba[g]"
)
fc.append(f"[g][{scrim_idx}:v]overlay=0:0:format=auto[g1]")
fc.append(f"[g1][{scrim_idx+1}:v]overlay=0:0:format=auto[gs]")
fc.append(
    f"[gs]ass={ass_path}:fontsdir={FONTS},"
    f"drawbox=x=0:y=0:w=1080:h=9:color=white@0.13:t=fill,"
    f"drawbox=x=0:y=0:w='1080*t/{TOTAL:.3f}':h=9:color=0xE8B44A@0.9:t=fill,"
    f"scale=in_range=full:out_range=limited:flags=lanczos,format=yuv420p[v]"
)
video_silent = os.path.join(B, "video_silent.mp4")
run([FF, "-hide_banner", "-loglevel", "error", "-y", *inputs,
     "-filter_complex", ";".join(fc), "-map", "[v]",
     "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p",
     "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
     "-color_range", "tv", "-r", str(FPS), "-t", f"{TOTAL:.3f}", video_silent])
print("video ok", round(TOTAL, 2), "s")

# --------------------------------------------------------------------------- #
# 7. Sound design + voice mix
# --------------------------------------------------------------------------- #
SR = 48000
n = int(SR * (TOTAL + 0.2))
tt = np.arange(n) / SR
rng = np.random.default_rng(11)

bed = np.zeros(n)
for f, g_, dly in [(55.0, 0.40, 0.0), (82.5, 0.18, 0.0), (110.0, 0.13, 0.4), (164.8, 0.05, 0.9)]:
    bed += g_ * np.sin(2 * np.pi * f * tt + 0.4 * np.sin(2 * np.pi * 0.06 * tt + dly))
bed *= 0.62 + 0.38 * np.sin(2 * np.pi * 0.045 * tt - 1.1)
bed *= 0.55

# heartbeat pulse -> tension
period, k = 0.78, 0
while k * period + 0.25 < TOTAL:
    s0 = int((k * period + 0.25) * SR)
    ln = min(int(0.42 * SR), n - s0)
    tt2 = np.arange(ln) / SR
    bed[s0:s0 + ln] += 0.55 * np.exp(-tt2 * 13) * np.sin(2 * np.pi * 46 * tt2)
    k += 1

# air / room tone
air = rng.standard_normal(n)
ker = np.ones(240) / 240
air = np.convolve(air, ker, mode="same") * 4.2
bed += 0.045 * air * (0.55 + 0.45 * np.sin(2 * np.pi * 0.07 * tt))

# impacts on scene cuts
for i in range(1, len(starts)):
    s0 = int((starts[i] - 0.06) * SR)
    ln = min(int(1.1 * SR), n - s0)
    tt3 = np.arange(ln) / SR
    hit = 0.85 * np.exp(-tt3 * 6.5) * np.sin(2 * np.pi * 41 * tt3)
    burst = rng.standard_normal(ln) * np.exp(-tt3 * 70) * 0.08
    bed[s0:s0 + ln] += hit + burst

# riser into the final CTA
r0 = max(0.0, starts[-1] - 1.6)
s0 = int(r0 * SR); ln = min(int(1.6 * SR), n - s0)
tt4 = np.arange(ln) / SR
riser = rng.standard_normal(ln)
riser = np.convolve(riser, np.ones(18) / 18, mode="same")
bed[s0:s0 + ln] += 0.30 * riser * (tt4 / 1.6) ** 2

for i in range(1, len(starts)):
    s0 = int((starts[i] - 0.20) * SR)
    ln = min(int(0.34 * SR), n - s0)
    if ln > 0:
        w_ = rng.standard_normal(ln)
        w_ = w_ - np.convolve(w_, np.ones(26) / 26, mode="same")
        bed[s0:s0 + ln] += 0.11 * w_ * (np.sin(np.linspace(0, np.pi, ln)) ** 2)

# fades
fi = int(0.25 * SR); fo = int(1.5 * SR)
bed[:fi] *= np.linspace(0, 1, fi)
bed[-fo:] *= np.linspace(1, 0, fo)

voice = np.zeros(n)
for i, v in enumerate(voices):
    a = v["a"]
    a = a - a.mean()
    a = a / (np.max(np.abs(a)) + 1e-9) * 0.92
    s0 = int(vstart[i] * SR)
    ln = min(len(a), n - s0)
    voice[s0:s0 + ln] += a[:ln]

mix = voice + bed * 0.34
peak = np.max(np.abs(mix))
if peak > 0.97:
    mix = np.tanh(mix * 1.25) / 1.25
mix = np.clip(mix, -1, 1)
stereo = np.stack([mix, mix], axis=1)
mix_path = os.path.join(B, "mix.wav")
with wave.open(mix_path, "w") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((stereo * 32767).astype(np.int16).tobytes())
print("audio ok", round(len(mix) / SR, 2), "s")
_v = voice[np.abs(voice) > 0.02]
print("RMS voice(dBFS)=%.1f  bed(dBFS)=%.1f  mix peak=%.2f" % (
    20 * np.log10(np.sqrt(np.mean(_v ** 2)) + 1e-9),
    20 * np.log10(np.sqrt(np.mean((bed * 0.34) ** 2)) + 1e-9),
    np.max(np.abs(mix))))

# --------------------------------------------------------------------------- #
# 8. Mux + loudness normalisation (YouTube target -14 LUFS)
# --------------------------------------------------------------------------- #
r = run([FF, "-hide_banner", "-loglevel", "info", "-y", "-i", video_silent, "-i", mix_path,
         "-af", "loudnorm=I=-14:TP=-1.2:LRA=11:print_format=json",
         "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
         "-movflags", "+faststart", os.path.join(OUT, "money_shorts.mp4")])
tail = r.stderr[-900:]
print(tail)
print("DONE ->", os.path.join(OUT, "money_shorts.mp4"), round(TOTAL, 2), "s")
json.dump(dict(total=TOTAL, durations=durs, starts=starts, vstart=vstart),
          open(os.path.join(B, "timeline.json"), "w"), indent=1)


def srt_ts(x):
    h = int(x // 3600); m = int(x % 3600 // 60); sec = int(x % 60); ms = int((x - int(x)) * 1000)
    return f"{h:02d}:{m:02d}:{sec:02d},{ms:03d}"

LINES = [
    "الغني بيشتري حاجات رخيصة، والفقير بيشتري حاجات غالية.",
    "ومش قصدي إنه بخيل. قصدي إن الغني بيسأل سؤال واحد قبل ما يدفع: الحاجة دي هترجّعلي فلوس، ولا هتاخد مني فلوس؟",
    "الفقير بيشتري العربية اللي بتخليه يبان ناجح. الغني بيشتري العربية اللي بتوصله للشغل اللي بيجيب فلوس.",
    "موبايل بالتقسيط 24 شهر؟ ده مش غنى، ده إيجار على المنظر. بتدفع كل شهر عشان الناس تصدق حاجة مش حقيقية.",
    "الفلوس اللي بتشتري شكل الغنى بتختفي، والفلوس اللي بتشتري أصل هي اللي بتفضل تشتغل وإنت نايم.",
    "القاعدة واحدة: اشتري اللي بيجيب فلوس، مش اللي بياخد فلوس. اكتب في كومنت أول أصل هتشتريه، واعمل حفظ للفيديو.",
]
out = []
for i, (v, line) in enumerate(zip(vstart, LINES), 1):
    out.append(f"{i}\n{srt_ts(v)} --> {srt_ts(min(TOTAL, v + voices[i-1]['speech']))}\n{line}\n")
open(os.path.join(OUT, "money_shorts.srt"), "w", encoding="utf-8").write("\n".join(out))

# cover (Shorts thumbnail = 1080x1920)
run([FF, "-hide_banner", "-loglevel", "error", "-y", "-ss", "2.20",
     "-i", os.path.join(OUT, "money_shorts.mp4"), "-frames:v", "1",
     "-q:v", "2", os.path.join(OUT, "cover.jpg")])
print("srt + cover done")
