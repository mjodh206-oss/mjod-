import { authorize, issue } from './access.mjs';

export function invitePage({origin,admin,secret}){
  let base;try{base=new URL(origin);}catch{return null;}
  if(base.protocol!=='https:'||base.username||base.password||base.pathname!=='/'||base.search||base.hash||!admin||admin.length<32||!secret||secret.length<32)return null;
  return (req,res)=>{
    const headers={'Cache-Control':'no-store','Content-Type':'text/html; charset=utf-8','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'; frame-ancestors 'none'; base-uri 'none'"};
    let password='';const credentials=req.headers.authorization;
    if(typeof credentials==='string'&&/^Basic [A-Za-z0-9+/=]{1,512}$/.test(credentials)){
      const raw=Buffer.from(credentials.slice(6),'base64').toString('utf8');
      if(raw.startsWith('operator:'))password=raw.slice(9);
    }
    if(!authorize(`Bearer ${password}`,{access:admin})){
      res.writeHead(401,{...headers,'WWW-Authenticate':'Basic realm="Interpreter operator", charset="UTF-8"'}).end();return;
    }
    const link=`${base.origin}/pair#${issue(secret)}`;
    res.writeHead(200,headers);
    res.end(req.method==='HEAD'?undefined:`<!doctype html><html dir="rtl" lang="ar"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>تفعيل المترجم الصوتي الفوري</title><main><h1>تفعيل المترجم الصوتي الفوري</h1><p><a href="${link}">افتح رابط تفعيل التطبيق على هاتف أندرويد</a></p><p>صالح لسبعة أيام. أبقِ الرابط خاصاً. تأكّد من تثبيت التطبيق قبل فتحه.</p><p><a href="/download/app.apk">تحميل APK للأندرويد</a></p><p>الصوت من <a href="https://elevenlabs.io/">ElevenLabs</a> عند استخدام مسار ElevenLabs.</p></main></html>`);
  };
}
