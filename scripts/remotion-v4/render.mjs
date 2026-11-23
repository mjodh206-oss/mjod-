import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

process.env.AWS_EXECUTION_ENV ||= 'AWS_Lambda_nodejs22.x';
const {default: chromium} = await import('@sparticuz/chromium');
const {bundle} = await import('@remotion/bundler');
const {renderMedia, renderStill, selectComposition} = await import('@remotion/renderer');

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const timeline = JSON.parse(fs.readFileSync(path.join(repo, 'scripts/remotion-v3/timeline-v3.json'), 'utf8'));
const serveUrl = await bundle({entryPoint: path.join(here, 'src/index.jsx')});
const browserExecutable = await chromium.executablePath();
const compositor = path.join(here, 'node_modules/@remotion/compositor-linux-x64-gnu/ffmpeg');
const short = process.argv.includes('--short');
const still = process.argv.includes('--still');
const id = short ? 'MaryCelesteShortMotionV5' : 'MaryCelesteMotionV5';
const output = path.join(here, 'renders', short ? 'short-v5-silent.mp4' : 'main-v5-silent.mp4');
const props = {cues: short ? timeline.shortCues : timeline.cues};
const composition = await selectComposition({
  serveUrl, id, inputProps: props, browserExecutable,
  chromiumOptions: {enableMultiProcessOnLinux: false},
});
if (still) {
  const frame = Number(process.env.FRAME ?? (short ? 480 : 2500));
  const jpg = path.join(here, 'renders', `${short ? 'short' : 'main'}-frame-${frame}.jpg`);
  fs.mkdirSync(path.dirname(jpg), {recursive: true});
  await renderStill({composition, serveUrl, output: jpg, frame, inputProps: props,
    browserExecutable, chromiumOptions: {enableMultiProcessOnLinux: false}, imageFormat: 'jpeg'});
  console.log(`Rendered preview frame ${frame}: ${jpg}`);
} else {
  fs.mkdirSync(path.dirname(output), {recursive: true});
  let last = -5;
  await renderMedia({
    composition, serveUrl, outputLocation: output, inputProps: props,
    browserExecutable, chromiumOptions: {enableMultiProcessOnLinux: false},
    ffmpegExecutable: compositor, codec: 'h264', audioCodec: 'aac', muted: true,
    concurrency: 2, crf: 18, x264Preset: 'medium', pixelFormat: 'yuv420p',
    onProgress: ({progress, renderedFrames}) => {
      const pct = Math.floor(progress * 100);
      if (pct >= last + 5) { last = pct; console.log(`${pct}% (${renderedFrames}/${composition.durationInFrames})`); }
    },
    metadata: {title: short ? 'ماري سيليست — موشن غرافيك V5 عمودي' : 'ماري سيليست — موشن غرافيك V5',
      comment: 'Remotion vector motion graphics; illustrative reconstruction, not archival footage.'},
  });
  console.log(`Rendered silent master: ${output} (${composition.durationInFrames} frames)`);
}
