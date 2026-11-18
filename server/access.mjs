import { createHmac, randomBytes, timingSafeEqual } from 'node:crypto';

const maxLife=30*24*3600; // seconds
const signature=(body,secret)=>createHmac('sha256',secret).update(`interpreter-relay-v1:${body}`).digest('base64url');
export function issue(secret,ttl=7*24*3600,now=Math.floor(Date.now()/1000)){
  if(typeof secret!=='string'||secret.length<32||!Number.isSafeInteger(ttl)||ttl<60||ttl>maxLife)throw new Error('Invalid provisioning secret or expiry');
  const body=`${now+ttl}.${randomBytes(18).toString('base64url')}`;
  return `v1.${body}.${signature(body,secret)}`;
}
function match(provided,expected){
  if(typeof provided!=='string'||typeof expected!=='string'||expected.length===0)return false;
  const a=Buffer.from(provided),b=Buffer.from(expected);return a.length===b.length&&timingSafeEqual(a,b);
}
export function authorize(header,{secret,access},now=Math.floor(Date.now()/1000)){
  if(typeof header!=='string'||!header.startsWith('Bearer ')||header.length>350)return false;
  const token=header.slice(7);
  if(match(token,access))return true;
  if(typeof secret!=='string'||secret.length<32)return false;
  const fields=/^v1\.(\d{9,11})\.([A-Za-z0-9_-]{24})\.([A-Za-z0-9_-]{43})$/.exec(token);
  if(!fields)return false;
  const deadline=Number(fields[1]);
  return deadline>now && deadline<=now+maxLife && match(fields[3],signature(`${fields[1]}.${fields[2]}`,secret));
}
