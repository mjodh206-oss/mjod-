import {issue} from './access.mjs';
try {
  const base=new URL(process.argv[2]);
  if(base.protocol!=='https:'||base.username||base.password||base.pathname!=='/'||base.search||base.hash)throw Error();
  const ttl=process.argv[3]===undefined?undefined:Number(process.argv[3])*3600;
  const grant=issue(process.env.RELAY_PROVISIONING_SECRET,ttl);
  console.log(`${base.origin}/pair#${grant}`);
}catch{
  console.error('Usage: RELAY_PROVISIONING_SECRET=… npm run issue-link -- https://your-server.example [hours up to 720]');
  process.exitCode=1;
}
