# Generated media — tracked inventory, untracked binaries

Everything in `backgrounds/`, `audio/`, `thumbnails/`, `previews/` and in
`content/videos/` is **generated output** and is gitignored on purpose (≈22 MB).
This file is tracked so a fresh clone can regenerate all of it.

## What each folder holds

| Folder | Source | Count |
|---|---|---|
| `backgrounds/ep-00X.png` | AI image generation, vertical 9:16, **no text** (text is drawn crisply by the builder) | 5 |
| `audio/ep-00X.mp3` | TTS voiceover, one consistent English voice (`voice-00`), built from `episodes/*.json` → `beats[].vo` | 5 |
| `thumbnails/ep-00X.jpg` | `tools/build_short.py` overlays `thumbnail.text` on the background | 5 |
| `previews/` | two extracted frames from `ep-001` for visual QA | 2 |
| `content/videos/ep-00X.mp4` | final 1080×1920 H.264/AAC Short with burned-in captions | 5 |
| `content/videos/ep-00X.srt` | full VO subtitles | 5 |

## Regenerating

```bash
python3 -m pip install -r requirements.txt
python tools/build_short.py --all --dry-run   # plan (durations, beat counts)
python tools/build_short.py --all            # rebuild MP4 + SRT + thumbnails
```

The builder needs a background PNG and a VO MP3 per episode; it scales the script's beat
timeline to the real audio duration, so a re-generated voiceover with a different pace stays
in sync. If `ffmpeg` is not on PATH, `imageio-ffmpeg` supplies a static binary.

## Background art direction (for regeneration)

Prompts were: vertical 9:16, cinematic, high contrast, no text/letters/watermark/faces,
clean negative space in the upper third for captions, 1080×1920 composition.

| Episode | Art direction |
|---|---|
| `ep-001` | extreme macro of a hand hovering above an open palm, electric blue rim + warm orange fill, neural line motif, near-black |
| `ep-002` | pale sulfur-yellow Venus in the lower half against black space, thin glowing orbital ellipse, tiny stars |
| `ep-003` | abstract split: blinding white star field on the left, absolute black night sky on the right, sharp vertical divide |
| `ep-004` | golden honey pouring from a wooden dipper in extreme macro, dark stone-chamber bokeh, amber/near-black |
| `ep-005` | flour-dusted rustic loaf and baker's hands kneading dough at dawn, warm oven glow, black/amber |

## Voiceover

One voice for the whole series keeps the channel recognisable. The exact text is the
concatenation of `beats[].vo` from each episode JSON (see `content/SCRIPTS.md`), ~107–119 words
per Short. Re-generate with `generate_speech` using the same `voice_id`, then rebuild.

## Licensing / disclosure

Backgrounds are AI-generated illustrations, not footage of real events. They are decorative.
Every factual claim in the video comes from the episode JSON and must be verified
(`fact_check.verified`) before publishing. Do not present generated imagery as documentary
evidence; the descriptions already say "Educational summary".
