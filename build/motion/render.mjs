// Chromium-based frame renderer: drives index.html deterministically and pipes frames to ffmpeg.
import puppeteer from 'puppeteer-core';
import chromium from '@sparticuz/chromium';
import { spawn } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(process.cwd());
const argv = Object.fromEntries(process.argv.slice(2).map(a => {
  const m = a.match(/^--([^=]+)=(.*)$/); return m ? [m[1], m[2]] : [a.replace(/^--/, ''), true];
}));
const FPS = 30, DUR = 44.33;
const FPORT = Number(argv.port || 8100);
const OUT = argv.out || path.join(ROOT, 'output/money_shorts_v2.mp4');
const FROM = Number(argv.from ?? 0), TO = Number(argv.to ?? DUR);
const STILLS = argv.stills ? String(argv.stills).split(',').map(Number) : null;

const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css',
  '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.ttf': 'font/ttf',
  '.woff2': 'font/woff2', '.json': 'application/json' };

const server = http.createServer((req, res) => {
  const p = decodeURIComponent(req.url.split('?')[0]);
  const file = path.join(ROOT, p);
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404); return res.end('nope');
  }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(FPORT, '127.0.0.1', r));

const browser = await puppeteer.launch({
  args: [...chromium.args, '--no-sandbox', '--disable-dev-shm-usage', '--hide-scrollbars',
         '--font-render-hinting=none', '--force-color-profile=srgb'],
  executablePath: await chromium.executablePath(),
  headless: true,
  defaultViewport: { width: 1080, height: 1920, deviceScaleFactor: 1 },
  env: { ...process.env, LD_LIBRARY_PATH: '/tmp/al/lib' },
});
const page = await browser.newPage();
await page.goto(`http://127.0.0.1:${FPORT}/build/motion/index.html`, { waitUntil: 'load' });
await page.evaluate(() => Promise.all([...document.images].map(i =>
  i.complete ? 1 : new Promise(r => { i.onload = i.onerror = r; }))));
await page.evaluate(() => document.fonts.ready);
await page.waitForFunction('window.__ready === true', { timeout: 30000 });
console.log('page ready');

const shot = async (t, file) => {
  await page.evaluate(tt => window.__seek(tt), t);
  return page.screenshot({ type: 'png', optimizeForSpeed: true, captureBeyondViewport: false, path: file });
};

if (STILLS) {
  fs.mkdirSync(path.join(ROOT, 'build/motion/stills'), { recursive: true });
  for (const t of STILLS) {
    await shot(t, path.join(ROOT, `build/motion/stills/t_${t}.png`));
    console.log('still', t);
  }
  await browser.close(); server.close(); process.exit(0);
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
const ff = spawn('ffmpeg', [
  '-hide_banner', '-loglevel', 'error', '-y',
  '-f', 'image2pipe', '-vcodec', 'png', '-framerate', String(FPS), '-i', 'pipe:0',
  '-i', path.join(ROOT, 'build/mix.wav'),
  '-af', 'loudnorm=I=-14:TP=-1.2:LRA=11',
  '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p',
  '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
  '-c:a', 'aac', '-b:a', '192k', '-ar', '48000',
  '-movflags', '+faststart', '-shortest', OUT,
], { stdio: ['pipe', 'inherit', 'inherit'] });

const t0 = Date.now();
let n = 0;
for (let t = FROM; t < TO; t += 1 / FPS) {
  const buf = await shot(t);
  if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
  n++;
  if (n % 60 === 0) {
    const el = (Date.now() - t0) / 1000;
    console.log(`frame ${n} (${t.toFixed(1)}s) — ${(n / el).toFixed(1)} fps, eta ${((TO - t) / (n / el)).toFixed(0)}s`);
  }
}
ff.stdin.end();
await new Promise(r => ff.on('close', r));
await browser.close(); server.close();
console.log(`DONE ${OUT} in ${((Date.now() - t0) / 1000).toFixed(0)}s`);
