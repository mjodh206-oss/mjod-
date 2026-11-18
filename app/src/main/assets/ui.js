'use strict';
const $ = id => document.getElementById(id);
const native = window.VoiceApp;
const credit = 'تم تطوير وتنفيذ هذا التطبيق بواسطة أمجد حبيب';
const langs = {ar:'العربية',en:'الإنجليزية',tr:'التركية',fr:'الفرنسية',de:'الألمانية',zh:'الصينية الماندرين'};
const streamTargets=['en','fr','de','zh'];
const modes = {stream:['wave','بث متزامن من العربية إلى اللغة الأخرى'],live:['timeline','محادثة مستمرة'], face:['people','وجهاً لوجه'], push:['hand','اضغط باستمرار للتحدث'], travel:['travel','وضع السفر'], business:['case','وضع الأعمال'], medical:['medical','الوضع الطبي']};
const defaults = {other:'en',reverse:false,mode:'live',voice:'auto',speed:'normal',pitch:'normal',route:'speaker'};
let opts = {...defaults};
try {
  const saved=JSON.parse(localStorage.getItem('voice-settings') || '{}');
  const allowed={other:['en','tr','fr','de','zh'],mode:Object.keys(modes),voice:['auto','male','female'],speed:['slow','normal','fast'],pitch:['low','normal','high'],route:['speaker','headphones'],reverse:[true,false]};
  for(const k in allowed) if(allowed[k].includes(saved[k])) opts[k]=saved[k];
} catch (_) {}
let active=false, amplitude=0, kind='idle', focusBack, previewStream, previewAudio, previewReading, previewRun=0, pushHeld=false;
const mic = $('micButton'), canvas=$('visual'), ctx=canvas.getContext('2d');
const stateNames={idle:'جاهز',connecting:'جارٍ الاتصال',listening:'أستمع إليك',translating:'جارٍ الترجمة',speaking:'النطق',error:'تعذّر بدء الترجمة'};
const quick=['live','stream','push','travel'];
const icon = i => `<svg aria-hidden="true"><use href="#${i}"/></svg>`;
const buzz = () => { if(!native && navigator.vibrate) navigator.vibrate(14); };
function say(text) {
  $('announcement').textContent=text;
  if(native) { native.say(text); return; }
  if(!window.speechSynthesis) return;
  const msg=new SpeechSynthesisUtterance(text); msg.lang='ar';
  const voice=speechSynthesis.getVoices().find(v=>v.lang.toLowerCase().startsWith('ar'));
  if(voice) msg.voice=voice;
  speechSynthesis.cancel(); speechSynthesis.speak(msg);
}
function state(code, running=active) {
  if(!Object.prototype.hasOwnProperty.call(stateNames,code)) return;
  const previous=kind;
  kind=code; active=running; $('app').dataset.state=code;
  $('micIcon').setAttribute('href',running && opts.mode!=='push'?'#stop':'#mic');
  mic.setAttribute('aria-label',running && opts.mode!=='push'?'أوقف الترجمة':opts.mode==='push'?'اضغط باستمرار للتحدث':'ابدأ الترجمة');
  mic.setAttribute('aria-pressed',String(running));
  if(code!==previous) $('announcement').textContent=stateNames[code];
}
window.studio = {
  state, level: n=>{amplitude=Math.max(0,Math.min(1,Number(n)||0));},
  quality: q=>{if(['good','weak','off'].includes(q)) $('connection').dataset.quality=q;},
  command: cmd=>{ if(cmd.startsWith('language:') && ['en','tr','fr','de','zh'].includes(cmd.slice(9))){opts.other=cmd.slice(9);save(false);} if(cmd==='slow' || cmd==='fast'){opts.speed=cmd; save(false);} },
  pair: showPair,
  back: ()=>{if(!$('sheetLayer').hidden){closeSheet();return true;} return false;}
};
function save(sync=true){try{localStorage.setItem('voice-settings', JSON.stringify(opts));}catch(_){} if(sync && native) native.configure(JSON.stringify(opts)); render();}
function swap(speak=true){opts.reverse=!opts.reverse;save();if(speak) say(`${langs[opts.reverse?opts.other:'ar']}، ${langs[opts.reverse?'ar':opts.other]}`);}
function chooseMode(key){
  if(key==='stream' && !streamTargets.includes(opts.other)){say('البث المتزامن بهذا الوضع لا يدعم التركية. استخدم وضع المحادثة المستمرة للترجمة التركية.');return false;}
  if(opts.mode!==key && (active || pushHeld)) halt();opts.mode=key;save();state(kind);
  say(key==='stream'?`بث متزامن من العربية إلى ${langs[opts.other]}. يتبع الصوت أسلوب المتحدث.`:modes[key][1]);return true;
}
function button(svg,label,action,classes='icon-button',selected){const b=document.createElement('button'); b.className=classes;b.type='button';b.setAttribute('aria-label',label);if(selected!==undefined)b.setAttribute('aria-pressed',String(selected));b.innerHTML=icon(svg);b.addEventListener('click',action);return b;}
function render(){
  const ids=opts.reverse?[opts.other,'ar']:['ar',opts.other];
  ['primaryLanguage','secondaryLanguage'].forEach((id,k)=>{const el=$(id);el.querySelector('.flag').dataset.flag=ids[k];el.querySelector('use').setAttribute('href', ids[k]==='ar'?'#speaker':'#chevron');el.setAttribute('aria-label',ids[k]==='ar'?langs.ar:`${langs[ids[k]]}، اختر لغة أخرى`);});
  const nav=$('quickModes');
  if(!nav.children.length) for(const name of quick){const b=button(...modes[name],()=>chooseMode(name),'mode',false);b.dataset.mode=name;nav.append(b);}
  for(const b of nav.children) b.setAttribute('aria-pressed',String(b.dataset.mode===opts.mode || (b.dataset.mode==='travel' && ['business','medical'].includes(opts.mode))));
  $('routeIcon').setAttribute('href',`#${opts.route==='speaker'?'speaker':'headphones'}`);
  $('routeButton').setAttribute('aria-label',opts.route==='speaker'?'مكبر الصوت، بدّل المخرج':'سماعات الرأس، بدّل المخرج');
}
function closeSheet(){ $('sheetLayer').hidden=true; $('app').removeAttribute('inert');$('app').removeAttribute('aria-hidden'); if(focusBack && focusBack.isConnected)focusBack.focus(); }
function show(label,content){const visible=!$('sheetLayer').hidden; if(!visible) focusBack=document.activeElement;const box=$('sheetContent');box.textContent='';content(box);$('sheet').setAttribute('aria-label',label);$('sheetLayer').hidden=false;$('app').setAttribute('inert','');$('app').setAttribute('aria-hidden','true');$('closeSheet').focus();}
function languages(){show('اختيار اللغة',box=>{const grid=document.createElement('div');grid.className='sheet-grid'; for(const code of Object.keys(langs).filter(x=>x!=='ar')){const b=button('wave',langs[code],()=>{opts.other=code;if(opts.mode==='stream'&&!streamTargets.includes(code)){if(active)halt();opts.mode='live';save();closeSheet();say('التركية، وضع المحادثة المستمرة');return;}save();closeSheet();say(langs[code]);},'icon-button',code===opts.other);b.innerHTML=`<span class="flag" data-flag="${code}"></span>`;grid.append(b);}box.append(grid);});}
function settings(){show('الإعدادات',box=>{
  const groups=[['wave','الصوت','voice',[['timeline','تلقائي','auto'],['mic','صوت رجالي','male'],['speaker','صوت نسائي','female']]],['play','السرعة','speed',[['slow','بطيء','slow'],['play','عادي','normal'],['fast','سريع','fast']]],['pitch','طبقة الصوت','pitch',[['slow','طبقة منخفضة','low'],['timeline','طبقة عادية','normal'],['fast','طبقة مرتفعة','high']]],['headphones','مخرج الصوت','route',[['speaker','مكبر الصوت','speaker'],['headphones','سماعات الرأس','headphones']]]];
  for(const [symbol,title,key,items] of groups){const r=document.createElement('div');r.className='row';r.setAttribute('role','group');r.setAttribute('aria-label',title);r.innerHTML=icon(symbol);r.firstChild.classList.add('row-icon');for(const [sym,label,value] of items){const b=button(sym,label,()=>{opts[key]=value;save();for(const peer of r.querySelectorAll('button'))peer.setAttribute('aria-pressed',String(peer===b));say(label);},'mode',opts[key]===value);r.append(b);}box.append(r);}
  const tools=document.createElement('div');tools.className='sheet-tools';tools.append(button('swap','اللغات',languages),button('people','أوضاع المحادثة',allModes),button('info','حول التطبيق',about));if(native)tools.append(button('shield','إلغاء ربط خادم الترجمة',unlink));box.append(tools);
});}
function allModes(){show('أوضاع المحادثة',box=>{const grid=document.createElement('div');grid.className='sheet-grid';for(const key of Object.keys(modes))grid.append(button(...modes[key],()=>{if(chooseMode(key))closeSheet();},'icon-button',opts.mode===key));box.append(grid);});}
function about(){show('حول التطبيق',box=>{const a=document.createElement('div');a.className='about';a.innerHTML=`<img src="mark.svg" alt=""><p>${credit}</p>`;a.append(button('speaker','استمع للحقوق',()=>say(credit)),button('shield','استمع لملخص الخصوصية',()=>say('يُرسل صوتك إلى خادم الترجمة أثناء الجلسة فقط، ولا يحفظ التطبيق تسجيلات على جهازك. أوقف الميكروفون متى شئت.')));box.append(a);});}
function showPair(challenge,host){
  const guide=`خادم الترجمة: ${host}. يُرسل صوت المحادثة إلى هذا الخادم عند الترجمة. تابع فقط إذا كنت تثق به.`;
  show('تأكيد ربط خادم الترجمة',box=>{const row=document.createElement('div');row.className='sheet-tools';
    row.append(button('speaker','استمع إلى شرح الربط',()=>say(guide)),button('shield','أوافق على ربط الخادم',()=>{native.pair(challenge);closeSheet();}));box.append(row);});
  say(guide);
}
function unlink(){show('تأكيد إلغاء الربط',box=>{const r=document.createElement('div');r.className='sheet-tools';r.append(button('close','إلغاء ربط الخادم',()=>{native.unlink();halt();closeSheet();}));box.append(r);});say('اضغط علامة الإغلاق في وسط اللوحة لتأكيد إلغاء ربط خادم الترجمة.');}
function consent(){show('إذن الميكروفون',box=>{const row=document.createElement('div');row.className='sheet-tools';row.append(button('speaker','استمع إلى سبب طلب الإذن',permissionGuide),button('mic','متابعة إلى إذن الميكروفون',()=>{closeSheet();native.requestMic();}));box.append(row);});permissionGuide();}
function permissionGuide(){say('لبدء الترجمة، نحتاج إذن الميكروفون لإرسال صوتك إلى خادم الترجمة. لا يحفظ التطبيق تسجيلات على جهازك. اضغط رمز الميكروفون مرة أخرى للمتابعة.');}
function route(){opts.route=opts.route==='speaker'?'headphones':'speaker';save();say(opts.route==='speaker'?'مكبر الصوت':'سماعات الرأس عند توصيلها');}
function updateQuality(){if(!native) window.studio.quality(navigator.onLine?'good':'off');}
async function simulateInput(){
  const run=++previewRun;state('connecting',true);
  try {
    if(!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) throw new Error('mic');
    const stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true,autoGainControl:true}});
    if(run!==previewRun){stream.getTracks().forEach(t=>t.stop());return;}
    previewStream=stream; previewAudio=new (window.AudioContext||window.webkitAudioContext)();
    const source=previewAudio.createMediaStreamSource(stream);previewReading=previewAudio.createAnalyser();previewReading.fftSize=256;source.connect(previewReading);state('listening',true);buzz();
    say('هذه معاينة للواجهة واختبار للميكروفون فقط. الترجمة الصوتية تتطلب ربط التطبيق بخادم الترجمة.');
  } catch (_) {if(run!==previewRun)return;halt();state('error',false);say('لم نتمكن من الوصول إلى الميكروفون في المعاينة.');}
}
function start(push=false){if(native){if(!native.hasMic()){pushHeld=false;consent();return;}state('connecting',true);native.start(JSON.stringify(opts),push);}else simulateInput();}
function halt(){
  if(native) native.stop();
  previewRun++;if(previewStream)previewStream.getTracks().forEach(t=>t.stop());if(previewAudio)previewAudio.close().catch(()=>{});
  previewStream=previewAudio=previewReading=null;amplitude=0;state('idle',false);
}
function release(){if(!pushHeld)return;pushHeld=false;if(native) native.release();else halt();}
mic.addEventListener('click',e=>{if(opts.mode==='push'){if(e.detail===0 && !pushHeld){if(active) releaseOrStop(); else {pushHeld=true;start(true);}}return;}if(active)halt();else start();});
function releaseOrStop(){if(pushHeld)release();else halt();}
mic.addEventListener('pointerdown',e=>{if(opts.mode!=='push'||e.button!==0)return;if(active){halt();return;}pushHeld=true;mic.setPointerCapture(e.pointerId);start(true);});
for(const evt of ['pointerup','pointercancel','lostpointercapture']) mic.addEventListener(evt,release);
mic.addEventListener('contextmenu',e=>e.preventDefault());
$('primaryLanguage').onclick=()=>opts.reverse?languages():say(langs.ar);
$('secondaryLanguage').onclick=()=>opts.reverse?say(langs.ar):languages();
$('swapButton').onclick=()=>swap();$('settingsButton').onclick=settings;$('closeSheet').onclick=closeSheet;$('scrim').onclick=closeSheet;
$('routeButton').onclick=route;$('repeatButton').onclick=()=>native?native.repeat():say('لا توجد ترجمة صوتية سابقة في المعاينة.');
$('connection').onclick=()=>say({off:'الاتصال بخادم الترجمة غير متاح',weak:'الاتصال ضعيف',good:native?'الاتصال متاح':'الإنترنت متصل. هذه معاينة للواجهة.'}[$('connection').dataset.quality]||'جارٍ فحص الاتصال');
window.addEventListener('online',updateQuality);window.addEventListener('offline',updateQuality);
window.addEventListener('pagehide',()=>{if(!native)halt();});
window.addEventListener('keydown',e=>{if(e.key==='Escape'&&!$('sheetLayer').hidden)closeSheet();if(e.key==='Tab'&&!$('sheetLayer').hidden){const b=[...$('sheet').querySelectorAll('button')];if(e.shiftKey&&document.activeElement===b[0]){e.preventDefault();b[b.length-1].focus();}else if(!e.shiftKey&&document.activeElement===b[b.length-1]){e.preventDefault();b[0].focus();}}});
updateQuality();save(false);
if(native){$('splash').hidden=false;native.ready();setTimeout(()=>{$('splash').hidden=true;},2150);}
let w=0,h=0,smooth=0,prevTime=0;const sample=new Uint8Array(256);const reduce=matchMedia('(prefers-reduced-motion: reduce)');
const colors={idle:[224,163,108],connecting:[111,155,255],listening:[105,164,255],translating:[255,190,105],speaking:[111,235,173],error:[237,129,145]};
function fit(){const box=canvas.getBoundingClientRect(),d=Math.min(2,devicePixelRatio||1);w=box.width;h=box.height;canvas.width=Math.round(w*d);canvas.height=Math.round(h*d);ctx.setTransform(d,0,0,d,0,0);}
window.addEventListener('resize',fit);fit();
function frame(time){requestAnimationFrame(frame);if(document.hidden || time-prevTime<(reduce.matches?180:14))return;prevTime=time;
  if(previewReading){previewReading.getByteTimeDomainData(sample);let sum=0;for(const n of sample)sum+=((n-128)/128)**2;amplitude=Math.min(1,Math.sqrt(sum/sample.length)*6);}
  smooth+=(amplitude-smooth)*.16;const t=reduce.matches?0:time/1700,y=h*.5;
  const rgb=colors[kind], rgba=a=>`rgba(${rgb.join(',')},${a})`;ctx.clearRect(0,0,w,h);
  if(w<1 || h<1)return;const orb=mic.offsetWidth/2;const fade=x=>Math.max(0,Math.sin(Math.PI*x/w))**.82;
  const gr=ctx.createLinearGradient(0,0,w,0);gr.addColorStop(0,'transparent');gr.addColorStop(.24,'#c58de766');gr.addColorStop(.5,rgba(.9));gr.addColorStop(.76,'#c58de766');gr.addColorStop(1,'transparent');
  const gl=ctx.createRadialGradient(w/2,y,orb*.75,w/2,y,Math.max(Math.min(h*.5,w*.47),orb*.8));gl.addColorStop(0,rgba(.12));gl.addColorStop(1,'transparent');ctx.fillStyle=gl;ctx.fillRect(0,0,w,h);
  for(let i=0;i<Math.min(104,Math.round(w/6));i++){
    const x= i*w/Math.min(104,Math.round(w/6));const d=Math.abs(x-w/2);if(d<orb*.93)continue;
    const env=fade(x);const wave=(Math.sin(i*.29+t)*.55+Math.cos(i*.63-t*.7)*.3+Math.sin(i*1.69+t*.8)*.15);
    const length=2+env*(8+Math.abs(wave)*(56+smooth*115));ctx.strokeStyle=rgba(env*(.13+Math.abs(wave)*.36));ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(x,y-length/2);ctx.lineTo(x,y+length/2);ctx.stroke();
  }
  ctx.strokeStyle=gr;
  for(let j=0;j<6;j++){ctx.globalAlpha=j===0?.8:.35;ctx.lineWidth=j===0?1.15:.65;ctx.beginPath();
    for(let x=0;x<=w;x+=3){const k=x/w,env=fade(x);const spread=22+smooth*81;const dy=env*(Math.sin(k*29-j*.51-t*.8)*spread*.73+Math.sin(k*55+j*.96+t*.52)*spread*.31)*(1+j*.14);if(!x)ctx.moveTo(x,y+dy);else ctx.lineTo(x,y+dy);}ctx.stroke();}
  ctx.globalAlpha=1;
  for(let i=0;i<57;i++){const x=((i*137.628)%1000)/1000*w,dy=Math.sin(i*66.4)*(23+(i%6)*12),a=fade(x)*(.13+(i%7)*.067);if(Math.abs(x-w/2)<orb*.88 && Math.abs(dy)<orb*.82)continue;ctx.fillStyle=rgba(a);ctx.beginPath();ctx.arc(x,y+dy+(reduce.matches?0:Math.sin(t+i)*3),i%11===0?1.55:.75,0,Math.PI*2);ctx.fill();}
}
requestAnimationFrame(frame);
