import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const timeline = JSON.parse(fs.readFileSync(path.join(repo, 'scripts/remotion-v3/timeline-v3.json'), 'utf8'));
const fps = timeline.fps;
const mainSeconds = timeline.totalFrames / fps;
const shortSeconds = timeline.shortTotalFrames / fps;
const audioDir = path.join(repo, 'audio/episode-01');
const ffmpeg = path.join(here, 'node_modules/@remotion/compositor-linux-x64-gnu/ffmpeg');

function run(command, args) {
  const result = spawnSync(command, args, {cwd: repo, stdio: 'inherit'});
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} exited with status ${result.status}`);
}

run(process.env.PYTHON || 'python3', [
  path.join(repo, 'video/synthesize-motion-sfx.py'),
  '--main-seconds', String(mainSeconds),
  '--short-seconds', String(shortSeconds),
]);

function mux({silent, voice, sfx, output, seconds, title}) {
  const tmp = `${output}.tmp.mp4`;
  const graph = [
    '[0:v:0]scale=in_range=pc:out_range=tv,format=yuv420p[v]',
    '[1:a:0]aresample=48000,volume=0.95[voice]',
    '[2:a:0]aresample=48000,volume=0.18[sfx]',
    `[voice][sfx]amix=inputs=2:duration=first:dropout_transition=0:normalize=0,atrim=0:${seconds}[a]`,
  ].join(';');
  run(ffmpeg, [
    '-hide_banner', '-loglevel', 'warning', '-y',
    '-i', silent, '-i', voice, '-i', sfx,
    '-filter_complex', graph,
    '-map', '[v]', '-map', '[a]',
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p',
    '-color_range', 'tv', '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709',
    '-c:a', 'aac', '-b:a', '192k', '-ar', '48000', '-t', String(seconds), '-movflags', '+faststart',
    '-metadata', `title=${title}`,
    '-metadata', 'comment=Remotion vector motion graphics; illustrative reconstruction, not archival footage. Original procedural Foley mixed quietly beneath narration.',
    tmp,
  ]);
  fs.renameSync(tmp, output);
  console.log(`Finalized ${path.relative(repo, output)}`);
}

mux({
  silent: path.join(here, 'renders/main-silent.mp4'),
  voice: path.join(audioDir, 'v3-final-mix.mp3'),
  sfx: path.join(audioDir, 'v4-motion-sfx-main.wav'),
  output: path.join(repo, 'deliverables/episode-01-main-motion-v4.mp4'),
  seconds: mainSeconds,
  title: 'ماري سيليست — موشن غرافيك V4',
});
mux({
  silent: path.join(here, 'renders/short-silent.mp4'),
  voice: path.join(audioDir, 'v3-short-final-mix.mp3'),
  sfx: path.join(audioDir, 'v4-motion-sfx-short.wav'),
  output: path.join(repo, 'deliverables/episode-01-short-motion-v4.mp4'),
  seconds: shortSeconds,
  title: 'ماري سيليست — موشن غرافيك V4 عمودي',
});
