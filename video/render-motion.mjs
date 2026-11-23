import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const sharp = require(process.env.SHARP_MODULE || 'sharp');
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const FFMPEG = process.env.FFMPEG_PATH || 'ffmpeg';
const FPS = 24;
const ASS_FONT_DIR = '/usr/share/fonts/truetype/dejavu';
const GOLD = '#d8b675';
const OFFWHITE = '#f2eee5';
const INK = '#07131d';

function srtTime(s) {
  const [h, m, rest] = s.split(':');
  const [sec, ms] = rest.split(',');
  return (+h * 3600) + (+m * 60) + (+sec) + (+ms / 1000);
}
function assTime(t) {
  const cs = Math.max(0, Math.round(t * 100));
  const h = Math.floor(cs / 360000);
  const m = Math.floor((cs % 360000) / 6000);
  const s = Math.floor((cs % 6000) / 100);
  const c = cs % 100;
  return `${h}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}.${String(c).padStart(2,'0')}`;
}
function parseSrt(file) {
  return fs.readFileSync(file, 'utf8').trim().split(/\r?\n\s*\r?\n/).map(block => {
    const lines = block.split(/\r?\n/);
    const [start,end] = lines[1].split(' --> ');
    const text = lines.slice(2).join('\\N').replace(/\*\*/g, '').trim();
    return {start:srtTime(start),end:srtTime(end),text};
  });
}
function writeAss({file, cues, labels, width, height, vertical=false}) {
  const fontSize = vertical ? 29 : 32;
  const margin = vertical ? 170 : 38;
  const labelSize = vertical ? 21 : 23;
  const labelMargin = vertical ? 235 : 95;
  const header = `[Script Info]\nScriptType: v4.00+\nWrapStyle: 2\nScaledBorderAndShadow: yes\nPlayResX: ${width}\nPlayResY: ${height}\n\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Caption,DejaVu Sans,${fontSize},&H00F2EEE5,&H00FFFFFF,&HCC07131D,&HAA07131D,0,0,0,0,100,100,0,0,3,1.6,0,2,48,48,${margin},1\nStyle: Label,DejaVu Sans,${labelSize},&H0075B6D8,&H00FFFFFF,&HCC07131D,&HAA07131D,1,0,0,0,100,100,0,0,3,1,0,8,30,30,${labelMargin},1\nStyle: Title,DejaVu Sans,${vertical?53:52},&H0075B6D8,&H00FFFFFF,&HDD07131D,&HBB07131D,1,0,0,0,100,100,0,0,1,2,1,8,50,50,${vertical?820:540},1\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n`;
  const dialogue = cues.map(c => `Dialogue: 0,${assTime(c.start)},${assTime(c.end)},Caption,,0,0,0,,{\\fad(180,160)}${c.text}`).join('\n');
  const labelEvents = labels.map(x => `Dialogue: 1,${assTime(x.start)},${assTime(x.end)},Label,,0,0,0,,{\\fad(220,220)}${x.text}`).join('\n');
  const titleText=vertical?'سفينة بلا طاقم؟':'أين اختفى البحارة العشرة؟';
  const titleY=Math.round(height*(vertical?.25:.22));
  const title=`Dialogue: 2,0:00:00.00,${assTime(vertical?7:8)},Title,,0,0,0,,{\\an8\\pos(${Math.round(width/2)},${titleY})\\fad(400,300)}${titleText}`;
  fs.writeFileSync(file, header + dialogue + '\n' + labelEvents + '\n' + title + '\n');
}

const clamp=(v,a=0,b=1)=>Math.min(b,Math.max(a,v));
const smooth=(v)=>{v=clamp(v);return v*v*(3-2*v)};
const mix=(a,b,t)=>a+(b-a)*t;
const esc=(s)=>String(s).replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;');
const n=(x)=>Number(x.toFixed(2));
const hexMix=(a,b,t)=>{
  const parse=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
  const ca=parse(a),cb=parse(b),v=ca.map((x,i)=>Math.round(mix(x,cb[i],clamp(t))));
  return '#'+v.map(x=>x.toString(16).padStart(2,'0')).join('');
};

function cloud(x,y,scale,alpha=0.28){
  return `<g transform="translate(${n(x)} ${n(y)}) scale(${scale})" fill="#70818a" opacity="${alpha}"><path d="M-130 12 Q-122 -12 -91 -8 Q-68 -42 -29 -25 Q-4 -53 31 -28 Q67 -34 78 -4 Q113 -6 127 20 L126 38 L-130 38Z"/></g>`;
}
function wavePath(W,y,amp,phase,period=190){
  let d=`M -${period} ${n(y+Math.sin(phase)*amp)}`;
  const count=Math.ceil(W/period)+3;
  for(let i=0;i<count;i++){
    const x=i*period-period;
    const yy=y+Math.sin(phase+i*1.8)*amp;
    d+=` Q ${x+period*.25} ${n(yy-amp*1.8)} ${x+period*.5} ${n(y+Math.sin(phase+(i+.5)*1.8)*amp)} Q ${x+period*.75} ${n(yy+amp*1.5)} ${x+period} ${n(y+Math.sin(phase+(i+1)*1.8)*amp)}`;
  }
  return d;
}
function sailor(x,y,scale=1,pose=0,alpha=1){
  const arm=pose===1?`M0 -12 L-13 -31 M0 -12 L13 -28`:`M0 -12 L-13 0 M0 -12 L13 1`;
  return `<g transform="translate(${n(x)} ${n(y)}) scale(${scale})" opacity="${alpha}" stroke="#e8dec8" stroke-width="5" stroke-linecap="round" fill="none"><circle cx="0" cy="-29" r="7" fill="#e8dec8" stroke="none"/><path d="M0 -21 L0 5 ${arm} M0 5 L-10 22 M0 5 L10 22"/></g>`;
}
function barrel(x,y,r=1,rot=0){
  return `<g transform="translate(${n(x)} ${n(y)}) rotate(${rot}) scale(${r})"><path d="M-15 -24 Q-21 0 -16 25 Q0 33 16 25 Q21 0 15 -24 Q0 -31 -15 -24Z" fill="#765533" stroke="#d8b675" stroke-width="2"/><ellipse cy="-24" rx="15" ry="5" fill="#9a7749" stroke="#d8b675" stroke-width="2"/><path d="M-17 -10 Q0 -4 17 -10 M-17 14 Q0 21 17 14" fill="none" stroke="#c9a36b" stroke-width="3"/><ellipse cy="-24" rx="7" ry="2.5" fill="#30271c"/></g>`;
}
function ship(x,y,scale,roll,phase,{ghost=false,crew=0,sails=1}={}){
  const ghostOpacity=ghost?0.48:1;
  const wave=Math.sin(phase*2.2)*1.8;
  let people='';
  for(let i=0;i<crew;i++){
    const pos=[-170,-128,-86,-44,-2,40,82,124,166,208][i%10];
    const row=i<5?0:1;
    people+=sailor(pos,-7-row*13,0.56,Math.floor((phase+i)%2),0.92);
  }
  return `<g transform="translate(${n(x)} ${n(y+wave)}) rotate(${n(roll)}) scale(${scale})" opacity="${ghostOpacity}">
    <ellipse cx="0" cy="64" rx="260" ry="16" fill="#02080d" opacity=".65"/>
    <path d="M-248 0 Q-40 24 252 -7 L211 58 Q180 87 -192 67 Q-224 54 -248 0Z" fill="url(#hull)" stroke="#d2ae6c" stroke-width="4"/>
    <path d="M-228 15 Q0 43 232 5 M-211 35 Q0 59 220 29 M-190 52 Q0 72 205 46" fill="none" stroke="#94784e" stroke-width="3" opacity=".9"/>
    <path d="M-240 -4 L245 -9 L214 8 Q0 24 -224 12Z" fill="#534331" stroke="#d8b675" stroke-width="3"/>
    <rect x="-37" y="-32" width="92" height="31" rx="3" fill="#18252b" stroke="#b7945b" stroke-width="3"/><rect x="-8" y="-53" width="42" height="21" fill="#283137" stroke="#b7945b" stroke-width="2"/>
    <g stroke="#c4aa73" stroke-width="2.5" opacity=".8"><path d="M-112 -5 L-90 -305 M12 -11 L6 -350 M120 -7 L124 -228"/><path d="M-157 -245 L-32 -245 M-135 -166 L-28 -166 M-46 -296 L82 -296 M-46 -200 L78 -200 M75 -160 L180 -160"/><path d="M-90 -305 L-206 -2 M-90 -305 L118 -4 M6 -350 L-102 -4 M6 -350 L217 -5 M124 -228 L76 -4 M124 -228 L222 -5" stroke-width="1.5"/></g>
    <g fill="url(#sail)" stroke="#b8aa8e" stroke-width="2.5"><path d="M-148 -240 Q-95 -253 -34 -239 L-43 -169 Q-98 -177 -139 -166Z"/><path d="M-39 -291 Q15 -302 77 -291 L70 -205 Q19 -216 -34 -202Z"/><path d="M80 -155 Q128 -166 177 -154 L169 -95 Q128 -104 91 -94Z"/><path d="M-134 -161 Q-83 -171 -32 -159 L-37 -136 Q-87 -145 -133 -135Z" opacity=".82"/><path d="M-31 -196 Q17 -205 72 -195 L67 -173 Q18 -182 -30 -170Z" opacity=".82"/></g>
    <g transform="translate(6 -350) rotate(${n(Math.sin(phase*2)*6)})"><path d="M0 0 L45 3 L40 24 L0 22Z" fill="#a94f42"/><path d="M5 4 L35 18 M36 4 L5 18" stroke="#f0dfc2" stroke-width="2"/></g>
    <g fill="#d6b66f"><circle cx="-22" cy="-14" r="4"/><circle cx="24" cy="-14" r="4"/><circle cx="-3" cy="-40" r="3"/></g>
    ${people}
  </g>`;
}
function lifeboat(x,y,scale=1,phase=0){
  return `<g transform="translate(${n(x)} ${n(y+Math.sin(phase*2.4)*5)}) scale(${scale})"><path d="M-82 0 Q0 16 86 -1 L68 30 Q0 46 -69 28Z" fill="#60472e" stroke="#dfbd7a" stroke-width="3"/><path d="M-65 8 Q0 21 72 7 M-42 3 L-31 27 M-7 7 L3 31 M30 6 L38 28" fill="none" stroke="#c3a16b" stroke-width="2"/><path d="M-93 45 Q-40 38 0 45 T92 44" fill="none" stroke="#9db9bc" stroke-width="2" opacity=".7"/></g>`;
}
function personOnLadder(x,y,phase){
  const sway=Math.sin(phase*3)*3;
  return `<g transform="translate(${n(x+sway)} ${n(y)})" stroke="#e7dec9" stroke-width="5" stroke-linecap="round" fill="none"><circle cy="-27" r="7" fill="#e7dec9" stroke="none"/><path d="M0 -19 L0 4 M0 -10 L-12 -21 M0 -10 L12 -20 M0 4 L-9 20 M0 4 L10 20"/></g>`;
}
function mapOverlay(W,H,t,portrait){
  const x=W*.10,y=H*(portrait?.13:.12),w=W*.80,h=H*(portrait?.43:.63);
  const p=clamp((t-8)/22);
  const routeX=x+w*(.24+.48*p);
  return `<g>
    <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="20" fill="#d9c79e" fill-opacity=".94" stroke="#f0dda9" stroke-width="4"/>
    <rect x="${x+18}" y="${y+18}" width="${w-36}" height="${h-36}" rx="14" fill="none" stroke="#796a4c" stroke-opacity=".42" stroke-width="2"/>
    <path d="M${x+w*.04} ${y+h*.30} Q${x+w*.12} ${y+h*.18} ${x+w*.24} ${y+h*.27} L${x+w*.31} ${y+h*.44} Q${x+w*.27} ${y+h*.56} ${x+w*.15} ${y+h*.56} L${x+w*.07} ${y+h*.46}Z M${x+w*.66} ${y+h*.17} Q${x+w*.77} ${y+h*.14} ${x+w*.85} ${y+h*.26} L${x+w*.94} ${y+h*.44} Q${x+w*.88} ${y+h*.58} ${x+w*.78} ${y+h*.50} L${x+w*.70} ${y+h*.40}Z" fill="#9c9d83" stroke="#5d6e68" stroke-width="3" opacity=".82"/>
    <path d="M${x+w*.22} ${y+h*.44} C${x+w*.38} ${y+h*.22} ${x+w*.56} ${y+h*.72} ${x+w*.78} ${y+h*.42}" fill="none" stroke="#984f43" stroke-width="5" stroke-dasharray="${n(w*.56)} ${n(w*.6)}" stroke-dashoffset="${n(w*.56*(1-p))}"/>
    <circle cx="${x+w*.22}" cy="${y+h*.44}" r="8" fill="#a4493d"/><circle cx="${routeX}" cy="${y+h*(.47+.05*Math.sin(t))}" r="9" fill="#283b45" stroke="#f9e8c4" stroke-width="3"/>
    <g transform="translate(${x+w*.86} ${y+h*.74})" stroke="#65583f" fill="none" stroke-width="3"><circle r="34"/><path d="M0 -30 L7 0 L0 30 L-7 0Z M-30 0 L0 -7 L30 0 L0 7Z" fill="#9a4b40"/></g>
    <path d="M${x+w*.08} ${y+h*.77} Q${x+w*.18} ${y+h*.73} ${x+w*.28} ${y+h*.77} T${x+w*.48} ${y+h*.77}" fill="none" stroke="#7f7257" stroke-width="2" opacity=".4"/>
  </g>`;
}
function logbook(W,H,t,portrait){
  const x=W*.10,y=H*(portrait?.56:.14),w=W*.80,h=H*(portrait?.20:.30);
  const pulse=.75+.25*Math.sin(t*1.3);
  return `<g transform="rotate(${n(-2+Math.sin(t*.4))} ${x+w/2} ${y+h/2})"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="12" fill="#d8c59b" stroke="#f4e4bd" stroke-width="3"/><path d="M${x+w*.08} ${y+h*.28} H${x+w*.87} M${x+w*.08} ${y+h*.42} H${x+w*.82} M${x+w*.08} ${y+h*.56} H${x+w*.9} M${x+w*.08} ${y+h*.70} H${x+w*.61}" stroke="#70614b" stroke-width="3" opacity=".55"/><circle cx="${x+w*.76}" cy="${y+h*.55}" r="${n(22*pulse)}" fill="#a64d40" fill-opacity=".10" stroke="#a64d40" stroke-width="4"/><path d="M${x+w*.69} ${y+h*.55} l12 12 28 -32" fill="none" stroke="#a64d40" stroke-width="5"/></g>`;
}
function evidenceOverlay(W,H,t,portrait){
  const cx=W*.5,cy=H*(portrait?.42:.48),cw=W*(portrait?.82:.62),ch=H*(portrait?.32:.36);
  const items=[['01',-1],['02',1],['03',-.7]];
  let cards='';
  items.forEach(([num,r],i)=>{
    const xx=cx+((i-1)*(cw*.34));
    const yy=cy+Math.sin(t*.65+i)*H*.015;
    const alpha=clamp((t-((portrait?16:108)+i*2))/2);
    cards+=`<g transform="translate(${n(xx)} ${n(yy)}) rotate(${r*3})" opacity="${alpha}"><rect x="${-cw*.15}" y="${-ch*.34}" width="${cw*.30}" height="${ch*.68}" rx="12" fill="#d8c69f" stroke="#f2e0b6" stroke-width="3"/><path d="M${-cw*.11} ${-ch*.19} H${cw*.10} M${-cw*.11} ${-ch*.08} H${cw*.08} M${-cw*.11} ${ch*.04} H${cw*.11} M${-cw*.11} ${ch*.16} H${cw*.04}" stroke="#6a5e48" stroke-width="3" opacity=".55"/><circle cx="${cw*.08}" cy="${-ch*.19}" r="9" fill="#a4493d"/></g>`;
  });
  const magX=cx+Math.cos(t*.5)*cw*.36,magY=cy+Math.sin(t*.7)*ch*.5;
  return `<g>${cards}<g transform="translate(${n(magX)} ${n(magY)})"><circle r="${n(Math.min(W,H)*.085)}" fill="#a3ced0" fill-opacity=".10" stroke="#e7d4a7" stroke-width="8"/><path d="M${Math.min(W,H)*.06} ${Math.min(W,H)*.06} L${Math.min(W,H)*.15} ${Math.min(W,H)*.16}" stroke="#e7d4a7" stroke-width="15" stroke-linecap="round"/><circle r="${n(Math.min(W,H)*.062)}" fill="#d3a861" fill-opacity=".12"/></g></g>`;
}
function theoryOverlay(W,H,t,portrait,short=false){
  const phase=clamp((t-(short?30:150))/(short?13:65));
  const cx=W*.5, cy=H*(portrait?.48:.46), rad=Math.min(W,H)*(portrait?.29:.31);
  const labels=[];
  const symbols=[
    `<path d="M-36 18 Q0 8 36 18 M-26 11 V-20 M26 11 V-20 M-27 -20 L-26 -31 M26 -20 L27 -31" fill="none" stroke="#83b4bd" stroke-width="5"/><path d="M-20 5 Q0 0 20 5" fill="none" stroke="#7caeba" stroke-width="6"/>`,
    `<g fill="#d49f4c" opacity=".8"><circle cx="-20" cy="20" r="7"/><circle cx="0" cy="0" r="6"/><circle cx="21" cy="-19" r="5"/></g><path d="M-28 30 Q-11 12 0 -3 T30 -32" fill="none" stroke="#d8ae67" stroke-width="3" stroke-dasharray="5 6"/>`,
    `<path d="M-38 18 Q-5 12 35 -18 M-33 0 Q0 -4 38 -36 M-25 30 L-15 -18 M0 30 L5 -24 M25 28 L33 -32" fill="none" stroke="#9cc1c3" stroke-width="3"/><path d="M-22 -28 L-22 28 M22 -28 L22 28" stroke="#d6ba82" stroke-width="2" stroke-dasharray="4 5"/>`
  ];
  if(portrait){
    symbols.forEach((symbol,i)=>{
      const vis=smooth(phase*3-i*.55), x=W*.08, y=H*(.36+i*.15), w=W*.84, h=H*.125;
      labels.push(`<g transform="translate(${n((1-vis)*W*.08)} 0)" opacity="${vis}"><rect x="${x}" y="${n(y)}" width="${n(w)}" height="${n(h)}" rx="18" fill="#102633" fill-opacity=".92" stroke="${i===1?'#d5a24c':'#82afb8'}" stroke-width="3"/><circle cx="${n(x+w*.78)}" cy="${n(y+h*.5)}" r="${n(h*.34)}" fill="none" stroke="#d8b675" stroke-opacity=".4" stroke-width="2" stroke-dasharray="5 7" transform="rotate(${n(t*12+i*35)} ${n(x+w*.78)} ${n(y+h*.5)})"/><g transform="translate(${n(x+w*.78)} ${n(y+h*.5)}) scale(.62)">${symbol}</g><path d="M${n(x+w*.65)} ${n(y+h*.32)} H${n(x+w*.12)} M${n(x+w*.65)} ${n(y+h*.5)} H${n(x+w*.24)} M${n(x+w*.65)} ${n(y+h*.68)} H${n(x+w*.18)}" stroke="#c4d0ca" stroke-opacity=".48" stroke-width="3" stroke-linecap="round"/></g>`);
    });
    return `<g>${labels.join('')}</g>`;
  }
  symbols.forEach((symbol,i)=>{
    const x=W*(.24+.26*i), y=cy+(i===1?rad*.9:0), sc=.65+.35*smooth(phase*2-i*.36);
    labels.push(`<g transform="translate(${n(x)} ${n(y)}) scale(${sc})" opacity="${smooth(phase*2-i*.45)}"><circle r="${n(rad)}" fill="#102633" fill-opacity=".87" stroke="${i===1?'#d5a24c':'#82afb8'}" stroke-width="3"/><g>${symbol}</g><circle r="${n(rad+13+4*Math.sin(t))}" fill="none" stroke="#d8b675" stroke-opacity=".22" stroke-width="2" stroke-dasharray="7 11" transform="rotate(${n(t*8+i*25)})"/></g>`);
  });
  return `<g>${labels.join('')}</g>`;
}
function stormOverlay(W,H,t){
  let rain='';
  for(let i=0;i<58;i++){
    const x=((i*97+t*100*(1+(i%3)*.12))%(W+80))-40;
    const y=((i*137+t*220)%(H+120))-70;
    rain+=`<path d="M${n(x)} ${n(y)} l-13 34" stroke="#c6d8dc" stroke-opacity=".34" stroke-width="2"/>`;
  }
  const flash=(Math.sin(t*2.7)>0.995)?0.17:0;
  return `<rect width="${W}" height="${H}" fill="#05101b" fill-opacity=".28"/><g>${rain}</g><path d="M${W*.75} ${H*.1} l-25 75 33 -8 -38 92" fill="none" stroke="#ffe5a4" stroke-width="4" opacity="${flash}"/>`;
}
function pumpOverlay(W,H,t,portrait){
  const x=W*(portrait?.5:.79),y=H*(portrait?.7:.63),sc=Math.min(W,H)*.20;
  const bar=.5+.5*Math.sin(t*2.4);
  return `<g transform="translate(${n(x)} ${n(y)}) scale(${sc/180})"><rect x="-130" y="-50" width="260" height="112" rx="14" fill="#122632" stroke="#d8b675" stroke-width="4"/><path d="M-92 7 Q-50 -5 -8 7 T77 7" fill="none" stroke="#7db4bd" stroke-width="7"/><path d="M-69 7 V-${n(30+bar*20)} M-69 -${n(30+bar*20)} H36 M36 -${n(30+bar*20)} V7" fill="none" stroke="#d8b675" stroke-width="9" stroke-linecap="round"/><circle cx="-69" cy="-38" r="8" fill="#f1d495"/><circle cx="36" cy="-38" r="8" fill="#f1d495"/><path d="M-112 -68 l15 -15 15 15 M97 -68 l15 -15 15 15" fill="none" stroke="#d9bb7d" stroke-width="4" opacity=".65"/></g>`;
}
function sceneGroup(name,t,W,H,portrait){
  const horizon=H*.59;
  const cx=W*.5;
  const shipX=cx+W*(.025*Math.sin(t*.12));
  const shipY=horizon-H*.04;
  const inRange=(a,b,fade=3)=>smooth((t-a)/fade)*smooth((b-t)/fade);
  let out='';
  if(name==='map') out+=`<g opacity="${inRange(8,39)}">${mapOverlay(W,H,t,portrait)}</g>`;
  if(name==='storm'){
    const alpha=portrait?inRange(25,35):inRange(53,69);
    out+=`<g opacity="${alpha}">${stormOverlay(W,H,t)}${logbook(W,H,t,portrait)}</g>`;
  }
  if(name==='rescue'){
    const q=smooth((t-67)/20), rescueX=mix(-W*.12,W*.28,q);
    out+=`<g opacity="${inRange(66,90)}">${ship(rescueX,H*.64,.42,.5*Math.sin(t),t,{crew:0,sails:1})}<path d="M${rescueX-W*.08} ${H*.51} L${shipX} ${H*.63}" fill="none" stroke="#d7b675" stroke-width="2" stroke-dasharray="7 9" opacity=".5"/></g>`;
  }
  if(name==='crew'){
    if(portrait){
      const fade=1-smooth((t-11)/5);
      out+=`<g opacity="${inRange(8,17)}">`;
      for(let i=0;i<10;i++)out+=sailor(W*(.32+(i%5)*.09),H*.57-(i>=5?9:0),.32,Math.floor(t+i)%2,fade);
      out+=`<g opacity="${smooth((t-13)/3)}" fill="none" stroke="#d8b675" stroke-width="3" stroke-dasharray="7 8"><circle cx="${W*.78}" cy="${H*.67}" r="${H*.07}"/><path d="M${W*.78} ${H*.60} V${H*.74}"/></g></g>`;
    }else{
      const boatX=mix(W*1.13,W*.70,smooth((t-76)/11));
      out+=`<g opacity="${inRange(76,101)}"><path d="M${shipX+W*.22} ${H*.55} L${boatX} ${H*.67}" stroke="#d1b06e" stroke-dasharray="8 9" stroke-width="3" opacity=".7"/><path d="M${shipX+W*.18} ${H*.52} L${shipX+W*.18} ${H*.68} M${shipX+W*.205} ${H*.52} L${shipX+W*.205} ${H*.68}" stroke="#d3b16f" stroke-width="3"/>`;
      for(let k=0;k<7;k++)out+=`<path d="M${shipX+W*.18} ${H*(.52+k*.021)} H${shipX+W*.205}" stroke="#d3b16f" stroke-width="2"/>`;
      out+=lifeboat(boatX,H*.68,.72,t);
      for(let i=0;i<3;i++){
        const progress=clamp((t-(78+i*2.4))/4);
        const x=mix(boatX+W*.02,shipX+W*.18,progress);
        const y=mix(H*.64, H*.52+i*H*.013, progress);
        if(t>77+i*2.4)out+=personOnLadder(x,y,t+i);
      }
      out+=`<g fill="#e9ddb8" opacity=".7"><circle cx="${n(boatX-W*.05)}" cy="${H*.72}" r="3"/><circle cx="${n(boatX+W*.06)}" cy="${H*.73}" r="2"/></g></g>`;
    }
  }
  if(name==='inspection'){
    out+=`<g opacity="${portrait?inRange(16,27):inRange(84,114)}">${evidenceOverlay(W,H,t,portrait)}${pumpOverlay(W,H,t,portrait)}`;
    for(let i=0;i<5;i++){
      const yy=H*(.43-i*.045)+Math.sin(t*1.6+i)*H*.018;
      const xx=shipX-W*.13+i*W*.065;
      out+=`<circle cx="${n(xx)}" cy="${n(yy)}" r="${n(3+i%3)}" fill="#d7ad62" opacity=".34"/>`;
    }
    out+=`</g>`;
  }
  if(name==='inquiry'){
    out+=`<g opacity="${inRange(106,149)}">${evidenceOverlay(W,H,t,portrait)}`;
    out+=`<g transform="translate(${W*.5} ${H*.20})" opacity=".76"><path d="M-${W*.3} 0 H${W*.3}" stroke="#d4b36f" stroke-width="2" stroke-dasharray="5 9"/>`;
    for(let i=0;i<4;i++){
      const x=(i-1.5)*W*.14;
      out+=`<circle cx="${x}" cy="0" r="${n(Math.min(W,H)*.018)}" fill="#d9b675"/><path d="M${x} 0 V${H*.07}" stroke="#d9b675" stroke-width="2"/>`;
    }
    out+=`</g></g>`;
  }
  if(name==='theories'){
    const alpha=portrait?inRange(30,43):inRange(150,217);
    out+=`<g opacity="${alpha}">${theoryOverlay(W,H,t,portrait,portrait)}</g>`;
  }
  if(name==='outro'){
    const start=portrait?41:214;
    const q=smooth((t-start)/8);
    out+=`<g opacity="${smooth((t-start)/3)}"><circle cx="${W*.5}" cy="${H*.20}" r="${n(H*(.08+q*.04))}" fill="#e8c98e" fill-opacity=".08" stroke="#e0c182" stroke-opacity=".55" stroke-width="2"/><path d="M${W*.32} ${H*.30} Q${W*.5} ${H*.28} ${W*.68} ${H*.30}" fill="none" stroke="#e0c182" stroke-opacity=".6" stroke-width="2"/><path d="M${W*.36} ${H*.36} Q${W*.5} ${H*.34} ${W*.64} ${H*.36}" fill="none" stroke="#e0c182" stroke-opacity=".25" stroke-width="1.5"/></g>`;
  }
  return out;
}
function baselineShipPosition(t,mode,W,H){
  if(mode==='short'){
    if(t<9)return [W*.56,H*.56,.85];
    if(t<17)return [W*.50,H*.57,.73];
    if(t<26)return [W*.54,H*.58,.78];
    if(t<34)return [W*.45,H*.59,.80];
    if(t<41)return [mix(W*.46,W*.25,smooth((t-34)/7)),H*.59,mix(.80,.35,smooth((t-34)/7))];
    return [W*.28,H*.57,.36];
  }
  if(t<8)return [W*.57,H*.59,1.03];
  if(t<35)return [W*.48,H*.60,.67];
  if(t<56)return [W*.47,H*.59,1.00];
  if(t<78)return [W*.51,H*.60,1.0];
  if(t<96)return [mix(W*.58,W*.43,smooth((t-78)/18)),H*.59,.88];
  if(t<123)return [W*.47,H*.59,.94];
  if(t<150)return [W*.54,H*.60,.95];
  if(t<181)return [W*.5,H*.61,.76];
  if(t<215)return [W*.5,H*.63,.7];
  return [mix(W*.55,W*.26,smooth((t-215)/10)),H*.59,mix(.72,.38,smooth((t-215)/10))];
}
function svgFrame(t,mode,W,H){
  const portrait=mode==='short';
  const sceneNames=portrait?['hook','crew','inspection','storm','theories','outro']:['ghost','map','cargo','storm','rescue','crew','inspection','inquiry','theories','outro'];
  const phase=t*.7;
  const horizon=H*(portrait?.58:.58);
  const [sx,sy,ss]=baselineShipPosition(t,mode,W,H);
  const roll=Math.sin(t*1.6)*1.1+Math.sin(t*.43)*.5;
  const weather=!portrait&&t>=54&&t<79;
  const daylight=mode==='main'?smooth((t-52)/38):.18+.32*smooth((t-24)/17);
  const skyTop=hexMix('#08131f','#314751',daylight),skyMid=hexMix('#1c2d3a','#778078',daylight);
  const moonX=W*(.79+.02*Math.sin(t*.12)), moonY=H*.2;
  const cloudShift=(t*9)%(W+500);
  let svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><defs>
  <linearGradient id="sky" x2="0" y2="1"><stop stop-color="${skyTop}"/><stop offset=".52" stop-color="${skyMid}"/><stop offset="1" stop-color="#59605c"/></linearGradient>
  <linearGradient id="sea" x2="0" y2="1"><stop stop-color="#2d4d58"/><stop offset=".46" stop-color="#18313d"/><stop offset="1" stop-color="#07131d"/></linearGradient>
  <linearGradient id="hull" x2="0" y2="1"><stop stop-color="#4b4034"/><stop offset="1" stop-color="#111b22"/></linearGradient>
  <linearGradient id="sail" x2="0" y2="1"><stop stop-color="#e0d7c2"/><stop offset="1" stop-color="#978c77"/></linearGradient>
  <radialGradient id="moon"><stop stop-color="#e9d8b4" stop-opacity=".52"/><stop offset="1" stop-color="#c39b63" stop-opacity="0"/></radialGradient>
  <linearGradient id="fadeBottom" x2="0" y2="1"><stop stop-color="#061019" stop-opacity="0"/><stop offset="1" stop-color="#061019" stop-opacity=".7"/></linearGradient>
  <filter id="glow"><feGaussianBlur stdDeviation="18"/></filter>
  </defs>`;
  svg+=`<rect width="${W}" height="${H}" fill="url(#sky)"/>`;
  const cameraScale=1.018+.012*Math.sin(t*.09),cameraX=Math.sin(t*.14)*W*.008,cameraY=Math.cos(t*.11)*H*.006;
  svg+=`<g transform="translate(${n(W/2+cameraX)} ${n(H/2+cameraY)}) scale(${n(cameraScale)}) translate(${-W/2} ${-H/2})">`;
  svg+=`<circle cx="${n(moonX)}" cy="${n(moonY)}" r="${n(H*.12)}" fill="url(#moon)"/><circle cx="${n(moonX)}" cy="${n(moonY)}" r="${n(H*.027)}" fill="#e2d4b7" fill-opacity=".6"/>`;
  for(let i=0;i<16;i++){
    const x=((i*157+cloudShift*(i%2?1:-.36))%(W+280))-140;
    svg+=cloud(x,H*(.24+(i%4)*.05),.48+(i%3)*.17,.12+(i%3)*.03);
  }
  svg+=`<path d="M0 ${n(horizon)} Q${W*.28} ${n(horizon-H*.018)} ${W*.52} ${n(horizon)} T${W} ${n(horizon-H*.008)} L${W} ${H} L0 ${H}Z" fill="url(#sea)"/>`;
  for(let i=0;i<7;i++){
    const y=horizon+H*(.04+i*.067),alpha=.20-i*.016;
    svg+=`<path d="${wavePath(W,y,H*(.007+i*.001),phase+i*1.7,W*(.16+i*.014))}" fill="none" stroke="#9eb7b9" stroke-opacity="${alpha}" stroke-width="${i<2?3:2}"/>`;
  }
  // Far shore haze and moving silver reflections.
  for(let i=0;i<8;i++){
    const x=((i*207+t*(i%2?23:-14))%(W+80))-40;
    const y=horizon+H*(.03+(i%5)*.055);
    svg+=`<ellipse cx="${n(x)}" cy="${n(y)}" rx="${n(W*(.012+(i%3)*.006))}" ry="${n(H*.003)}" fill="#c7d2c9" opacity="${.12+(i%4)*.035}"/>`;
  }
  const shipGhost=mode==='main'&&t<8;
  svg+=ship(sx,sy,ss,roll,t,{ghost:shipGhost,crew:mode==='main'&&t>=35&&t<58?10:0});
  if(mode==='main'&&t>=33&&t<57){
    const fade=smooth((t-33)/2)*smooth((57-t)/2);
    for(let i=0;i<5;i++)svg+=barrel(sx-W*.14+i*W*.07,sy+H*.02+Math.sin(t*2+i)*3,.75,Math.sin(t+i)*2);
    svg+=`<g opacity="${fade*.45}" fill="none" stroke="#e2c38a" stroke-width="3"><path d="M${sx-W*.1} ${sy-H*.07} Q${sx-W*.18} ${sy-H*.22} ${sx-W*.08} ${sy-H*.31}"/><path d="M${sx+W*.07} ${sy-H*.06} Q${sx+W*.17} ${sy-H*.21} ${sx+W*.09} ${sy-H*.30}"/></g>`;
  }
  // A quiet second vessel emerges only when the rescue crew sights her.
  if(mode==='short'&&t>18&&t<31){
    const q=smooth((t-18)/12), rx=mix(-W*.2,W*.19,q);
    svg+=ship(rx,H*.64,.32,.3*Math.sin(t),t,{ghost:false});
  }
  // Selected story modules fade in/out over the uninterrupted animated sea.
  if(mode==='main'){
    const ranges=[['map',8,39],['storm',53,69],['rescue',66,90],['crew',76,101],['inspection',84,114],['inquiry',106,149],['theories',150,217],['outro',214,225]];
    for(const [name,a,b] of ranges)svg+=sceneGroup(name,t,W,H,portrait);
  } else {
    const ranges=[['crew',8,18],['inspection',16,27],['storm',25,35],['theories',33,43],['outro',41,45.5]];
    for(const [name,a,b] of ranges)svg+=sceneGroup(name,t,W,H,portrait);
  }
  if(weather){svg+=stormOverlay(W,H,t);}
  svg+=`</g>`;
  // Subtle camera vignette, grain-like flecks, and safe-area gradient.
  svg+=`<rect width="${W}" height="${H}" fill="url(#fadeBottom)"/>`;
  svg+=`<rect x="2" y="2" width="${W-4}" height="${H-4}" rx="${n(Math.min(W,H)*.022)}" fill="none" stroke="#d4b36e" stroke-opacity=".28" stroke-width="2"/>`;
  svg+=`<g fill="#eee5cf" opacity=".22">`;
  for(let i=0;i<19;i++){
    const x=(i*193+t*(i%2?12:-8))%W,y=(i*89+t*(i%3?6:-3))%H;
    svg+=`<circle cx="${n(x)}" cy="${n(y)}" r="${i%4===0?1.8:1}"/>`;
  }
  svg+=`</g></svg>`;
  return svg;
}

function makeLabels(mode){
  if(mode==='short')return [
    {start:0,end:8,text:'المحيط الأطلسي · ١٨٧٢ · إعادة بناء توضيحية'},
    {start:16,end:25,text:'السفينة بقيت طافية · قارب النجاة مفقود'},
    {start:25,end:34,text:'آخر سجل معروف · قرب جزر الأزور'},
    {start:34,end:43,text:'فرضيات — لا تفسير مثبت'},
    {start:42,end:45.5,text:'وراء الحكاية · قصص حقيقية… وما لم يُحكَ منها'},
  ];
  return [
    {start:0,end:8,text:'ماري سيليست · الأطلسي · ١٨٧٢ · إعادة بناء توضيحية'},
    {start:8,end:35,text:'٧ نوفمبر ١٨٧٢ · من نيويورك إلى جنوة'},
    {start:34,end:57,text:'١٬٧٠١ برميل من الكحول الصناعي · وفق سجل الشحنة'},
    {start:56,end:67,text:'٢٥ نوفمبر ١٨٧٢ · آخر ملاحظة معروفة في السجل'},
    {start:67,end:84,text:'٥ ديسمبر ١٨٧٢ · رصدتها سفينة دي غراتيا'},
    {start:82,end:103,text:'الوقائع: القارب مفقود · المضخة مفككة جزئيًا'},
    {start:90,end:107,text:'وُجد ماء في القاع · السفينة بقيت صالحة للإبحار'},
    {start:108,end:129,text:'التحقيق لم يثبت جريمة أو سببًا مؤكّدًا للمغادرة'},
    {start:128,end:154,text:'الرواية السينمائية ليست دليلًا تاريخيًا'},
    {start:153,end:218,text:'فرضيات منفصلة · لا واحدة منها حقيقة مثبتة'},
    {start:217,end:225.5,text:'وراء الحكاية · قصص حقيقية… وما لم يُحكَ منها'},
  ];
}

async function render({mode,duration,frames,width,height,audio,subtitle,output}){
  const isShort=mode==='short';
  const cues=parseSrt(subtitle);
  const ass=output.replace(/\.mp4$/,'.ass');
  writeAss({file:ass,cues,labels:makeLabels(mode),width,height,vertical:isShort});
  const filter=`subtitles=filename='${ass}':fontsdir='${ASS_FONT_DIR}',format=yuv420p`;
  const args=['-hide_banner','-loglevel','error','-y','-f','rawvideo','-pixel_format','rgba','-video_size',`${width}x${height}`,'-framerate',String(FPS),'-i','pipe:0','-i',audio,'-vf',filter,'-map','0:v:0','-map','1:a:0','-frames:v',String(frames),'-c:v','libx264','-preset','veryfast','-crf','19','-profile:v','high','-pix_fmt','yuv420p','-r',String(FPS),'-c:a','aac','-b:a',isShort?'128k':'192k','-ar','48000','-af','loudnorm=I=-16:TP=-1.5:LRA=10','-movflags','+faststart','-shortest',output];
  const ff=spawn(FFMPEG,args,{stdio:['pipe','ignore','pipe']});
  let err='';ff.stderr.on('data',b=>err+=b.toString());
  const failure=new Promise((_,reject)=>ff.on('error',reject));
  const close=new Promise((resolve,reject)=>ff.on('close',code=>code===0?resolve():reject(new Error(`ffmpeg exited ${code}: ${err.slice(-3000)}`))));
  const pixelCount=width*height*4;
  for(let i=0;i<frames;i++){
    const t=i/FPS;
    const raw=await sharp(Buffer.from(svgFrame(t,mode,width,height))).ensureAlpha().raw().toBuffer();
    if(raw.length!==pixelCount)throw new Error(`Unexpected frame size ${raw.length}, expected ${pixelCount}`);
    if(!ff.stdin.write(raw))await Promise.race([new Promise(resolve=>ff.stdin.once('drain',resolve)),failure]);
    if(i%240===0||i===frames-1)console.log(`${mode}: ${i}/${frames} (${(100*i/frames).toFixed(1)}%)`);
  }
  ff.stdin.end();
  await Promise.race([close,failure]);
  console.log(`wrote ${path.relative(ROOT,output)}`);
}

const mainMode={mode:'main',duration:225.5,frames:5409,width:1280,height:720,audio:path.join(ROOT,'audio/episode-01/episode-01-full-voiceover.mp3'),subtitle:path.join(ROOT,'deliverables/episode-01-subtitles-ar.srt'),output:path.join(ROOT,'deliverables/episode-01-main-motion.mp4')};
const shortMode={mode:'short',duration:45.5,frames:1092,width:720,height:1280,audio:path.join(ROOT,'audio/episode-01/04-short.mp3'),subtitle:path.join(ROOT,'deliverables/episode-01-short-ar.srt'),output:path.join(ROOT,'deliverables/episode-01-short-motion.mp4')};
const selected=process.argv[2]??'all';
if(selected==='preview'){
  const frames=[['main',0,1280,720],['main',61,1280,720],['main',80,1280,720],['main',110,1280,720],['main',135,1280,720],['main',190,1280,720],['main',220,1280,720],['short',13,720,1280],['short',20,720,1280],['short',38,720,1280],['short',43,720,1280]];
  for(const [mode,t,w,h] of frames){
    const out=path.join(ROOT,`deliverables/.preview-${mode}-${t}.png`);
    await sharp(Buffer.from(svgFrame(t,mode,w,h))).png().toFile(out);
    console.log(`preview ${mode} at ${t}s: ${out}`);
  }
}else if(selected==='main')await render(mainMode);
else if(selected==='short')await render(shortMode);
else {await render(mainMode);await render(shortMode);}
