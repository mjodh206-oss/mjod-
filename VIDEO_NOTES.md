# سلسلة «أقوى 7» — Vertical Shorts (1080×1920)

Series of procedurally generated vertical shorts. **No stock footage, no templates**:
every frame is drawn by code and every sound is synthesised.

| | الجزء الأول | الجزء الثاني |
|---|---|---|
| **العنوان** | أقوى 7 نصائح في تاريخ الكون | 7 قوانين تحكم حياتك |
| **الملف** | `mjod_video.mp4` (42 MB) | `mjod_video_p2.mp4` |
| **النسخة الخفيفة** | `mjod_video_720p.mp4` (15 MB) | `mjod_video_p2_720p.mp4` |
| **المدة** | 1:46 | 1:39 |
| **المواصفات** | 1080×1920 · 30fps · H.264 High · AAC 192k · −14 LUFS |  |

## Pipeline
```
audio/p2/vo_*.mp3        narration takes (Arabic)
voice/*.wav              level-matched VO at 1.09× pacing
voice/analysis_pN.json   measured speech segments (drives all sync)
video/episodes.py        ALL content per part (text, colours, timings)
video/timeline.py        sections + beat grid, built from episodes.py
video/audio_build.py     score + SFX + ducking + master  -> build/mix.wav
video/render.py          draws frames -> x264                -> <episode>.mp4
```

### Adding an episode
1. Generate the VO takes into `audio/pN/`.
2. Normalise + measure → `voice/analysis_pN.json`.
3. Add an entry to `video/episodes.py`.
4. ```bash
   MJOD_EPISODE=3 python3 -m video.audio_build
   MJOD_EPISODE=3 python3 -m video.render
   ```

### Sync model
`analysis_pN.json` holds measured speech spans. `timeline.align()` distributes words
across those spans by syllable weight; the hook beats and the karaoke captions are
then placed from those real timestamps, so text never drifts from the voice.

### Sound design (synthesised)
Sub-boom impacts on each cut · whooshes into/out of cuts · risers into every law ·
detuned saw pads with a chord change every bar (Am–F–C–G) · heartbeat on the final
law · glass shimmer · crowd swell on the outro · sidechain ducking under the voice ·
bus compression + limiter, mastered to −14 LUFS.

### Visual system
3D torus-knot hero (numpy projection, depth-shaded neon tube) · animated volumetric
light field · dust · perspective grid · Arabic typography shaped with **HarfBuzz** and
rasterised with **FreeType** (correct RTL joining) · gold gradient type · bloom ·
vignette · film grain · chromatic aberration on cuts · fat karaoke captions so the
video works muted.

**Part 2 signature:** statute ticks down both edges (`law_frame`) so the sequel is
instantly recognisable as the same series but a different chapter.

### Rebuild checklist (important)
`build/` and `build2/` are git-ignored. If a build directory is missing, the render
still succeeds but writes a **silent** video (no `mix.wav` to mux). Always run the
audio builder first:

    MJOD_EPISODE=1 python3 -m video.audio_build    # -> build/mix.wav
    MJOD_EPISODE=1 python3 -m video.render         # 1080x1920 master, audio muxed

    MJOD_EPISODE=2 python3 -m video.audio_build    # -> build2/mix.wav
    MJOD_EPISODE=2 python3 -m video.render

Then encode: hqdn3d → crf 22 preset slow → loudnorm I=-14 → 720p copy.

### Delivery links (branch arena/94a71bdc-mjod)
- `mjod_video.mp4` — part 1, 1:45.97, 1080x1920
- `mjod_video_720p.mp4` — part 1 light copy
- `mjod_video_p2.mp4` — part 2 «7 قوانين تحكم حياتك», 1:38.90
- `mjod_video_p2_720p.mp4` — part 2 light copy

---

# Part 3 — «7 أخطاء تقتل حياتك» (75 s)

Same episode-driven engine, but a **new look book** and a rebuilt hook.

## What changed vs parts 1–2

### 1. New visual engine: `video/shaders.py`
Vectorised re-implementations of the high-scoring shadertoy looks, written for a
CPU-only box (no GPU, no EGL in this sandbox — `moderngl`/`skia-python` install
but cannot create a context, so GLSL is out):

| function | look | used in |
|---|---|---|
| `nebula` | domain-warped fbm + ridged filaments | hook A/E, law 06, outro |
| `vortex` | log-spiral accretion disc | hook B, law 04 |
| `tunnel` | polar raymarch, depth fog | hook C, laws 01/07 |
| `energy_grid` | perspective wave grid + shock rings | hook D, law 02 |
| `orbs` | metaball field | law 03 |
| `shards` | faceted glass with rim light | law 05 |
| `god_rays` | radial volumetric shafts | hook E, law 01 |
| `speed_lines` | warp streaks | hook C |
| `bloom_halation` | single-pass bloom + film halation | grade |
| `gate_weave` | sub-pixel camera gate breathing | grade |
| `anamorphic_flare` / `shockwave` | lens streak, expanding light ring | hits |

Each law carries `look=` in `episodes.py`, so the next part only needs new names.

### 2. The hook (first 3 s)
Three *complete* images before 1.3 s and five cuts before 8.4 s, every cut on a
real word onset (measured, not guessed):

| cut | t | plate | type |
|---|---|---|---|
| A | 0.00 | near-black + cold nebula, 3-frame white smash | «توقف» 300 px |
| B | 1.12 | orange accretion vortex | «أنت لا تفشل» / «بسبب سوء الحظ» |
| C | 4.28 | amber tunnel, numeral explodes | «7» 720 px + «سبعة أخطاء» |
| D | 6.55 | ice-blue Tron grid | «دون أن تشعر» |
| E | 8.32 | blood-red nebula + god rays | «يقتلك بصمت» 152 px |

`_drift()` adds a sub-degree push/pan/roll inside every cut so no shot is ever
a still frame. Last 0.4 s is a blackout into law 01.

### 3. Music: explicit low-level bed
`episodes.py` now carries two knobs read by `audio_build`:

```python
music=0.115,   # bed gain (parts 1-2 use the 0.34 default => ~9.4 dB louder)
duck=0.80,     # how hard the bed ducks under the voice (1.0 = full)
```

P3 bed is 9.4 dB quieter than parts 1–2 and ducks 0.80 instead of 0.62, so the
score is *felt* rather than heard. Its chord bed is also a different mood:
`CHORDS_P3` = Dm–Bb–F–C at 80 bpm (parts 1–2 are Am–F–C–G at 88),
selected with `mood=3`.

## Render

```bash
MJOD_EPISODE=3 python3 -m video.audio_build     # -> build3/mix.wav
MJOD_EPISODE=3 python3 -m video.render_p3        # -> mjod_video_p3.mp4 (1080x1920)
MJOD_EPISODE=3 python3 -m video.preview3 1.2 7.6 # stills for review
```

`video/render_p3.py` is the part-3 renderer (hooks/tips/outro/grade). It reads
the same `timeline.py` + `episodes.py`, so an episode-4 only needs a new dict and
(optionally) new entries in `shaders.py`.

Frame cost on 2 vCPU: ~0.58 s/frame => ~22 min for 2232 frames, then the usual
encode chain (hqdn3d → crf 22 slow → loudnorm −14 → 720p copy).

## Files

| file | what |
|---|---|
| `mjod_video_p3.mp4` | part 3 master, 1:14.40, 1080×1920, AAC 192k |
| `mjod_video_p3_720p.mp4` | light copy for sharing |
| `poster_p3.jpg` | cover frame |
| `audio/p3/vo_*.mp3` | 9 narration takes |
| `voice/p3_vo_*.wav` | 1.09× time-warped takes used by the mix |
| `voice/analysis_p3.json` | measured speech segments (sync source of truth) |
