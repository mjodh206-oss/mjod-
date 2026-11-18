import { createServer } from 'node:http';
import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
import { timingSafeEqual } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { WebSocketServer } from 'ws';
import { bridge } from './realtime.mjs';

const root=fileURLToPath(new URL('../app/src/main/assets/',import.meta.url));
const privacy=fileURLToPath(new URL('./privacy.html',import.meta.url));
const apk=fileURLToPath(new URL('../artifacts/instant-voice-interpreter.apk',import.meta.url));
const key=process.env.OPENAI_API_KEY;
const access=process.env.RELAY_ACCESS_TOKEN;
const model=process.env.REALTIME_MODEL || 'gpt-realtime';
const maxClients=Number(process.env.RELAY_MAX_SESSIONS || 6);
const relay=Boolean(key && access && access.length>=24 && Number.isSafeInteger(maxClients) && maxClients>0);
const mime={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.svg':'image/svg+xml','.jpg':'image/jpeg'};
const headers={'Cache-Control':'no-cache','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Permissions-Policy':'microphone=(self)'};
const server=createServer(async(req,res)=>{
  let url;try{url=new URL(req.url,'http://local').pathname;}catch{res.writeHead(400,headers).end();return;}
  if(!['GET','HEAD'].includes(req.method)){res.writeHead(405,{Allow:'GET, HEAD'}).end();return;}
  if(url==='/health'){res.writeHead(200,{...headers,'Content-Type':'application/json'}).end(req.method==='HEAD'?undefined:JSON.stringify({translation:relay}));return;}
  const file=url==='/download/app.apk'?apk:url==='/privacy'?privacy:url==='/'?resolve(root,'index.html'):
    /^\/(?:flags\/)?[a-z.]+$/.test(url)&&mime[extname(url)]?resolve(root,'.'+url):null;
  if(!file){res.writeHead(404,headers).end();return;}
  if(file!==apk&&file!==privacy&&!file.startsWith(root)){res.writeHead(404,headers).end();return;}
  try{
    const details=await stat(file);if(!details.isFile())throw Error();
    const type=file===apk?{'Content-Type':'application/vnd.android.package-archive','Content-Disposition':'attachment; filename="instant-voice-interpreter.apk"'}:{'Content-Type':mime[extname(file)]};
    res.writeHead(200,{...headers,...type,'Content-Length':details.size});if(req.method==='HEAD'){res.end();return;}
    const stream=createReadStream(file);stream.on('error',()=>res.destroy());res.on('close',()=>stream.destroy());stream.pipe(res);
  }catch{res.writeHead(404,headers).end();}
});
const wss=new WebSocketServer({noServer:true,maxPayload:4096,perMessageDeflate:false});
function matches(token) { if(typeof token!=='string'||!access)return false; const a=Buffer.from(token),b=Buffer.from(`Bearer ${access}`);return a.length===b.length&&timingSafeEqual(a,b); }
server.on('upgrade',(req,socket,head)=>{
  const reject=code=>socket.end(`HTTP/1.1 ${code}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
  if(req.url!=='/relay')return reject('404 Not Found');
  if(!relay)return reject('503 Service Unavailable');
  if(!matches(req.headers.authorization))return reject('401 Unauthorized');
  if(wss.clients.size>=maxClients)return reject('429 Too Many Requests');
  wss.handleUpgrade(req,socket,head,ws=>bridge(ws,{apiKey:key,model}));
});
server.listen(Number(process.env.PORT || 3000),'0.0.0.0');
