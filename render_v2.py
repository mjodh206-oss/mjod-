#!/usr/bin/env python3
"""V2 cinematic renderer: real motion (particles, radar sweep, parallax camera,
glitch/circle/whip transitions, karaoke subtitles) + synthesized trailer score.
Tools: OpenCV + FFmpeg (both major GitHub projects), PIL, numpy."""
import os, re, subprocess, sys, wave, math
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display
import imageio_ffmpeg

FF = imageio_ffmpeg.get_ffmpeg_exe()
SR = 44100
W, H, FPS = 1080, 1920, 30
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
TEMPO = 1.12
CW, CH = 1400, 2480                      # oversized canvas for camera moves
os.makedirs("build2", exist_ok=True)

def run(cmd):
    print("+", " ".join(str(c) for c in cmd)[:200], flush=True)
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr[-3000:]); sys.exit(1)
    return r

def duration(path):
    r = subprocess.run([FF, "-i", path], capture_output=True, text=True)
    m = re.search(r"Duration: (\d+):(\d+):([\d.]+)", r.stderr)
    return int(m.group(1))*3600 + int(m.group(2))*60 + float(m.group(3))

def ar(s): return get_display(arabic_reshaper.reshape(s))

# ---------------- scene data ----------------
SCENES = [
    dict(img="assets/scene1_desert.jpg", vo="audio/vo1.mp3", tag="الصحراء الكبرى",
         lines=[["ماذا","لو","قلت","لك","إن","نصف","أهرامات","العالم"],
                ["ليست","فوق","الأرض،","بل","مدفونة"],
                ["تحت","رمال","الصحراء","الكبرى؟"]]),
    dict(img="assets/scene2_radar.jpg", vo="audio/vo2.mp3", tag="رادار فضائي",
         lines=[["باستخدام","تقنيات","الرادار","الفضائي،"],
                ["رصد","الباحثون","هياكل","هندسية","عملاقة"],
                ["وغرفاً","تحت","الأرض","تمتد","لكيلومترات،"],
                ["عجزت","التفسيرات","التقليدية","عن","إيضاحها."]]),
    dict(img="assets/scene3_tomb.jpg", vo="audio/vo3.mp3", tag="مقبرة أثرية",
         lines=[["الغريب","ليس","فقط","حجم","هذه","الأبنية،"],
                ["بل","ما","عُثر","عليه","بداخلها:"],
                ["سراديب","مقطوعة","بالليزر،"],
                ["وكتل","جرانيت","تزن","أطنان"],
                ["تم","نقلها","قبل","آلاف","السنين","بطريقة"],
                ["يستحيل","تكرارها","بمعدات","ذلك","العصر!"]]),
    dict(img="assets/scene4_earth.jpg", vo="audio/vo4.mp3", tag="من الفضاء",
         lines=[["هل","هي","مجرد","مقابر","عادية؟"],
                ["أم","حضارة","متطورة","اندثرت"],
                ["تحت","الرمال؟"]]),
]

# ---------------- VO timing ----------------
vo_wavs, vo_durs = [], []
for i, sc in enumerate(SCENES, 1):
    out = f"build2/vo{i}_fast.wav"
    run([FF, "-y", "-i", sc["vo"], "-filter:a", f"atempo={TEMPO}", "-ar", str(SR), "-ac", "1", out])
    vo_wavs.append(out); vo_durs.append(duration(out))
scene_durs = [d + 0.7 for d in vo_durs]
starts, _t = [], 0.0
for d in scene_durs: starts.append(_t); _t += d
TOTAL = _t
print("VO:", [round(d, 2) for d in vo_durs], "TOTAL:", round(TOTAL, 2), flush=True)

# ---------------- precompute ----------------
def cover_canvas(path):
    img = cv2.imread(path)
    h, w = img.shape[:2]
    s = max(CW / w, CH / h)
    r = cv2.resize(img, (int(w*s+0.5), int(h*s+0.5)), interpolation=cv2.INTER_LANCZOS4)
    x0 = (r.shape[1]-CW)//2; y0 = (r.shape[0]-CH)//2
    return r[y0:y0+CH, x0:x0+CW].copy()

canvases = [cover_canvas(sc["img"]) for sc in SCENES]

_yy, _xx = np.mgrid[0:H, 0:W].astype(np.float32)
_dist = np.sqrt(((_xx-W/2)/(W*0.62))**2 + ((_yy-H/2)/(H*0.62))**2)
vig = cv2.cvtColor((np.clip(1.18-_dist*0.72, 0.42, 1.0)*255).astype(np.uint8), cv2.COLOR_GRAY2BGR)
beam_grad = np.exp(-(((_xx-540)/300.0)**2 + ((_yy-470)/680.0)**2)).astype(np.float32)
wipe_dist = np.sqrt((_xx-540)**2 + (_yy-960)**2).astype(np.float32)

rng0 = np.random.default_rng(42)
sand = dict(x0=rng0.uniform(0, 1200, 240), y0=rng0.uniform(0, 2000, 240),
            vx=rng0.uniform(260, 820, 240), vy=rng0.uniform(-28, 28, 240),
            ln=rng0.uniform(8, 30, 240), al=rng0.uniform(0.14, 0.5, 240),
            ph=rng0.uniform(0, 6.28, 240))
dust = dict(x0=rng0.uniform(0, 1080, 150), y0=rng0.uniform(0, 2000, 150),
            vy=rng0.uniform(24, 72, 150), r=rng0.uniform(1, 3.2, 150),
            al=rng0.uniform(0.2, 0.7, 150), ph=rng0.uniform(0, 6.28, 150))
stars = dict(x=rng0.uniform(0, 1080, 180), y=rng0.uniform(0, 1920, 180),
             r=rng0.uniform(0.8, 2.2, 180), f=rng0.uniform(0.6, 2.4, 180),
             ph=rng0.uniform(0, 6.28, 180))

f_sub = ImageFont.truetype(FONT, 46)
f_big = ImageFont.truetype(FONT, 76)
f_tag = ImageFont.truetype(FONT, 32)
f_hud = ImageFont.truetype(FONT, 40)
f_hud2 = ImageFont.truetype(FONT, 27)
f_pill = ImageFont.truetype(FONT, 50)
_dummy = ImageDraw.Draw(Image.new("RGB", (8, 8)))
SPACE_W = 16

# karaoke word timing per scene
for si, sc in enumerate(SCENES):
    flat, widths, line_w = [], [], []
    for li, line in enumerate(sc["lines"]):
        lw = 0
        for wd in line:
            wpx = _dummy.textlength(ar(wd), font=f_sub)
            flat.append((wd, li)); widths.append(wpx); lw += wpx + SPACE_W
        line_w.append(lw - SPACE_W)
    sc["flat"], sc["widths"], sc["line_w"] = flat, widths, line_w
    n = len(flat); vd = vo_durs[si]
    sc["word_t"] = [(0.35 + i*vd/n, 0.35 + (i+1)*vd/n) for i in range(n)]

# big-text word geometry (scene 4)
sc4 = SCENES[3]; sc4["big_pos"] = []
for li, line in enumerate(sc4["lines"]):
    ws = [_dummy.textlength(ar(wd), font=f_big) for wd in line]
    total = sum(ws) + SPACE_W*(len(line)-1)
    x = (W + total)/2
    for wd, wpx in zip(line, ws):
        sc4["big_pos"].append((x - wpx/2, 330 + li*112, wd))
        x -= (wpx + SPACE_W)
pill_txt = ar("اكتب رأيك في التعليقات ▼")
pill_w = _dummy.textlength(pill_txt, font=f_pill)

# ---------------- camera + particles ----------------
def camera_crop(canvas, t, dur, si):
    if si == 0:   zoom, px, py = 1.02+0.12*(t/dur), 28*math.sin(t*0.16), 18*math.sin(t*0.11+1)
    elif si == 1: zoom, px, py = 1.16-0.14*(t/dur), 20*math.sin(t*0.13), 14*math.cos(t*0.10)
    elif si == 2: zoom, px, py = 1.00+0.16*(t/dur), 24*math.sin(t*0.14+2), 16*math.sin(t*0.09)
    else:         zoom, px, py = 1.28-0.26*(t/dur), 14*math.sin(t*0.10), 10*math.cos(t*0.08)
    wz, hz = int(W/zoom), int(H/zoom)
    cx, cy = int(700+px), int(1240+py)
    x0 = min(max(cx-wz//2, 0), CW-wz); y0 = min(max(cy-hz//2, 0), CH-hz)
    return cv2.resize(canvas[y0:y0+hz, x0:x0+wz], (W, H), interpolation=cv2.INTER_LINEAR)

def particles(si, t):
    layer = np.zeros((H, W, 3), np.uint8)
    if si == 0:
        gust = 0.7 + 0.6*max(0.0, math.sin(t*0.6))
        xs = (sand["x0"] + sand["vx"]*(t + 0.35*math.sin(t*0.6))*gust) % 1240 - 80
        ys = (sand["y0"] + sand["vy"]*t + 18*np.sin(t*2+sand["ph"])) % 2040 - 60
        for i in range(len(xs)):
            c = tuple(int(v*sand["al"][i]) for v in (120, 180, 220))
            cv2.line(layer, (int(xs[i]), int(ys[i])),
                     (int(xs[i]-sand["ln"][i]), int(ys[i]-sand["vy"][i]*0.03)), c, 2)
    elif si == 2:
        ys = (dust["y0"] + dust["vy"]*t) % 2040 - 60
        xs = dust["x0"] + 14*np.sin(t*0.8+dust["ph"])
        for i in range(len(xs)):
            boost = 0.4 + 1.6*math.exp(-((xs[i]-540)/280.0)**2)
            a = min(1.0, dust["al"][i]*boost)
            c = tuple(int(v*a) for v in (170, 210, 230))
            cv2.circle(layer, (int(xs[i]), int(ys[i])), int(dust["r"][i]), c, -1)
    elif si == 3:
        for i in range(len(stars["x"])):
            a = 0.22 + 0.6*(0.5+0.5*math.sin(t*stars["f"][i]+stars["ph"][i]))
            c = tuple(int(v*a) for v in (255, 240, 220))
            cv2.circle(layer, (int(stars["x"][i]), int(stars["y"][i])), int(stars["r"][i]), c, -1)
    return layer

# ---------------- PIL overlays ----------------
def draw_tag(d, text, t):
    d.ellipse([36, 44, 52, 60], fill=(255, 209, 102, 255))
    d.text((66, 52), ar(text), font=f_tag, fill=(255, 255, 255, 235), anchor="lm",
           stroke_width=2, stroke_fill=(0, 0, 0, 200))

def draw_subtitles(d, sc, t):
    lines, flat = sc["lines"], sc["flat"]
    lh, box_h = 66, len(lines)*66 + 52
    y_top = H - 170 - box_h
    bw = max(sc["line_w"]) + 76
    d.rounded_rectangle([(W-bw)/2, y_top, (W+bw)/2, y_top+box_h], radius=26, fill=(0, 0, 0, 150))
    wi = 0
    for li, line in enumerate(lines):
        yc = y_top + 26 + 33 + li*lh
        x = (W + sc["line_w"][li])/2
        for wd in line:
            wpx = sc["widths"][wi]
            st, en = sc["word_t"][wi]
            col = (255, 209, 102, 255) if st <= t < en else (255, 255, 255, 255)
            d.text((x-wpx/2, yc), ar(wd), font=f_sub, fill=col, anchor="mm",
                   stroke_width=2, stroke_fill=(0, 0, 0, 220))
            x -= (wpx + SPACE_W); wi += 1

def draw_radar(d, t):
    cx, cy = 540, 830
    for r in (170, 340, 510, 680):
        d.ellipse([cx-r, cy-r, cx+r, cy+r], outline=(80, 220, 255, 55), width=2)
    for gx in range(0, W, 135): d.line([(gx, 0), (gx, H)], fill=(255, 255, 255, 13), width=1)
    for gy in range(0, H, 135): d.line([(0, gy), (W, gy)], fill=(255, 255, 255, 13), width=1)
    theta = (t*70) % 360
    for k in range(26):
        a0 = math.radians(theta - k*2.2); a1 = math.radians(theta - k*2.2 + 2.8)
        pts = [(cx, cy)] + [(cx+700*math.cos(a), cy+700*math.sin(a))
                            for a in np.linspace(a0, a1, 5)]
        d.polygon(pts, fill=(80, 220, 255, int(95*(1-k/26)**2)))
    d.line([(cx, cy), (cx+700*math.cos(math.radians(theta)), cy+700*math.sin(math.radians(theta)))],
           fill=(140, 240, 255, 220), width=3)
    blips = [(-210, -160), (240, -90), (-90, 230), (190, 270), (10, -330), (320, 120)]
    for i, (dx, dy) in enumerate(blips):
        bx, by = cx+dx, cy+dy
        pr = 6 + 5*math.sin(t*2.5+i*1.3)
        al = int(150 + 90*math.sin(t*2.5+i*1.3))
        d.ellipse([bx-pr, by-pr, bx+pr, by+pr], fill=(255, 120, 60, al))
        d.ellipse([bx-pr*2.2, by-pr*2.2, bx+pr*2.2, by+pr*2.2], outline=(255, 120, 60, 90), width=2)
    for i in (0, 2, 4):
        bx, by = cx+blips[i][0], cy+blips[i][1]
        bw_, bh_, L = 100, 76, 20
        for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            x0, y0 = bx+sx*bw_/2, by+sy*bh_/2
            d.line([(x0, y0), (x0-sx*L, y0)], fill=(80, 220, 255, 230), width=3)
            d.line([(x0, y0), (x0, y0-sy*L)], fill=(80, 220, 255, 230), width=3)
        d.text((bx-bw_/2, by-bh_/2-8), ar(f"هيكل {chr(65+i)}"), font=f_hud2,
               fill=(140, 240, 255, 230), anchor="lb")
    sy = int((t*260) % H)
    d.line([(0, sy), (W, sy)], fill=(80, 220, 255, 60), width=10)
    d.line([(0, sy), (W, sy)], fill=(140, 240, 255, 130), width=3)
    blink = 255 if math.sin(t*6) > 0 else 60
    d.ellipse([36, 40, 56, 60], fill=(255, 60, 60, blink))
    d.text((70, 50), ar("رادار فضائي"), font=f_hud, fill=(255, 255, 255, 240), anchor="lm",
           stroke_width=2, stroke_fill=(0, 0, 0, 200))
    d.text((70, 92), ar("مسح حراري تحت الرمال"), font=f_hud2, fill=(140, 240, 255, 230), anchor="lm")
    d.text((W-36, 50), f"{26.82+0.03*math.sin(t*0.4):.4f}° N", font=f_hud2,
           fill=(255, 255, 255, 220), anchor="rm")
    d.text((W-36, 92), "ALT 705 KM", font=f_hud2, fill=(255, 255, 255, 180), anchor="rm")

def draw_big(d, sc, t):
    box_alpha = int(140*min(1, max(0, (t-0.4)/0.5)))
    if box_alpha > 0:
        d.rounded_rectangle([70, 290, W-70, 290+3*112+30], radius=30, fill=(0, 0, 0, box_alpha))
    for i, (x, y, wd) in enumerate(sc["big_pos"]):
        st = 0.5 + i*0.18
        a = min(1.0, max(0.0, (t-st)/0.35))
        if a <= 0: continue
        d.text((x, y + (1-a)*36), ar(wd), font=f_big, fill=(255, 214, 120, int(255*a)),
               anchor="mm", stroke_width=3, stroke_fill=(0, 0, 0, int(230*a)))
    s = 1 + 0.035*math.sin(t*5)
    pw, ph = (pill_w+84)*s, 104*s
    cx, cy = W/2, H-170-52
    d.rounded_rectangle([cx-pw/2, cy-ph/2, cx+pw/2, cy+ph/2], radius=int(52*s), fill=(200, 30, 30, 240))
    d.text((cx, cy), pill_txt, font=f_pill, fill=(255, 255, 255, 255), anchor="mm")

# ---------------- scene frame ----------------
def scene_frame(si, t, fidx):
    dur = scene_durs[si]
    fr = camera_crop(canvases[si], t, dur, si)
    fr = cv2.convertScaleAbs(fr, alpha=1.05+0.02*math.sin(t*0.4), beta=-3)
    hsv = cv2.cvtColor(fr, cv2.COLOR_BGR2HSV)
    hh, ss, vv = cv2.split(hsv)
    fr = cv2.cvtColor(cv2.merge((hh, cv2.convertScaleAbs(ss, alpha=1.12, beta=0), vv)), cv2.COLOR_HSV2BGR)
    fr = np.clip(fr.astype(np.float32)*(vig.astype(np.float32)/255.0), 0, 255).astype(np.uint8)
    noise = np.random.default_rng(fidx).integers(-7, 8, (H, W, 1)).astype(np.int16)
    fr = np.clip(fr.astype(np.int16)+noise, 0, 255).astype(np.uint8)
    fr = cv2.add(fr, particles(si, t))
    if si == 2:
        inten = 0.10 + 0.035*math.sin(t*7) + 0.025*math.sin(t*13.7)
        fr = np.clip(fr.astype(np.float32) + beam_grad[:, :, None]*inten*np.array([150, 220, 255], np.float32),
                     0, 255).astype(np.uint8)
    rgb = cv2.cvtColor(fr, cv2.COLOR_BGR2RGB)
    base = Image.fromarray(rgb).convert("RGBA")
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    if si == 1: draw_radar(d, t)
    else: draw_tag(d, SCENES[si]["tag"], t)
    if si < 3: draw_subtitles(d, SCENES[si], t)
    else: draw_big(d, SCENES[3], t)
    out = Image.alpha_composite(base, ov).convert("RGB")
    return cv2.cvtColor(np.array(out), cv2.COLOR_RGB2BGR)

def zoom_center(img, z):
    wz, hz = int(W/z), int(H/z)
    x0, y0 = (W-wz)//2, (H-hz)//2
    return cv2.resize(img[y0:y0+hz, x0:x0+wz], (W, H), interpolation=cv2.INTER_LINEAR)

def get_frame(t):
    fidx = int(t*FPS)
    for bi, b in enumerate(starts[1:]):
        if b-0.25 <= t < b+0.25:
            p = (t-(b-0.25))/0.5
            old = scene_frame(bi, scene_durs[bi]-0.5+p*0.5, fidx)
            new = scene_frame(bi+1, p*0.5, fidx)
            if bi == 0:      # glitch
                base = new.copy()
                rng = np.random.default_rng(fidx*7+1)
                y = 0
                while y < H:
                    bh = int(rng.integers(6, 30))
                    off = int((1-p)*rng.integers(-80, 81))
                    if off: base[y:y+bh] = np.roll(new[y:y+bh], off, axis=1)
                    y += bh
                base = cv2.addWeighted(base, 1-0.4*(1-p), old, 0.4*(1-p), 0)
                base[:, :, 0] = np.roll(base[:, :, 0], 2, axis=1)
                base[:, :, 2] = np.roll(base[:, :, 2], -2, axis=1)
                if rng.random() < 0.6:
                    yy = int(rng.integers(0, H)); base[yy:yy+2] = 255
                fr = base
            elif bi == 1:    # circular wipe
                fr = np.where((wipe_dist < p*1500)[:, :, None], new, old)
            else:            # whip zoom + flash
                ob = cv2.blur(zoom_center(old, 1+p*0.3), (int(1+p*36)|1, 1))
                fr = cv2.addWeighted(ob, 1-p, zoom_center(new, 1.3-p*0.3), p, 0)
                a = 0.5*math.sin(math.pi*p)
                fr = np.clip(fr.astype(np.float32)*(1-a)+255*a, 0, 255).astype(np.uint8)
            break
    else:
        si = max(i for i, s in enumerate(starts) if t >= s)
        fr = scene_frame(si, t-starts[si], fidx)
    if t < 0.5: fr = np.clip(fr.astype(np.float32)*(t/0.5), 0, 255).astype(np.uint8)
    if t > TOTAL-1.2: fr = np.clip(fr.astype(np.float32)*max(0.0, (TOTAL-t)/1.2), 0, 255).astype(np.uint8)
    return fr

# ---------------- render video ----------------
n_frames = int(TOTAL*FPS)
proc = subprocess.Popen([FF, "-y", "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", f"{W}x{H}",
                         "-r", str(FPS), "-i", "-", "-an", "-c:v", "libx264", "-preset", "fast",
                         "-crf", "23", "-pix_fmt", "yuv420p", "build2/v2_silent.mp4"],
                        stdin=subprocess.PIPE)
for i in range(n_frames):
    proc.stdin.write(get_frame(i/FPS).tobytes())
    if i % 150 == 0: print(f"frame {i}/{n_frames}", flush=True)
proc.stdin.close(); proc.wait()
assert proc.returncode == 0
print("video rendered", flush=True)

# ---------------- audio: SFX + trailer score ----------------
rng = np.random.default_rng(7)
N = int(TOTAL*SR)
bed = np.zeros(N); music = np.zeros(N)

def add(arr, start, sig):
    s = int(start*SR); e = min(len(arr), s+len(sig))
    if e > s: arr[s:e] += sig[:e-s]

def fft_lowpass(x, fc):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1/SR)
    return np.fft.irfft(X/(1+(f/fc)**2), len(x))

def boom(dur_s, amp=0.9):
    tt = np.arange(int(dur_s*SR))/SR
    freq = 30 + 85*np.exp(-tt*3.0)
    return amp*np.exp(-tt*2.4)*np.sin(2*np.pi*np.cumsum(freq)/SR)

def saw_stack(freqs, tt):
    sig = np.zeros_like(tt)
    for f in freqs: sig += 2*((tt*f) % 1.0)-1.0
    return sig/len(freqs)

def env_trap(tt, dur, a=0.9):
    return np.clip(np.minimum(1, np.minimum(tt/a, (dur-tt)/a)), 0, 1)

# --- score: chord pads + bass ---
CHORDS = [(55.0, [220.0, 261.63, 329.63, 440.0]), (43.65, [174.61, 220.0, 261.63, 349.23]),
          (65.41, [196.0, 261.63, 329.63, 392.0]), (49.0, [196.0, 246.94, 293.66, 392.0]),
          (73.42, [220.0, 293.66, 349.23, 440.0]), (41.20, [207.65, 261.63, 329.63, 415.30])]
for ci in range(16):
    root, pad = CHORDS[ci % len(CHORDS)]
    st, dur = ci*3.2, 4.8
    tt = np.arange(int(dur*SR))/SR
    add(music, st, 0.16*fft_lowpass(saw_stack(pad, tt), 850)*env_trap(tt, dur))
    bass = np.sin(2*np.pi*root*tt)*0.7 + np.sin(2*np.pi*root*2*tt)*0.3
    add(music, st, 0.20*bass*env_trap(tt, dur))

# --- taiko drums ---
def drum_hit():
    tt = np.arange(int(0.5*SR))/SR
    freq = 42 + 150*np.exp(-tt*9)
    sig = np.sin(2*np.pi*np.cumsum(freq)/SR)*np.exp(-tt*8)
    return sig + fft_lowpass(rng.standard_normal(len(tt)), 900)*np.exp(-tt*14)*0.5
hit = drum_hit()
sched = [(0.4, 0.55, 0.30, scene_durs[0]), (starts[1]+0.3, 0.45, 0.50, scene_durs[1]),
         (starts[2]+0.3, 1.10, 0.32, scene_durs[2]), (starts[3]+0.1, 0.90, 0.60, scene_durs[3])]
for st0, step, amp, sdur in sched:
    tt2 = st0
    while tt2 < st0 + sdur - 0.4:
        add(music, tt2, amp*hit); tt2 += step

# --- braams, risers, whooshes ---
for si, st in enumerate(starts):
    tt = np.arange(int(2.2*SR))/SR
    env = (1-np.exp(-tt*18))*np.exp(-tt*1.15)
    add(music, st, 0.35*fft_lowpass(saw_stack([110, 116.54, 164.81, 220, 233.08], tt), 650)*env)
for b in starts[1:]:
    tt = np.arange(int(1.1*SR))/SR; w = (tt/1.1)**2
    nz = rng.standard_normal(len(tt))
    riser = (fft_lowpass(nz, 350)*(1-w) + fft_lowpass(nz, 6000)*w)*w*0.5
    freq = 160 + 790*(tt/1.1)
    riser += np.sin(2*np.pi*np.cumsum(freq)/SR)*w*0.10
    add(music, b-1.1, 0.6*riser)
    tt = np.arange(int(0.5*SR))/SR; p = tt/0.5; w = np.sin(np.pi*p)
    nz = rng.standard_normal(len(tt))
    add(music, b-0.25, 0.8*(fft_lowpass(nz, 280)*(1-w) + fft_lowpass(nz, 5200)*w)*w)

# --- SFX (v1 set) ---
tt_all = np.arange(N)/SR
bed += (0.03*np.sin(2*np.pi*48*tt_all) + 0.014*np.sin(2*np.pi*96*tt_all))*(0.7+0.3*np.sin(2*np.pi*0.2*tt_all))
bed *= np.minimum(1, tt_all/0.5)*np.minimum(1, (TOTAL-tt_all)/2.5)
s1, d1 = starts[0], scene_durs[0]
wind = fft_lowpass(rng.standard_normal(int(d1*SR)), 380)
gust = 0.55 + 0.45*np.sin(2*np.pi*0.25*np.arange(len(wind))/SR)
add(bed, s1, 0.20*wind*gust); add(bed, s1+0.35, boom(1.6, 0.9))
s2, d2 = starts[1], scene_durs[1]
tb = s2+0.5
while tb < s2+d2-0.3:
    bt = np.arange(int(0.09*SR))/SR
    add(bed, tb, 0.14*np.exp(-bt*45)*np.sin(2*np.pi*1500*bt)); tb += 1.35
s3, d3 = starts[2], scene_durs[2]
dt_ = np.arange(int(1.6*SR))/SR
drag = fft_lowpass(rng.standard_normal(len(dt_)), 220)*(0.7+0.3*np.sin(2*np.pi*7*dt_))
drag *= np.minimum(1, dt_/0.25)*np.exp(-dt_*1.1)
add(bed, s3+0.15, 0.8*drag)
hb = s3+0.4
while hb < s3+d3-0.4:
    for off, a in [(0, 0.28), (0.19, 0.18)]:
        bt = np.arange(int(0.22*SR))/SR
        add(bed, hb+off, a*np.exp(-bt*20)*np.sin(2*np.pi*52*bt))
    hb += 0.92
add(bed, starts[3]+0.1, boom(2.2, 1.0))
ct = np.arange(int(1.8*SR))/SR
add(bed, starts[3]+0.1, 0.32*fft_lowpass(rng.standard_normal(len(ct)), 1400)*np.exp(-ct*2.2))

# --- mix ---
mix = bed + music*0.5
for wav, st in zip(vo_wavs, starts):
    with wave.open(wav, "rb") as wf:
        sig = np.frombuffer(wf.readframes(wf.getnframes()), dtype=np.int16).astype(np.float64)/32768.0
    s = int((st+0.35)*SR); e = min(N, s+len(sig))
    mix[s:e] += sig[:e-s]*1.2
mix = np.tanh(mix*1.05)
peak = np.max(np.abs(mix))
if peak > 0.95: mix *= 0.95/peak
pcm = (np.clip(np.stack([mix, mix], 1), -1, 1)*32767).astype(np.int16)
with wave.open("build2/final_audio.wav", "wb") as wf:
    wf.setnchannels(2); wf.setsampwidth(2); wf.setframerate(SR)
    wf.writeframes(pcm.tobytes())

run([FF, "-y", "-i", "build2/v2_silent.mp4", "-i", "build2/final_audio.wav",
     "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
     "-filter:a", "loudnorm=I=-14:TP=-1.5:LRA=11", "-movflags", "+faststart",
     "-shortest", "pyramids_buried.mp4"])
print("DONE -> pyramids_buried.mp4", round(TOTAL, 2), "s")
