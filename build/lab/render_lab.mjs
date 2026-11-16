import puppeteer from 'puppeteer-core';
import chromium from '@sparticuz/chromium';
import { spawn } from 'node:child_process';
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path';
const ROOT=process.cwd();
const argv=Object.fromEntries(process.argv.slice(2).map(a=>{const m=a.match(/^--([^=]+)=(.*)$/);return m?[m[1],m[2]]:[a.replace(/^--/,''),true]}));
const PAGE=argv.page, OUT=argv.out, DUR=Number(argv.dur||3), FPS=Number(argv.fps||30), AUDIO=argv.audio||'';
const MT={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.mjs':'text/javascript','.jpg':'image/jpeg','.png':'image/png','.ttf':'font/ttf','.wav':'audio/wav'};
const server=http.createServer((req,res)=>{const f=path.join(ROOT,decodeURIComponent(req.url.split('?')[0]));
 if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){res.writeHead(404);return res.end();}
 res.writeHead(200,{'Content-Type':MT[path.extname(f)]||'application/octet-stream','Accept-Ranges':'bytes'});
 fs.createReadStream(f).pipe(res);});
await new Promise(r=>server.listen(8130,'127.0.0.1',r));
const browser=await puppeteer.launch({args:[...chromium.args,'--no-sandbox','--disable-dev-shm-usage','--hide-scrollbars',
  '--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--font-render-hinting=none','--force-color-profile=srgb'],
  executablePath:await chromium.executablePath(),headless:true,
  defaultViewport:{width:1080,height:1920,deviceScaleFactor:1},env:{...process.env,LD_LIBRARY_PATH:'/tmp/al/lib'}});
const page=await browser.newPage();
page.on('pageerror',e=>console.log('EXC',String(e).slice(0,200)));
await page.goto(`http://127.0.0.1:8130/${PAGE}`,{waitUntil:'load'});
await page.evaluate(()=>Promise.all([...document.images].map(i=>i.complete?1:new Promise(r=>{i.onload=i.onerror=r}))));
await page.evaluate(()=>document.fonts.ready);
await page.waitForFunction('window.__ready===true',{timeout:30000});
const ffmpegArgs=['-hide_banner','-loglevel','error','-y','-f','image2pipe','-vcodec','png','-framerate',String(FPS),'-i','pipe:0'];
if(AUDIO) ffmpegArgs.push('-i',AUDIO);
ffmpegArgs.push('-c:v','libx264','-preset','slow','-crf','18','-pix_fmt','yuv420p',
 '-colorspace','bt709','-color_primaries','bt709','-color_trc','bt709','-color_range','tv');
if(AUDIO) ffmpegArgs.push('-c:a','aac','-b:a','192k','-shortest');
ffmpegArgs.push('-movflags','+faststart',OUT);
const ff=spawn('ffmpeg',ffmpegArgs,{stdio:['pipe','inherit','inherit']});
const t0=Date.now(); let n=0;
for(let t=0;t<DUR;t+=1/FPS){
  await page.evaluate(tt=>window.__seek(tt),t);
  const buf=await page.screenshot({type:'png',optimizeForSpeed:true,captureBeyondViewport:false});
  if(!ff.stdin.write(buf)) await new Promise(r=>ff.stdin.once('drain',r));
  n++;
}
ff.stdin.end();
await new Promise(r=>ff.on('close',r));
await browser.close(); server.close();
console.log(`DONE ${OUT} frames=${n} in ${((Date.now()-t0)/1000).toFixed(0)}s`);
