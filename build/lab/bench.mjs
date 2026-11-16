import puppeteer from 'puppeteer-core';
import chromium from '@sparticuz/chromium';
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path';
const ROOT=process.cwd();
const MT={'.html':'text/html','.js':'text/javascript; charset=utf-8','.mjs':'text/javascript','.jpg':'image/jpeg','.png':'image/png','.ttf':'font/ttf'};
const server=http.createServer((req,res)=>{const f=path.join(ROOT,decodeURIComponent(req.url.split('?')[0]));
 if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){res.writeHead(404);return res.end();}
 res.writeHead(200,{'Content-Type':MT[path.extname(f)]||'application/octet-stream'}); fs.createReadStream(f).pipe(res);});
await new Promise(r=>server.listen(8120,'127.0.0.1',r));
const b=await puppeteer.launch({args:[...chromium.args,'--no-sandbox','--disable-dev-shm-usage','--hide-scrollbars','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'],
 executablePath:await chromium.executablePath(),headless:true,
 defaultViewport:{width:1080,height:1920,deviceScaleFactor:1},env:{...process.env,LD_LIBRARY_PATH:'/tmp/al/lib'}});
const p=await b.newPage();
p.on('console',m=>{ if(m.type()==='error') console.log('PAGE ERR:', m.text().slice(0,300)); });
p.on('pageerror',e=>console.log('PAGE EXC:', String(e).slice(0,300)));
await p.goto('http://127.0.0.1:8120/build/lab/scene3d.html',{waitUntil:'load'});
try{ await p.waitForFunction('window.__ready===true',{timeout:30000}); }catch(e){ console.log('NOT READY'); }
console.log('meta', await p.evaluate(()=>window.__meta||null));
const t0=Date.now();
for(let i=0;i<8;i++){ await p.evaluate(t=>window.__seek(t), i*0.25); const s=Date.now(); await p.screenshot({path:`/tmp/gl_${i}.png`}); if(i===0) console.log('first shot ms', Date.now()-s); }
console.log('avg ms/frame', ((Date.now()-t0)/8).toFixed(0));
await b.close(); server.close();
