#!/usr/bin/env python3
"""Build the Arabic documentary short from generated images + VO + synth SFX."""
import os, re, subprocess, sys, wave
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display
import imageio_ffmpeg

FF = imageio_ffmpeg.get_ffmpeg_exe()
SR = 44100
W, H, FPS = 1080, 1920, 30
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
TEMPO = 1.12

os.makedirs("build", exist_ok=True)

def run(cmd):
    print("+", " ".join(str(c) for c in cmd)[:220], flush=True)
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr[-3000:]); sys.exit(1)
    return r

def duration(path):
    r = subprocess.run([FF, "-i", path], capture_output=True, text=True)
    m = re.search(r"Duration: (\d+):(\d+):([\d.]+)", r.stderr)
    return int(m.group(1))*3600 + int(m.group(2))*60 + float(m.group(3))

# ---------- 1. speed up VO ----------
scenes = [
    ("assets/scene1_desert.jpg", "audio/vo1.mp3",
     ["ماذا لو قلت لك إن نصف أهرامات العالم",
      "ليست فوق الأرض، بل مدفونة",
      "تحت رمال الصحراء الكبرى؟"]),
    ("assets/scene2_radar.jpg", "audio/vo2.mp3",
     ["باستخدام تقنيات الرادار الفضائي،",
      "رصد الباحثون هياكل هندسية عملاقة",
      "وغرفاً تحت الأرض تمتد لكيلومترات،",
      "عجزت التفسيرات التقليدية عن إيضاحها."]),
    ("assets/scene3_tomb.jpg", "audio/vo3.mp3",
     ["الغريب ليس فقط حجم هذه الأبنية،",
      "بل ما عُثر عليه بداخلها:",
      "سراديب مقطوعة بالليزر،",
      "وكتل جرانيت تزن أطنان",
      "تم نقلها قبل آلاف السنين بطريقة",
      "يستحيل تكرارها بمعدات ذلك العصر!"]),
    ("assets/scene4_earth.jpg", "audio/vo4.mp3", None),  # big on-screen text instead
]

vo_wavs, durs = [], []
for i, (_, vo, _) in enumerate(scenes, 1):
    out = f"build/vo{i}_fast.wav"
    run([FF, "-y", "-i", vo, "-filter:a", f"atempo={TEMPO}", "-ar", str(SR), "-ac", "1", out])
    vo_wavs.append(out); durs.append(duration(out))

scene_durs = [d + 0.7 for d in durs]          # 0.35 head + 0.35 tail
starts, t = [], 0.0
for d in scene_durs:
    starts.append(t); t += d
TOTAL = t
print("VO durs:", [round(d,2) for d in durs], "TOTAL:", round(TOTAL,2))

# ---------- 2. subtitle PNGs ----------
def ar(line): return get_display(arabic_reshaper.reshape(line))

def subtitle_png(lines, path, font_size=46, box_alpha=150):
    f = ImageFont.truetype(FONT, font_size)
    lh = int(font_size * 1.45)
    h = lh * len(lines) + 56
    img = Image.new("RGBA", (W, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    widths = [d.textlength(ar(l), font=f) for l in lines]
    bw = max(widths) + 72
    d.rounded_rectangle([(W-bw)/2, 10, (W+bw)/2, h-10], radius=26, fill=(0, 0, 0, box_alpha))
    for j, l in enumerate(lines):
        d.text((W/2, 28 + lh*j + lh/2), ar(l), font=f, fill=(255, 255, 255),
               anchor="mm", stroke_width=2, stroke_fill=(0, 0, 0, 220))
    img.save(path); return h

def bigtext_png(path):
    lines = ["هل هي مجرد مقابر عادية؟", "أم حضارة متطورة اندثرت", "تحت الرمال؟"]
    f = ImageFont.truetype(FONT, 78)
    lh = int(78 * 1.4)
    h = lh * len(lines) + 60
    img = Image.new("RGBA", (W, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    widths = [d.textlength(ar(l), font=f) for l in lines]
    bw = max(widths) + 80
    d.rounded_rectangle([(W-bw)/2, 10, (W+bw)/2, h-10], radius=30, fill=(0, 0, 0, 150))
    for j, l in enumerate(lines):
        d.text((W/2, 30 + lh*j + lh/2), ar(l), font=f, fill=(255, 214, 120),
               anchor="mm", stroke_width=3, stroke_fill=(0, 0, 0, 230))
    img.save(path)

sub_pngs = []
for i, (_, _, lines) in enumerate(scenes, 1):
    p = f"build/sub{i}.png"
    if lines: subtitle_png(lines, p)
    else: bigtext_png(p)
    sub_pngs.append(p)

# comment pill for the outro
pill = "build/pill.png"
f = ImageFont.truetype(FONT, 50)
txt = ar("اكتب رأيك في التعليقات ▼")
img = Image.new("RGBA", (W, 130), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
tw = d.textlength(txt, font=f)
d.rounded_rectangle([(W-tw)/2-40, 15, (W+tw)/2+40, 115], radius=50, fill=(200, 30, 30, 235))
d.text((W/2, 65), txt, font=f, fill=(255, 255, 255), anchor="mm")
img.save(pill)

# ---------- 3. scene clips (Ken Burns + fades + burned subtitles) ----------
clips = []
for i, (img_path, _, _) in enumerate(scenes, 1):
    dur = scene_durs[i-1]; frames = int(dur * FPS)
    if i == 4:  # pull-back (zoom out) for the space shot
        z = f"1.18-0.18*on/{frames}"
    else:
        z = f"1+0.18*on/{frames}"
    vf = (f"scale=1620:-2,zoompan=z='{z}':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
          f":d={frames}:s={W}x{H}:fps={FPS},"
          f"fade=t=in:st=0:d=0.4,fade=t=out:st={dur-0.6:.2f}:d=0.6,format=yuv420p")
    if i != 4:
        vf += f",overlay=(W-w)/2:H-h-170:enable='between(t,0.25,{dur-0.25:.2f})'"
    clip = f"build/clip{i}.mp4"
    if i == 4:
        # big question text (input 1) at y=300 + comment pill (input 2) at bottom
        fc = (f"[0:v]{vf}[v0];[v0][1:v]overlay=(W-w)/2:300:"
              f"enable='between(t,0.4,{dur-0.4:.2f})'[v1];"
              f"[v1][2:v]overlay=(W-w)/2:H-h-170:"
              f"enable='between(t,0.4,{dur-0.4:.2f})'[v]")
        run([FF, "-y", "-i", img_path, "-i", sub_pngs[i-1], "-i", pill,
             "-filter_complex", fc, "-map", "[v]", "-frames:v", str(frames),
             "-c:v", "libx264", "-preset", "fast", "-crf", "20", "-r", str(FPS), clip])
    else:
        run([FF, "-y", "-i", img_path, "-i", sub_pngs[i-1], "-filter_complex", f"[0:v]{vf}[v]",
             "-map", "[v]", "-frames:v", str(frames), "-c:v", "libx264", "-preset", "fast",
             "-crf", "20", "-r", str(FPS), clip])
    clips.append(clip)

concat_list = "build/concat.txt"
with open(concat_list, "w") as fh:
    for c in clips: fh.write(f"file '{os.path.abspath(c)}'\n")
run([FF, "-y", "-f", "concat", "-safe", "0", "-i", concat_list,
     "-c:v", "libx264", "-preset", "fast", "-crf", "20", "-r", str(FPS), "build/video_silent.mp4"])

# ---------- 4. synthesized SFX bed ----------
rng = np.random.default_rng(7)
N = int(TOTAL * SR)
bed = np.zeros(N)

def add(start, sig):
    s = int(start * SR); e = min(N, s + len(sig))
    if e > s: bed[s:e] += sig[:e-s]

def lowpass(x, a):
    y = np.empty_like(x); acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc); y[i] = acc
    return y

def boom(dur_s, amp=0.9):
    tt = np.arange(int(dur_s * SR)) / SR
    freq = 30 + 85 * np.exp(-tt * 3.0)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    return amp * np.exp(-tt * 2.4) * np.sin(phase)

tt_all = np.arange(N) / SR
# tense drone bed throughout
bed += (0.045 * np.sin(2*np.pi*48*tt_all) + 0.02 * np.sin(2*np.pi*96*tt_all)) \
       * (0.7 + 0.3 * np.sin(2*np.pi*0.2*tt_all))
bed *= np.minimum(1, tt_all / 0.5) * np.minimum(1, (TOTAL - tt_all) / 2.5)

# scene 1: sandstorm wind + deep boom
s1, d1 = starts[0], scene_durs[0]
wind = lowpass(rng.standard_normal(int(d1*SR)), 0.06)
gust = 0.55 + 0.45 * np.sin(2*np.pi*0.25*np.arange(len(wind))/SR) + 0.25*np.sin(2*np.pi*0.9*np.arange(len(wind))/SR)
add(s1, 0.30 * wind * gust)
add(s1 + 0.35, boom(1.6, 0.95))

# scene 2: drone swell + radar beeps
s2, d2 = starts[1], scene_durs[1]
add(s2, 0.05 * np.sin(2*np.pi*55*np.arange(int(d2*SR))/SR))
tb = s2 + 0.5
while tb < s2 + d2 - 0.3:
    bt = np.arange(int(0.09*SR)) / SR
    add(tb, 0.16 * np.exp(-bt*45) * np.sin(2*np.pi*1500*bt))
    tb += 1.35

# scene 3: stone drag + heartbeat
s3, d3 = starts[2], scene_durs[2]
drag_t = np.arange(int(1.6*SR)) / SR
drag = lowpass(rng.standard_normal(len(drag_t)), 0.02) * (0.7 + 0.3*np.sin(2*np.pi*7*drag_t))
drag *= np.minimum(1, drag_t/0.25) * np.exp(-drag_t*1.1)
add(s3 + 0.15, 0.85 * drag)
hb = s3 + 0.4
while hb < s3 + d3 - 0.4:
    for off, a in [(0, 0.30), (0.19, 0.20)]:
        bt = np.arange(int(0.22*SR)) / SR
        add(hb + off, a * np.exp(-bt*20) * np.sin(2*np.pi*52*bt))
    hb += 0.92

# scene 4: cinematic impact
s4 = starts[3]
add(s4 + 0.1, boom(2.2, 1.0))
crash_t = np.arange(int(1.8*SR)) / SR
add(s4 + 0.1, 0.35 * lowpass(rng.standard_normal(len(crash_t)), 0.25) * np.exp(-crash_t*2.2))

# ---------- 5. mix VO + bed ----------
mix = bed.copy()
for wav, st in zip(vo_wavs, starts):
    with wave.open(wav, "rb") as wf:
        n = wf.getnframes(); raw = wf.readframes(n)
    sig = np.frombuffer(raw, dtype=np.int16).astype(np.float64) / 32768.0
    s = int((st + 0.35) * SR); e = min(N, s + len(sig))
    mix[s:e] += sig[:e-s] * 1.15

peak = np.max(np.abs(mix))
if peak > 0.95: mix *= 0.95 / peak
stereo = np.stack([mix, mix], axis=1)
pcm = (np.clip(stereo, -1, 1) * 32767).astype(np.int16)
with wave.open("build/final_audio.wav", "wb") as wf:
    wf.setnchannels(2); wf.setsampwidth(2); wf.setframerate(SR)
    wf.writeframes(pcm.tobytes())

# ---------- 6. mux ----------
run([FF, "-y", "-i", "build/video_silent.mp4", "-i", "build/final_audio.wav",
     "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
     "-movflags", "+faststart", "-shortest", "pyramids_buried.mp4"])
print("DONE total", round(TOTAL, 2), "s -> pyramids_buried.mp4")
