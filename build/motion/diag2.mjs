import puppeteer from 'puppeteer-core';
import chromium from '@sparticuz/chromium';
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path';
const ROOT=process.cwd();
const server=http.createServer((req,res)=>{const f=path.join(ROOT,decodeURIComponent(req.url.split('?')[0]));
 if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){res.writeHead(404);return res.end();}
 const ext=path.extname(f); const mt={'.html':'text/html','.jpg':'image/jpeg','.png':'image/png','.ttf':'font/ttf'}[ext]||'application/octet-stream';
 res.writeHead(200,{'Content-Type':mt}); fs.createReadStream(f).pipe(res);});
await new Promise(r=>server.listen(8112,'127.0.0.1',r));
const b=await puppeteer.launch({args:[...chromium.args,'--no-sandbox','--disable-dev-shm-usage','--hide-scrollbars'],
 executablePath:await chromium.executablePath(),headless:true,
 defaultViewport:{width:1080,height:1920,deviceScaleFactor:1},env:{...process.env,LD_LIBRARY_PATH:'/tmp/al/lib'}});
const p=await b.newPage();
const HIDE=`document.querySelectorAll('.aurora,#grain,#dust,.shadeTop,.shadeBot,.vignette,#ui,#prog,#flash,#fade,#chart').forEach(e=>e.style.visibility='hidden')`;
const SHOW={
 nothing:``,
 aurora:`.aurora{visibility:visible}`,
 dust:`#dust{visibility:visible}`,
 shadeTop:`.shadeTop{visibility:visible}`,
 shadeBot:`.shadeBot{visibility:visible}`,
 vignette:`.vignette{visibility:visible}`,
 chart:`#chart{visibility:visible}`,
 grain:`#grain{visibility:visible}`,
};
for(const [k,css] of Object.entries(SHOW)){
  await p.goto('http://127.0.0.1:8112/build/motion/index.html',{waitUntil:'load'});
  await p.waitForFunction('window.__ready===true');
  await p.evaluate(()=>window.__seek(1.2));
  await p.evaluate((h,cs)=>{eval(h); if(cs){const s=document.createElement('style');s.textContent=cs;document.head.appendChild(s);}},HIDE,css);
  await new Promise(r=>setTimeout(r,50));
  await p.screenshot({path:`/tmp/d2_${k}.png`});
}
await b.close(); server.close();
