import {EventEmitter} from 'node:events';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {elevenProvider} from './eleven.mjs';

class Socket extends EventEmitter {
  readyState=1; bufferedAmount=0; sent=[];
  send(data,{binary=false}={}){this.sent.push(binary?data:JSON.parse(data));this.emit('sent');}
  receive(data){this.emit('message',Buffer.from(JSON.stringify(data)),false);}
  close(code){if(this.readyState===3)return;this.readyState=3;this.closeCode=code;this.emit('close');}
}
function fixture({push=false,ttsStatus=200,result,ttsSource}={}){
  const client=new Socket(),scribe=new Socket(),all=[];let wsURL;
  const bridge=elevenProvider({elevenKey:'server-eleven',geminiKey:'server-gemini',connect:u=>{wsURL=u;return scribe;},
    fetcher:async(url,init)=>{
      all.push({url,...init});
      if(url.includes('googleapis.com'))return Response.json({candidates:[{content:{parts:[{text:JSON.stringify(result||{kind:'translation',target:'tr',text:'Nasılsınız?'})}]}}]});
      return new Response(ttsSource?.()||new ReadableStream({start(c){c.enqueue(new Uint8Array([1,2,3]));c.enqueue(new Uint8Array([4,5,6]));c.close();}}),{status:ttsStatus,headers:{'content-type':'audio/pcm'}});
    }});
  bridge(client);client.receive({type:'start',push,options:{mode:push?'push':'live',other:'tr',voice:'male'}});
  scribe.receive({message_type:'session_started'});
  return {client,scribe,all,wsURL};
}
function waitFor(client,predicate){
  if(client.sent.some(predicate))return Promise.resolve();
  return new Promise((resolve,reject)=>{
    const timeout=setTimeout(()=>{client.off('sent',check);reject(Error('No expected output'));},500);
    function check(){if(client.sent.some(predicate)){clearTimeout(timeout);client.off('sent',check);resolve();}}
    client.on('sent',check);
  });
}

test('Scribe, Gemini and ElevenLabs stream only aligned PCM and events to Android',async()=>{
  const {client,scribe,all,wsURL}=fixture();
  assert.equal(new URL(wsURL).searchParams.get('commit_strategy'),'vad');
  client.emit('message',Buffer.alloc(1280),true);
  assert.equal(scribe.sent[0].message_type,'input_audio_chunk');
  scribe.receive({message_type:'partial_transcript',text:'كيف'});
  assert.equal(all.length,0);
  scribe.receive({message_type:'committed_transcript',text:'كيف حالك؟'});
  await waitFor(client,e=>e.type==='done');
  assert.equal(all.length,2);
  assert.equal(all[0].headers['x-goog-api-key'],'server-gemini');
  assert.match(all[0].url,/gemini-3\.1-flash-lite:generateContent$/);
  assert.equal(all[1].headers['xi-api-key'],'server-eleven');
  assert.match(all[1].url,/JBFqnCBsd6RMkjVDRZzb\/stream\?output_format=pcm_24000$/);
  assert.deepEqual(client.sent.filter(Buffer.isBuffer),[Buffer.from([1,2]),Buffer.from([3,4,5,6])]);
  assert.deepEqual(client.sent.filter(e=>!Buffer.isBuffer(e)).map(e=>e.type),['ready','translating','speaking','done']);
  assert.ok(!JSON.stringify(client.sent).includes('Nasılsınız'));
  assert.deepEqual(JSON.parse(all[1].body).language_code,'tr');
  client.close();assert.equal(scribe.readyState,3);
});

test('manual commit waits for release and Arabic commands do not spend TTS or translation quota',async()=>{
  const {client,scribe,all,wsURL}=fixture({push:true});
  assert.equal(new URL(wsURL).searchParams.get('commit_strategy'),'manual');
  for(let i=0;i<3;i++)client.emit('message',Buffer.alloc(1280),true);
  assert.ok(!scribe.sent.some(e=>e.commit));
  client.receive({type:'release'});assert.equal(scribe.sent.at(-1).commit,true);
  scribe.receive({message_type:'committed_transcript',text:'أعِد!'});
  assert.ok(client.sent.some(e=>e.type==='command'&&e.action==='repeat'));
  assert.equal(all.length,0);client.close();
});

test('quota does not silently fall back to a different voice provider',async()=>{
  const {client,scribe}=fixture({ttsStatus:429});
  scribe.receive({message_type:'committed_transcript',text:'مرحبا'});
  await waitFor(client,e=>e.type==='problem');
  assert.deepEqual(client.sent.at(-1),{type:'problem',reason:'quota'});
  assert.ok(!client.sent.some(Buffer.isBuffer));assert.equal(scribe.readyState,3);
});

test('barge-in discards pending speech chunks and does not report the cancelled turn as finished',async()=>{
  let controller;
  const {client,scribe,all}=fixture({ttsSource:()=>new ReadableStream({start(c){controller=c;c.enqueue(new Uint8Array([1,2]));}})});
  scribe.receive({message_type:'committed_transcript',text:'مرحباً'});
  await waitFor(client,Buffer.isBuffer);
  scribe.receive({message_type:'partial_transcript',text:'لحظة'});
  assert.equal(all[1].signal.aborted,true);
  controller.enqueue(new Uint8Array([3,4]));controller.close();
  await new Promise(resolve=>setImmediate(resolve));
  assert.deepEqual(client.sent.filter(Buffer.isBuffer),[Buffer.from([1,2])]);
  assert.ok(client.sent.some(e=>e.type==='interrupt'));
  assert.ok(!client.sent.some(e=>e.type==='done'));
  client.close();
});
