# «أقوى 7 نصائح في تاريخ الكون» — Vertical Short (1080×1920)

A fully **procedurally generated** short: no stock footage, no templates.
Every frame is rendered by code; every sound is synthesised.

## Deliverables
| file | what |
|---|---|
| `mjod_video.mp4` | final master — 1080×1920, 30 fps, H.264 High, AAC 192k, ~106 s |
| `build/mix.wav` | final mix (voice + score + SFX), 48 kHz / 24-bit |
| `build/music.wav`, `build/sfx.wav` | score and SFX stems |
| `audio/vo_*.mp3` | Arabic voiceover takes |

## Pipeline
```
audio/vo_*.mp3            generated narration (Arabic)
build/vo/*.wav            time-warped to 1.09× for pacing + silence analysis
video/timeline.py         single source of truth: sections, beats, word timing
video/audio_build.py      score + sound design + ducking + mastering -> build/mix.wav
video/render.py           draws every frame -> ffmpeg (x264) -> mp4
```

### Sync model
`build/vo_analysis.json` holds measured speech segments per clip. `timeline.align()`
distributes each word across those segments by syllable weight, so captions,
quote reveals and SFX land on the actual voice — not on a guessed grid.

### Sound design (all synthesised)
- sub-boom impacts on every section cut
- whooshes into / out of cuts
- risers + detuned saw pads per tip, chord changes every bar (Am–F–C–G)
- heartbeat in tip 07, glass shimmer, crowd swell on the outro
- sidechain ducking of the score under the voice + bus compression + limiter

### Visual system
- hero object: 3D torus-knot projected in numpy, depth-shaded neon tube
- animated volumetric light field, dust particles, perspective grid floor
- Arabic typography shaped with HarfBuzz + rasterised with FreeType
  (correct RTL joining — PIL alone cannot do this without libraqm)
- gold gradient type, bloom, vignette, film grain, chromatic aberration on cuts
- **fat karaoke captions** on every line so the video works muted (most feed views are)
