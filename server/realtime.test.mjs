import test from 'node:test';
import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {bridge,options,resampler,session} from './realtime.mjs';

class FakeSocket extends EventEmitter {
  readyState=1; bufferedAmount=0; messages=[];
  send(data,{binary=false}={}){this.messages.push(binary?data:JSON.parse(data));}
  incoming(message){this.emit('message',Buffer.from(JSON.stringify(message)),false);}
  close(code){this.readyState=3;this.closeCode=code;this.emit('close');}
}
function start({push=false}={}) {
  const client=new FakeSocket(),upstream=new FakeSocket();
  bridge(client,{model:'test',apiKey:'test',connect:()=>upstream});
  client.incoming({type:'start',push,options:{other:'tr',mode:push?'push':'travel'}});
  upstream.emit('open');
  return {client,upstream};
}
test('normalizes user options before provider use',()=>{
  assert.deepEqual(options({other:'__proto__',voice:'unknown',speed:'fast',pitch:'high'}),{other:'en',mode:'live',voice:'auto',speed:'fast',pitch:'high'});
  const config=session(options({mode:'medical',other:'de'}),false);
  assert.deepEqual(config.output_modalities,['audio']);
  assert.match(config.instructions,/German/);
  assert.match(config.instructions,/dosage/);
  assert.equal(config.audio.input.turn_detection.interrupt_response,true);
});
test('resampling is independent of packet boundaries',()=>{
  const input=Buffer.alloc(3200);for(let i=0;i<input.length;i+=2)input.writeInt16LE((i*7919)%63001-31500,i);
  const contiguous=resampler()(input), transform=resampler();
  assert.deepEqual(Buffer.concat([transform(input.subarray(0,1278)),transform(input.subarray(1278))]),contiguous);
  assert.ok(Math.abs(contiguous.length-4800)<=2);
});
test('live session relays PCM and state, not transcript, and allows interruption',()=>{
  const {client,upstream}=start();
  upstream.incoming({type:'session.updated'});
  client.emit('message',Buffer.alloc(1280),true);
  assert.equal(upstream.messages[1].type,'input_audio_buffer.append');
  upstream.incoming({type:'response.output_audio_transcript.delta',delta:'must not leak'});
  upstream.incoming({type:'input_audio_buffer.speech_stopped'});
  upstream.incoming({type:'response.output_audio.delta',item_id:'item_1',delta:'AQIDBA=='});
  upstream.incoming({type:'input_audio_buffer.speech_started'});
  client.incoming({type:'truncate',item:'item_1',ms:20});
  assert.deepEqual(client.messages,[{type:'ready'},{type:'translating'},{type:'speaking',item:'item_1'},Buffer.from([1,2,3,4]),{type:'interrupt'},{type:'listening'}]);
  assert.equal(upstream.messages.at(-1).audio_end_ms,20);
  client.close();assert.equal(upstream.readyState,3);
});
test('push-to-talk buffers 16 kHz input and commits only after release and readiness',()=>{
  const {client,upstream}=start({push:true});
  for(let i=0;i<3;i++)client.emit('message',Buffer.alloc(1280),true);
  client.incoming({type:'release'});
  assert.equal(upstream.messages.length,1);
  assert.equal(upstream.messages[0].session.audio.input.turn_detection,null);
  upstream.incoming({type:'session.updated'});
  assert.deepEqual(upstream.messages.slice(1).map(m=>m.type),Array(3).fill('input_audio_buffer.append').concat('input_audio_buffer.commit','response.create'));
  client.close();
});
test('blank push and malformed PCM fail safely',()=>{
  const a=start({push:true});a.upstream.incoming({type:'session.updated'});a.client.incoming({type:'release'});
  assert.equal(a.client.messages.at(-1).type,'empty');a.client.close();
  const b=start();b.client.emit('message',Buffer.alloc(1279),true);
  assert.equal(b.client.closeCode,1008);assert.equal(b.upstream.readyState,3);
});

test('directed mode emits translated PCM while microphone frames are still arriving',()=>{
  const client=new FakeSocket(),upstream=new FakeSocket();let url;
  bridge(client,{model:'test',apiKey:'test',connect:u=>{url=u;return upstream;}});
  client.incoming({type:'start',options:{other:'fr',mode:'stream'}});
  assert.match(url,/\/realtime\/translations\?model=gpt-realtime-translate$/);
  upstream.emit('open');
  assert.equal(upstream.messages[0].session.audio.output.language,'fr');
  assert.equal(upstream.messages[0].session.instructions,undefined);
  upstream.incoming({type:'session.updated'});
  client.emit('message',Buffer.alloc(1280),true);
  assert.equal(upstream.messages.at(-1).type,'session.input_audio_buffer.append');
  const voice=Buffer.alloc(9600);for(let i=0;i<voice.length;i+=2)voice.writeInt16LE(i%4===0?2400:-2400,i);
  upstream.incoming({type:'session.output_transcript.delta',delta:'not visible'});
  upstream.incoming({type:'session.output_audio.delta',delta:voice.toString('base64')});
  assert.deepEqual(client.messages,[{type:'ready'},{type:'speaking',item:'stream_1'},voice]);
  client.emit('message',Buffer.alloc(1280),true);
  const quiet=Buffer.alloc(9600).toString('base64');
  for(let i=0;i<3;i++)upstream.incoming({type:'session.output_audio.delta',delta:quiet});
  assert.deepEqual(client.messages.at(-1),{type:'done',complete:true});
  assert.ok(!upstream.messages.some(msg=>msg.type==='response.create'));
  client.close();
});

test('unsupported directed output falls back to bidirectional interpretation',()=>{
  const client=new FakeSocket(),upstream=new FakeSocket();let url;
  bridge(client,{model:'test',apiKey:'test',connect:u=>{url=u;return upstream;}});
  client.incoming({type:'start',options:{other:'tr',mode:'stream'}});
  assert.match(url,/\/realtime\?model=test$/);
  upstream.emit('open');assert.match(upstream.messages[0].session.instructions,/Turkish/);
  client.close();
});
