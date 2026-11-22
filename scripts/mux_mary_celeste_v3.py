#!/usr/bin/env python3
"""Mux Remotion V3 visuals with the voice-prioritized AAC final mix."""
from pathlib import Path
import subprocess
import imageio_ffmpeg
ROOT=Path(__file__).resolve().parents[1]
FF=imageio_ffmpeg.get_ffmpeg_exe()

def mux(silent,audio,out,title):
    silent=ROOT/silent;audio=ROOT/audio;out=ROOT/out
    cmd=[FF,'-hide_banner','-loglevel','error','-y','-i',str(silent),'-i',str(audio),
         '-map','0:v:0','-map','1:a:0','-vf','scale=in_range=full:out_range=tv:flags=lanczos,format=yuv420p',
         '-c:v','libx264','-preset','medium','-crf','19','-pix_fmt','yuv420p','-color_range','tv',
         '-colorspace','bt709','-color_primaries','bt709','-color_trc','bt709','-c:a','aac','-b:a','192k',
         '-ar','48000','-shortest','-movflags','+faststart','-metadata',f'title={title}',
         '-metadata','comment=Remotion editorial motion graphics; illustrative images, not archival footage.',str(out)]
    subprocess.run(cmd,check=True)
    print(f'Wrote {out} ({out.stat().st_size/1024/1024:.1f} MiB)')
if __name__=='__main__':
    mux('Mary-Celeste-main-v3-silent.mp4','audio/episode-01/v3-final-mix.mp3','Mary-Celeste-main-v3.mp4','ماري سيليست — النسخة التحريرية V3')
    mux('Mary-Celeste-short-v3-silent.mp4','audio/episode-01/v3-short-final-mix.mp3','Mary-Celeste-short-v3.mp4','ماري سيليست — الشورت V3')
