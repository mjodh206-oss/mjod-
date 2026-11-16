import puppeteer from 'puppeteer-core';
import chromium from '@sparticuz/chromium';
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path';
const ROOT=process.cwd();
const server=http.createServer((req,res)=>{const f=path.join(ROOT,decodeURIComponent(req.url.split('?')[0]));
 if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){res.writeHead(404);return res.end();}
 const ext=path.extname(f); const mt={'.html':'text/html','.jpg':'image/jpeg','.png':'image/png','.ttf':'font/ttf'}[ext]||'application/octet-stream';
 res.writeHead(200,{'Content-Type':mt}); fs.createReadStream(f).pipe(res);});
await new Promise(r=>server.listen(8113,'127.0.0.1',r));
const b=await puppeteer.launch({args:[...chromium.args,'--no-sandbox','--disable-dev-shm-usage','--hide-scrollbars'],
 executablePath:await chromium.executablePath(),headless:true,
 defaultViewport:{width:1080,height:1920,deviceScaleFactor:1},env:{...process.env,LD_LIBRARY_PATH:'/tmp/al/lib'}});
const p=await b.newPage();
const SEL=['.aurora','#grain','#dust','.shadeTop','.shadeBot','.vignette','#ui','#prog','#flash','#fade','#chart'];
const cases=['base',...SEL];
for(const c of cases){
  await p.goto('http://127.0.0.1:8113/build/motion/index.html',{waitUntil:'load'});
  await p.waitForFunction('window.__ready===true');
  await p.evaluate(()=>window.__seek(1.2));
  const info=await p.evaluate((sel,c)=>{
    document.querySelectorAll(sel.join(',')).forEach(e=>e.style.display='none');
    if(c!=='base'){document.querySelectorAll(c).forEach(e=>e.style.display='');}
    const st=document.querySelector('.shadeTop');
    return {bg:getComputedStyle(st).backgroundImage.slice(0,60), rect:st.getBoundingClientRect().height};
  },SEL,c);
  await new Promise(r=>setTimeout(r,40));
  await p.screenshot({path:`/tmp/d3_${c.replace(/[#.]/g,'')}.png`});
  if(c==='base')console.log(info);
}
await b.close(); server.close();
