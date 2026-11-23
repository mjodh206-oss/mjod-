import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const timeline = JSON.parse(fs.readFileSync(path.join(repo, 'scripts/remotion-v3/timeline-v3.json'), 'utf8'));
const mainSeconds = timeline.totalFrames / timeline.fps;
const shortSeconds = timeline.shortTotalFrames / timeline.fps;
const audioDir = path.join(repo, 'audio/episode-01');
const rendersDir = path.join(here, 'renders');
const ffmpeg = path.join(here, 'node_modules/@remotion/compositor-linux-x64-gnu/ffmpeg');
const python = process.env.PYTHON || 'python3';

function run(command, args) {
  const result = spawnSync(command, args, {cwd: repo, stdio: 'inherit'});
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} exited with status ${result.status}`);
}

fs.mkdirSync(rendersDir, {recursive: true});
run(python, [
  path.join(repo, 'video/synthesize-motion-sfx.py'),
  '--main-seconds', String(mainSeconds),
  '--short-seconds', String(shortSeconds),
]);

function finalize({short, seconds, voiceName, sfxName, silentName, outputName, title}) {
  const voiceWav = path.join(rendersDir, `${short ? 'short' : 'main'}-voice-v5.wav`);
  const mixedWav = path.join(rendersDir, `${short ? 'short' : 'main'}-mix-v5.wav`);
  const sfx = path.join(audioDir, sfxName);
  const output = path.join(repo, 'deliverables', outputName);
  const tmp = `${output}.tmp.mp4`;
  run(ffmpeg, [
    '-hide_banner', '-loglevel', 'warning', '-y', '-i', path.join(audioDir, voiceName),
    '-vn', '-ar', '24000', '-ac', '2', '-c:a', 'pcm_s16le', voiceWav,
  ]);
  run(python, [
    path.join(here, 'mix_audio.py'), '--voice', voiceWav, '--foley', sfx, '--output', mixedWav,
  ]);
  run(ffmpeg, [
    '-hide_banner', '-loglevel', 'warning', '-y',
    '-i', path.join(rendersDir, silentName), '-i', mixedWav,
    '-filter_complex', '[0:v:0]scale=in_range=pc:out_range=tv,format=yuv420p[v]',
    '-map', '[v]', '-map', '1:a:0',
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p',
    '-color_range', 'tv', '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709',
    '-c:a', 'aac', '-b:a', '192k', '-ar', '48000', '-t', String(seconds), '-movflags', '+faststart',
    '-metadata', `title=${title}`,
    '-metadata', 'comment=Remotion illustrated motion graphics; scene-specific original Foley, dynamically ducked under narration.',
    tmp,
  ]);
  fs.renameSync(tmp, output);
  console.log(`Finalized ${path.relative(repo, output)}`);
}

finalize({
  short: false, seconds: mainSeconds,
  voiceName: 'v3-final-mix.mp3', sfxName: 'v5-motion-sfx-main.wav',
  silentName: 'main-v5-silent.mp4', outputName: 'episode-01-main-motion-v5.mp4',
  title: 'ماري سيليست — موشن غرافيك V5',
});
finalize({
  short: true, seconds: shortSeconds,
  voiceName: 'v3-short-final-mix.mp3', sfxName: 'v5-motion-sfx-short.wav',
  silentName: 'short-v5-silent.mp4', outputName: 'episode-01-short-motion-v5.mp4',
  title: 'ماري سيليست — موشن غرافيك V5 عمودي',
});
