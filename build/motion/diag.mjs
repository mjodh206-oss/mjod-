import puppeteer from 'puppeteer-core';
import chromium from '@sparticuz/chromium';
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path';
const ROOT=process.cwd();
const server=http.createServer((req,res)=>{const f=path.join(ROOT,decodeURIComponent(req.url.split('?')[0]));
 if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){res.writeHead(404);return res.end();}
 const ext=path.extname(f); const mt={'.html':'text/html','.jpg':'image/jpeg','.png':'image/png','.ttf':'font/ttf'}[ext]||'application/octet-stream';
 res.writeHead(200,{'Content-Type':mt}); fs.createReadStream(f).pipe(res);});
await new Promise(r=>server.listen(8111,'127.0.0.1',r));
const b=await puppeteer.launch({args:[...chromium.args,'--no-sandbox','--disable-dev-shm-usage','--hide-scrollbars'],
 executablePath:await chromium.executablePath(),headless:true,
 defaultViewport:{width:1080,height:1920,deviceScaleFactor:1},env:{...process.env,LD_LIBRARY_PATH:'/tmp/al/lib'}});
const p=await b.newPage();
await p.goto('http://127.0.0.1:8111/build/motion/index.html',{waitUntil:'load'});
await p.waitForFunction('window.__ready===true');
await p.evaluate(()=>window.__seek(1.2));
const tests={
 all:`1`,
 noGrain:`document.getElementById('grain').style.display='none'`,
 noAurora:`document.querySelectorAll('.aurora').forEach(e=>e.style.display='none')`,
 noShade:`document.querySelectorAll('.shadeTop,.shadeBot,.vignette').forEach(e=>e.style.display='none')`,
 raw:`document.querySelectorAll('.aurora,#grain,#dust,.shadeTop,.shadeBot,.vignette,#ui,#prog,#flash,#fade,#chart').forEach(e=>e.style.display='none')`,
};
for(const [k,code] of Object.entries(tests)){
  await p.reload({waitUntil:'load'}); await p.waitForFunction('window.__ready===true');
  await p.evaluate(()=>window.__seek(1.2));
  if(k!=='all') await p.evaluate(c=>eval(c),code);
  await new Promise(r=>setTimeout(r,60));
  await p.screenshot({path:`/tmp/diag_${k}.png`});
  console.log('shot',k);
}
await b.close(); server.close();
