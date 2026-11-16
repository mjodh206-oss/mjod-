import puppeteer from 'puppeteer-core';
import chromium from '@sparticuz/chromium';
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path';
const ROOT=process.cwd();
const argv=Object.fromEntries(process.argv.slice(2).map(a=>{const m=a.match(/^--([^=]+)=(.*)$/);return m?[m[1],m[2]]:[a.replace(/^--/,''),true]}));
const PAGE=argv.page, TIMES=(argv.t||'1.6,6.5').split(',').map(Number);
const MT={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.mjs':'text/javascript','.jpg':'image/jpeg','.png':'image/png','.ttf':'font/ttf','.wav':'audio/wav'};
const server=http.createServer((req,res)=>{const f=path.join(ROOT,decodeURIComponent(req.url.split('?')[0]));
 if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){res.writeHead(404);return res.end();}
 res.writeHead(200,{'Content-Type':MT[path.extname(f)]||'application/octet-stream','Accept-Ranges':'bytes'});
 fs.createReadStream(f).pipe(res);});
await new Promise(r=>server.listen(8131,'127.0.0.1',r));
const browser=await puppeteer.launch({args:[...chromium.args,'--no-sandbox','--disable-dev-shm-usage','--hide-scrollbars',
  '--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--font-render-hinting=none','--force-color-profile=srgb'],
  executablePath:await chromium.executablePath(),headless:true,
  defaultViewport:{width:1080,height:1920,deviceScaleFactor:1},env:{...process.env,LD_LIBRARY_PATH:'/tmp/al/lib'}});
const page=await browser.newPage();
page.on('pageerror',e=>console.log('EXC',String(e).slice(0,300)));
await page.goto(`http://127.0.0.1:8131/${PAGE}`,{waitUntil:'load'});
await page.evaluate(()=>document.fonts.ready);
await page.waitForFunction('window.__ready===true',{timeout:30000});
for(const t of TIMES){
  await page.evaluate(tt=>window.__seek(tt),t);
  await new Promise(r=>setTimeout(r,80));
  const f=`/tmp/kt_${t.toFixed(1)}.png`;
  await page.screenshot({path:f,type:'png'});
  console.log('shot',t,f);
}
await browser.close(); server.close();
