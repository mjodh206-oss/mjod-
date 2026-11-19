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
