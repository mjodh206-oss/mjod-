#!/usr/bin/env python3
import subprocess, re, os

FF = subprocess.run(['python3', '-c', 'import imageio_ffmpeg;print(imageio_ffmpeg.get_ffmpeg_exe())'],
                    capture_output=True, text=True).stdout.strip()
AUD = '/home/user/mjod-/shorts/audio'
FR = '/home/user/mjod-/shorts/frames'
OUT = '/home/user/mjod-/shorts/yt_short_7tips.mp4'
order = ['01_hook', '02_tip1', '03_tip2', '04_tip3', '05_tip4',
         '06_tip5', '07_tip6', '08_tip7', '09_cta']


def dur(f):
    out = subprocess.run([FF, '-i', f], capture_output=True, text=True).stderr
    m = re.search(r'Duration: (\d+):(\d+):(\d+\.\d+)', out)
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))


n = len(order)
durs = [dur(os.path.join(AUD, name + '.mp3')) for name in order]

args = [FF, '-y']
for i, name in enumerate(order):
    args += ['-framerate', '30', '-loop', '1', '-t', f'{durs[i]:.2f}',
             '-i', f'{FR}/scene_{i+1:02d}.png']
for name in order:
    args += ['-i', os.path.join(AUD, name + '.mp3')]

vlabels = []
for i in range(n):
    frames = max(1, round(durs[i] * 30))
    z = f"1+0.09*on/{frames}"
    vlabels.append(
        f"[{i}:v]scale=1200:2133,zoompan=z='{z}':d=1:"
        f"x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=1080x1920:fps=30[v{i}]")
alabels = ''.join(f'[{n + i}:a]' for i in range(n))
fc = (';'.join(vlabels) + ';' + ''.join(f'[v{i}]' for i in range(n)) +
      f'concat=n={n}:v=1:a=0[vout];' + alabels + f'concat=n={n}:v=0:a=1[aout]')
args += ['-filter_complex', fc, '-map', '[vout]', '-map', '[aout]',
         '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p',
         '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart', OUT]

print('durations:', [round(d, 2) for d in durs], 'total', round(sum(durs), 2))
print('assembling...')
r = subprocess.run(args, capture_output=True, text=True)
print('exit:', r.returncode)
print(r.stderr[-2000:])
