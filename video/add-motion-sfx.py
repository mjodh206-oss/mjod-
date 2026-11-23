#!/usr/bin/env python3
"""Mix scene-synced procedural Foley beneath the existing V4 narration.

The video stream is copied without re-encoding. A sidechain compressor ducks the
Foley against the narration; an output limiter catches only transient peaks.
"""
from __future__ import annotations
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
FFMPEG = os.environ.get('FFMPEG_PATH', 'ffmpeg')


def mux(video: Path, sfx: Path) -> None:
    temp = video.with_name(video.stem + '.with-sfx.tmp.mp4')
    graph = (
        '[1:a]aresample=48000,aformat=channel_layouts=stereo[sfx];'
        '[sfx][0:a:0]sidechaincompress=threshold=0.04:ratio=8:attack=12:release=250:makeup=1[ducked];'
        '[0:a:0][ducked]amerge=inputs=2,pan=stereo|c0=c0+c2|c1=c1+c3,'
        'alimiter=limit=0.95,aresample=48000[a]'
    )
    cmd = [
        FFMPEG, '-hide_banner', '-loglevel', 'warning', '-y',
        '-i', str(video), '-i', str(sfx), '-filter_complex', graph,
        '-map', '0:v:0', '-map', '[a]', '-c:v', 'copy', '-c:a', 'aac',
        '-b:a', '192k', '-ar', '48000', '-shortest', '-movflags', '+faststart',
        '-metadata:s:a:0', 'language=ara', str(temp),
    ]
    subprocess.run(cmd, check=True)
    os.replace(temp, video)
    print(f'Added ducked Foley to {video.relative_to(ROOT)}')


def main() -> None:
    # Always start from clean animation renders so repeated runs never stack Foley.
    subprocess.run(['node', str(ROOT / 'video/render-motion.mjs'), 'all'], cwd=ROOT, check=True)
    generator = ROOT / 'video/synthesize-motion-sfx.py'
    subprocess.run([sys.executable, str(generator)], cwd=ROOT, check=True)
    audio = ROOT / 'audio/episode-01'
    mux(ROOT / 'deliverables/episode-01-main-motion.mp4', audio / 'v4-motion-sfx-main.wav')
    mux(ROOT / 'deliverables/episode-01-short-motion.mp4', audio / 'v4-motion-sfx-short.wav')

if __name__ == '__main__':
    main()
