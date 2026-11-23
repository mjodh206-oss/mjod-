import React from 'react';
import {AbsoluteFill, Composition, Easing, interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import '@fontsource/noto-sans-arabic/arabic-400.css';
import '@fontsource/noto-sans-arabic/arabic-600.css';
import '@fontsource/noto-sans-arabic/arabic-700.css';
import '@fontsource/noto-naskh-arabic/arabic-400.css';

const GOLD='#D8B675', CREAM='#F2EEE5', INK='#07131D', SEA='#17303B', TEAL='#88B7BF';
const clamp={extrapolateLeft:'clamp',extrapolateRight:'clamp'};
const ease=Easing.bezier(.22,.78,.18,1);
const smooth=(x)=>{const q=Math.max(0,Math.min(1,x));return q*q*(3-2*q)};
const lim=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
const lerp=(a,b,p)=>a+(b-a)*p;
const sceneAlpha=(s,a,b,f=1.25)=>smooth((s-a)/f)*smooth((b-s)/f);
const frameValue=(f,a,b,x,y)=>interpolate(f,[a,b],[x,y],{...clamp,easing:ease});

function lineWave(w,y,amp,phase,step=150){
  let d=`M -${step} ${y}`;
  for(let x=-step;x<w+step;x+=step){
    const y1=y+Math.sin((x/step)*1.6+phase)*amp;
    const y2=y+Math.sin(((x+step)/step)*1.6+phase)*amp;
    d+=` Q ${x+step*.25} ${y1-amp*1.25} ${x+step*.5} ${(y1+y2)/2} Q ${x+step*.75} ${y2+amp*1.2} ${x+step} ${y2}`;
  }
  return d;
}
function Cloud({x,y,scale,alpha=.2}){
  return <g transform={`translate(${x} ${y}) scale(${scale})`} fill="#91A2A6" opacity={alpha}>
    <path d="M-142 22 Q-134 -13 -99 -9 Q-76 -50 -35 -31 Q-6 -65 34 -35 Q77 -42 91 -5 Q129 -7 146 25 L144 44 L-142 44Z"/>
  </g>;
}
function Sailor({x,y,scale=1,phase=0,opacity=1,climb=false}){
  const step=Math.sin(phase*4)*3;
  const armL=climb?`M0 -13 L-12 -25`:`M0 -13 L-12 -1`;
  const armR=climb?`M0 -13 L12 -26`:`M0 -13 L12 0`;
  return <g transform={`translate(${x} ${y+step}) scale(${scale})`} opacity={opacity} fill="none" stroke="#EEE5D1" strokeWidth="5" strokeLinecap="round">
    <circle cy="-28" r="7" fill="#EEE5D1" stroke="none"/><path d={`M0 -20 L0 5 ${armL} M0 -12 ${armR} M0 5 L-9 22 M0 5 L10 22`}/>
  </g>;
}
function Barrel({x,y,rot=0,scale=1}){
  return <g transform={`translate(${x} ${y}) rotate(${rot}) scale(${scale})`}>
    <path d="M-16 -26 Q-22 0 -16 25 Q0 34 16 25 Q22 0 16 -26 Q0 -33 -16 -26Z" fill="#795A39" stroke={GOLD} strokeWidth="2.4"/>
    <ellipse cy="-26" rx="16" ry="5" fill="#A38355" stroke={GOLD} strokeWidth="2"/>
    <path d="M-18 -11 Q0 -4 18 -11 M-17 13 Q0 20 17 13" fill="none" stroke="#C9A36B" strokeWidth="3"/>
    <ellipse cy="-26" rx="7" ry="2.5" fill="#2A231B"/>
  </g>;
}
function Ship({x,y,scale,frame,roll=0,ghost=false,crew=0,sails=1}){
  const sway=Math.sin(frame*.05)*1.2;
  const people=[];
  for(let i=0;i<crew;i++){
    const px=-165+(i%5)*78, py=-7-Math.floor(i/5)*10;
    people.push(<Sailor key={i} x={px} y={py} scale={.52} phase={frame/24+i} opacity={.9}/>);
  }
  const sail1=Math.sin(frame*.031)*2.8, sail2=Math.sin(frame*.027+1)*2.2;
  return <g transform={`translate(${x} ${y+sway}) rotate(${roll}) scale(${scale})`} opacity={ghost?.45:1}>
    <ellipse cx="0" cy="66" rx="260" ry="18" fill="#02070B" opacity=".65"/>
    <path d="M-246 0 Q-26 27 250 -7 L211 59 Q174 90 -190 68 Q-223 54 -246 0Z" fill="url(#hull)" stroke={GOLD} strokeWidth="4"/>
    <path d="M-226 16 Q0 42 230 7 M-210 36 Q0 58 218 31 M-191 53 Q0 74 204 48" fill="none" stroke="#92784F" strokeWidth="3" opacity=".88"/>
    <path d="M-240 -4 L243 -9 L214 9 Q0 25 -224 12Z" fill="#51422E" stroke="#D4B170" strokeWidth="3"/>
    <rect x="-42" y="-34" width="96" height="32" rx="4" fill="#18262D" stroke="#C09B60" strokeWidth="3"/>
    <rect x="-11" y="-55" width="45" height="21" fill="#26343A" stroke="#C09B60" strokeWidth="2"/>
    <g stroke="#BEA36C" strokeWidth="2.5" fill="none" opacity=".78">
      <path d="M-112 -5 L-90 -305 M10 -11 L6 -350 M122 -7 L124 -228"/>
      <path d="M-158 -245 L-32 -245 M-136 -166 L-28 -166 M-46 -296 L82 -296 M-46 -200 L78 -200 M75 -160 L180 -160"/>
      <path d="M-90 -305 L-206 -2 M-90 -305 L118 -4 M6 -350 L-102 -4 M6 -350 L217 -5 M124 -228 L76 -4 M124 -228 L222 -5" strokeWidth="1.5"/>
    </g>
    <g fill="url(#sail)" stroke="#B9AC94" strokeWidth="2.5">
      <path d="M-148 -240 Q-95 -253 -34 -239 L-43 -169 Q-98 -177 -139 -166Z" transform={`rotate(${sail1} -91 -208)`}/>
      <path d="M-39 -291 Q15 -302 77 -291 L70 -205 Q19 -216 -34 -202Z" transform={`rotate(${sail2} 18 -250)`}/>
      <path d="M80 -155 Q128 -166 177 -154 L169 -95 Q128 -104 91 -94Z" transform={`rotate(${sail1*.7} 128 -125)`}/>
      <path d="M-134 -161 Q-83 -171 -32 -159 L-37 -136 Q-87 -145 -133 -135Z" opacity=".82"/>
      <path d="M-31 -196 Q17 -205 72 -195 L67 -173 Q18 -182 -30 -170Z" opacity=".82"/>
    </g>
    <g transform={`translate(6 -350) rotate(${Math.sin(frame*.18)*8})`}>
      <path d="M0 0 Q22 4 45 0 L40 23 Q20 18 0 22Z" fill="#A95043"/>
      <path d="M5 4 L37 18 M36 4 L5 18" stroke="#F1E3CB" strokeWidth="2"/>
    </g>
    <g fill="#D8B675"><circle cx="-26" cy="-14" r="4"/><circle cx="23" cy="-14" r="4"/><circle cx="0" cy="-43" r="3"/></g>
    {people}
  </g>;
}
function Lifeboat({x,y,scale=1,frame,ghost=false}){
  return <g transform={`translate(${x} ${y+Math.sin(frame*.16)*5}) scale(${scale})`} opacity={ghost?.45:1}>
    <path d="M-85 0 Q0 18 88 -1 L69 31 Q0 48 -70 29Z" fill="#60472E" stroke="#D8B675" strokeWidth="3"/>
    <path d="M-66 8 Q0 21 72 7 M-43 3 L-31 28 M-7 7 L3 31 M29 6 L38 28" fill="none" stroke="#C4A36C" strokeWidth="2"/>
    <path d="M-96 45 Q-44 38 0 45 T95 44" fill="none" stroke="#ACC1C2" strokeWidth="2.5" opacity=".7"/>
  </g>;
}
function Badge({children,vertical=false,top=92}){
  return <div style={{position:'absolute',top:vertical?top*1.7:top,right:vertical?26:54,zIndex:30,padding:vertical?'9px 14px':'7px 16px',border:'1px solid rgba(216,182,117,.48)',borderRadius:99,background:'rgba(5,14,23,.72)',color:GOLD,font:'600 14px "Noto Sans Arabic"',direction:'rtl',boxShadow:'0 8px 24px rgba(0,0,0,.2)'}}>{children}</div>;
}
function Subtitle({cues,frame,vertical=false}){
  const cue=cues?.find(x=>frame>=x.start&&frame<x.end);
  if(!cue)return null;
  const fade=smooth(Math.min((frame-cue.start)/6,(cue.end-frame)/6));
  return <div style={{position:'absolute',left:vertical?28:120,right:vertical?28:120,bottom:vertical?250:58,zIndex:40,textAlign:'center',direction:'rtl',opacity:fade}}>
    <div style={{display:'inline-block',maxWidth:'100%',padding:vertical?'15px 19px':'13px 26px',background:'rgba(4,11,18,.84)',borderBottom:`2px solid ${GOLD}`,color:CREAM,fontFamily:'Noto Sans Arabic',fontSize:vertical?24:25,lineHeight:1.65,fontWeight:600,textShadow:'0 2px 10px rgba(0,0,0,.9)'}}>{cue.text.split('\n').map((line,i)=><React.Fragment key={i}>{i>0&&<br/>}{line}</React.Fragment>)}</div>
  </div>;
}
function SceneLabel({frame,vertical,mode}){
  const seconds=frame/24;
  const labels=mode==='short'?
    [[0,7,'المحيط الأطلسي · ١٨٧٢'],[8,16,'أفراد الطاقم العشرة'],[16,25,'سفينة طافية · قارب مفقود'],[25,31,'آخر سجل قرب الأزور'],[30,43,'فرضيات غير مثبتة'],[42,46,'وراء الحكاية']]:
    [[0,8,'إعادة بناء موشن غرافيك · لا صور أرشيفية'],[8,36,'٧ نوفمبر ١٨٧٢ · نيويورك إلى جنوة'],[34,57,'١٬٧٠١ برميل · وفق سجل الشحنة'],[56,67,'آخر ملاحظة معروفة · ٢٥ نوفمبر'],[67,83,'٥ ديسمبر · رصدتها دي غراتيا'],[82,108,'القارب مفقود · المضخة مفككة جزئيًا'],[108,130,'تحقيق جبل طارق · لا سبب محسوم'],[130,151,'الشك ليس دليلًا'],[150,217,'فرضيات منفصلة · لا واحدة مثبتة'],[217,226,'السفينة عادت · اللغز بقي مفتوحًا']];
  const item=labels.find(([a,b])=>seconds>=a&&seconds<b);
  if(!item)return null;
  const opacity=smooth(Math.min((seconds-item[0])/.35,(item[1]-seconds)/.35));
  return <div style={{position:'absolute',top:vertical?170:72,left:vertical?24:65,right:vertical?24:65,zIndex:25,textAlign:'center',opacity,direction:'rtl',font:'700 14px "Noto Sans Arabic"',color:GOLD,textShadow:'0 2px 12px #07131d'}}><span style={{padding:'6px 15px',background:'rgba(5,13,21,.48)',borderRadius:99}}>{item[2]}</span></div>;
}
function titleCard(t,vertical){
  const op=sceneAlpha(t,0,8,.8);
  return <div style={{position:'absolute',top:vertical?310:170,left:vertical?25:140,right:vertical?25:140,zIndex:20,textAlign:'center',direction:'rtl',opacity:op,transform:`translateY(${lerp(22,0,smooth(t/.9))}px)`}}>
    <div style={{font:'700 16px "Noto Sans Arabic"',color:GOLD,letterSpacing:'.04em'}}>ماري سيليست · ١٨٧٢</div>
    <div style={{font:`700 ${vertical?51:60}px "Noto Naskh Arabic"`,lineHeight:1.28,color:CREAM,textShadow:'0 5px 25px rgba(0,0,0,.85)',marginTop:8}}>أين اختفى<br/>البحّارة العشرة؟</div>
    <div style={{height:3,width:90,background:GOLD,margin:'18px auto 0',transform:`scaleX(${smooth(t/1.3)})`}}/>
  </div>;
}
function MapCard({t,W,H,portrait}){
  const alpha=sceneAlpha(t,8,39);
  const x=W*.09,y=portrait?H*.12:H*.11,w=W*.82,h=portrait?H*.38:H*.64;
  const p=smooth((t-9)/20);
  return <g opacity={alpha}>
    <rect x={x} y={y} width={w} height={h} rx={22} fill="#D8C79F" fillOpacity=".95" stroke="#F2E4BE" strokeWidth="4"/>
    <rect x={x+16} y={y+16} width={w-32} height={h-32} rx={15} fill="none" stroke="#746448" strokeOpacity=".45" strokeWidth="2"/>
    <path d={`M${x+w*.05} ${y+h*.33} Q${x+w*.12} ${y+h*.15} ${x+w*.22} ${y+h*.24} L${x+w*.29} ${y+h*.4} Q${x+w*.27} ${y+h*.58} ${x+w*.16} ${y+h*.58} L${x+w*.08} ${y+h*.48}Z M${x+w*.68} ${y+h*.16} Q${x+w*.81} ${y+h*.12} ${x+w*.88} ${y+h*.25} L${x+w*.96} ${y+h*.44} Q${x+w*.89} ${y+h*.57} ${x+w*.78} ${y+h*.51} L${x+w*.7} ${y+h*.4}Z`} fill="#8D9982" stroke="#65736A" strokeWidth="3"/>
    <path d={`M${x+w*.20} ${y+h*.42} C${x+w*.34} ${y+h*.18} ${x+w*.51} ${y+h*.73} ${x+w*.79} ${y+h*.4}`} fill="none" stroke="#974B42" strokeWidth="5" strokeDasharray={w*.68} strokeDashoffset={w*.68*(1-p)} strokeLinecap="round"/>
    <circle cx={x+w*.2} cy={y+h*.42} r="8" fill="#A4493D"/>
    <circle cx={x+w*(.2+.59*p)} cy={y+h*(.42+.055*Math.sin(t))} r="10" fill="#243943" stroke="#F5E5C2" strokeWidth="3"/>
    <g transform={`translate(${x+w*.87} ${y+h*.76}) rotate(${t*12})`} stroke="#65583F" fill="none" strokeWidth="3"><circle r="34"/><path d="M0 -30 L7 0 L0 30 L-7 0Z" fill="#A94E43"/></g>
    <path d={`M${x+w*.14} ${y+h*.78} Q${x+w*.27} ${y+h*.71} ${x+w*.4} ${y+h*.78} T${x+w*.62} ${y+h*.78}`} fill="none" stroke="#7F7257" strokeWidth="2" opacity=".4"/>
  </g>;
}
function Logbook({t,W,H,portrait}){
  const x=portrait?W*.08:W*.1,y=portrait?H*.56:H*.15,w=W*.8,h=portrait?H*.2:H*.3;
  const reveal=smooth((t-(portrait?25:56))/.9);
  const lineProgress=smooth((t-(portrait?26:57))/3);
  return <g opacity={reveal} transform={`rotate(${Math.sin(t*.5)*1.2} ${x+w/2} ${y+h/2})`}>
    <rect x={x} y={y} width={w} height={h} rx={14} fill="#D8C69E" stroke="#F3E3BD" strokeWidth="3"/>
    {[.27,.43,.59,.74].map((r,i)=><path key={i} d={`M${x+w*.08} ${y+h*r} H${x+w*(.52+.36*lineProgress)}`} stroke="#71624D" strokeWidth="3" opacity=".56"/>)}
    <circle cx={x+w*.78} cy={y+h*.55} r="22" fill="none" stroke="#A54941" strokeWidth="4" opacity={lineProgress}/>
    <path d={`M${x+w*.7} ${y+h*.56} l12 12 27 -32`} fill="none" stroke="#A54941" strokeWidth="4" strokeDasharray="65" strokeDashoffset={65*(1-lineProgress)}/>
  </g>;
}
function PumpDiagram({x,y,scale=1,frame}){
  const water=.28+.14*Math.sin(frame*.11);
  const handle=Math.sin(frame*.12)*11;
  return <g transform={`translate(${x} ${y}) scale(${scale})`}>
    <rect x="-115" y="-70" width="230" height="138" rx="16" fill="#0D202A" stroke={GOLD} strokeWidth="3"/>
    <path d="M-85 30 Q-42 20 0 30 T85 30 V53 H-85Z" fill="#428B9A" fillOpacity=".65"/>
    <path d="M-70 20 V-32 M-70 -32 H38 M38 -32 V20" stroke="#D9BB7C" strokeWidth="8" fill="none" strokeLinecap="round"/>
    <path d={`M-70 -32 H${38+handle} M${38+handle} -32 V20`} stroke="#F0D18A" strokeWidth="5" fill="none" strokeLinecap="round"/>
    <path d={`M-86 ${30-water*20} Q-42 ${20-water*20} 0 ${30-water*20} T86 ${30-water*20}`} fill="none" stroke="#A4D0D0" strokeWidth="4"/>
  </g>;
}
function Magnifier({frame,x,y,r=78}){
  const mx=x+Math.cos(frame*.035)*55,my=y+Math.sin(frame*.042)*28;
  const rotate=Math.sin(frame*.03)*10;
  return <g transform={`translate(${mx} ${my}) rotate(${rotate})`}>
    <circle r={r} fill="#9BC6CA" fillOpacity=".08" stroke="#E8D8B4" strokeWidth="8"/>
    <circle r={r*.74} fill="#A3CED0" fillOpacity=".1"/>
    <path d={`M${r*.65} ${r*.65} L${r*1.55} ${r*1.55}`} stroke="#E8D8B4" strokeWidth="15" strokeLinecap="round"/>
  </g>;
}
function MiniCard({x,y,w,h,frame,index,color=GOLD}){
  const f=frame/24;
  const inP=spring({frame:Math.max(0,frame-index*10),fps:24,config:{damping:18,stiffness:100,mass:.75}});
  const rise=(1-inP)*28;
  return <g transform={`translate(${rise} 0)`} opacity={inP}>
    <rect x={x} y={y} width={w} height={h} rx="14" fill="#102531" stroke={color} strokeWidth="3"/>
    <path d={`M${x+20} ${y+h*.28} H${x+w*.62} M${x+20} ${y+h*.47} H${x+w*.48} M${x+20} ${y+h*.67} H${x+w*.54}`} stroke="#B8C6C4" strokeOpacity=".42" strokeWidth="4" strokeLinecap="round"/>
    <circle cx={x+w*.79} cy={y+h*.5} r={h*.23} fill="none" stroke={color} strokeOpacity=".45" strokeWidth="3" strokeDasharray="8 8" transform={`rotate(${f*18} ${x+w*.79} ${y+h*.5})`}/>
  </g>;
}
function TheoryCards({t,W,H,portrait}){
  const defs=[['ماء في القاع','مضخة معطلة؟','water'],['أبخرة الشحنة','خشية حريق؟','vapor'],['الطقس والتيارات','انجراف السفينة','weather']];
  if(portrait){
    return <g>{defs.map(([title,sub,type],i)=>{
      const delay=30+i*3,pr=spring({frame:Math.max(0,(t-delay)*24),fps:24,config:{damping:17,stiffness:105}});
      const x=W*.08,y=H*(.32+i*.15),w=W*.84,h=H*.125;
      return <g key={title} opacity={pr} transform={`translate(${(1-pr)*45} 0)`}>
        <rect x={x} y={y} width={w} height={h} rx="18" fill="#0D202A" stroke={i===1?'#D7A754':TEAL} strokeWidth="3"/>
        <g transform={`translate(${x+w*.78} ${y+h*.5})`}>
          {type==='water'&&<><path d="M-30 10 Q-15 4 0 10 T30 10" fill="none" stroke={TEAL} strokeWidth="5"/><path d="M-20 4 V-23 M20 4 V-23 M-20 -23 H20" stroke={GOLD} strokeWidth="5"/></>}
          {type==='vapor'&&<><circle cx="-17" cy="16" r="6" fill={GOLD} opacity=".8"/><circle cx="0" cy="-2" r="5" fill={GOLD} opacity=".65"/><circle cx="17" cy="-21" r="4" fill={GOLD} opacity=".55"/><path d="M-23 26 Q-8 4 0 -5 T23 -33" fill="none" stroke={GOLD} strokeWidth="3" strokeDasharray="5 6"/></>}
          {type==='weather'&&<><path d="M-30 -20 H25 M-30 -4 H18 M-30 12 H26" stroke={TEAL} strokeWidth="3"/><path d="M-18 -29 V23 M10 -29 V23" stroke={GOLD} strokeWidth="2" strokeDasharray="4 5"/></>}
        </g>
        <text x={x+w*.62} y={y+h*.42} textAnchor="end" fill={CREAM} fontFamily="Noto Sans Arabic" fontSize={portrait?24:20} fontWeight="700" direction="rtl">{title}</text>
        <text x={x+w*.62} y={y+h*.70} textAnchor="end" fill="#C5CECA" fontFamily="Noto Sans Arabic" fontSize={portrait?18:16} direction="rtl">{sub}</text>
        <text x={x+w*.14} y={y+h*.56} textAnchor="middle" fill={GOLD} fontFamily="Noto Sans Arabic" fontSize={portrait?13:12} direction="rtl">فرضية</text>
      </g>;
    })}</g>;
  }
  const cardW=W*.255, gap=W*.028, start=W*.067, y=H*.36, h=H*.27;
  return <g>{defs.map(([title,sub,type],i)=>{
    const delay=150+i*8,pr=spring({frame:Math.max(0,(t-delay)*24),fps:24,config:{damping:18,stiffness:95}});
    const x=start+i*(cardW+gap);
    return <g key={title} opacity={pr} transform={`translate(0 ${(1-pr)*42})`}>
      <rect x={x} y={y} width={cardW} height={h} rx="18" fill="#0D202A" stroke={i===1?'#D7A754':TEAL} strokeWidth="2.5"/>
      <circle cx={x+cardW*.5} cy={y+h*.36} r={h*.20} fill="#18323C" stroke={GOLD} strokeOpacity=".52" strokeWidth="2" strokeDasharray="8 8" transform={`rotate(${t*3+i*50} ${x+cardW*.5} ${y+h*.36})`}/>
      {type==='water'&&<g transform={`translate(${x+cardW*.5} ${y+h*.36})`}><path d="M-32 5 Q-16 -1 0 5 T32 5 M-24 0 V-24 M24 0 V-24 M-24 -24 H24" fill="none" stroke={TEAL} strokeWidth="4"/></g>}
      {type==='vapor'&&<g transform={`translate(${x+cardW*.5} ${y+h*.36})`}><circle cx="-22" cy="22" r="6" fill={GOLD}/><circle cx="0" cy="0" r="5" fill={GOLD} opacity=".8"/><circle cx="20" cy="-20" r="4" fill={GOLD} opacity=".7"/><path d="M-28 30 Q-12 8 0 -2 T30 -34" fill="none" stroke={GOLD} strokeWidth="3" strokeDasharray="5 6"/></g>}
      {type==='weather'&&<g transform={`translate(${x+cardW*.5} ${y+h*.36})`}><path d="M-35 -22 H30 M-35 -4 H24 M-35 14 H32" stroke={TEAL} strokeWidth="3"/><path d="M-18 -30 V28 M12 -30 V28" stroke={GOLD} strokeWidth="2" strokeDasharray="4 5"/></g>}
      <text x={x+cardW*.5} y={y+h*.72} textAnchor="middle" fill={CREAM} fontFamily="Noto Sans Arabic" fontSize="22" fontWeight="700" direction="rtl">{title}</text>
      <text x={x+cardW*.5} y={y+h*.85} textAnchor="middle" fill="#C5CECA" fontFamily="Noto Sans Arabic" fontSize="15" direction="rtl">{sub}</text>
      <text x={x+cardW*.5} y={y+h*.95} textAnchor="middle" fill={GOLD} fontFamily="Noto Sans Arabic" fontSize="13" direction="rtl">فرضية — غير مثبتة</text>
    </g>;
  })}</g>;
}
function OceanArt({frame,mode}){
  const {width:W,height:H}=useVideoConfig();
  const portrait=mode==='short',s=frame/24;
  const horizon=portrait?H*.61:H*.58;
  const dark=mode==='main'?1-smooth((s-54)/35):.3+.2*Math.sin(s*.08);
  const top=dark>0.5?'#08131f':'#2b4550',mid=dark>0.5?'#1a2c39':'#6b7774';
  const clouds=[];
  for(let i=0;i<12;i++){
    const x=((i*177+s*(i%2?13:-7))%(W+320))-150;
    clouds.push(<Cloud key={i} x={x} y={H*(.19+(i%4)*.055)} scale={.45+(i%3)*.14} alpha={.12+(i%4)*.025}/>);
  }
  const rain=s>=54&&s<=70&&mode==='main'||s>=25&&s<=31&&mode==='short';
  const rainLines=[];
  if(rain)for(let i=0;i<46;i++){
    const x=(i*103+frame*(.7+(i%3)*.12))%(W+50)-25;
    const y=(i*97+frame*2.3)%(H+60)-30;
    rainLines.push(<path key={i} d={`M${x} ${y} l-12 29`} stroke="#D3E0E0" strokeOpacity=".36" strokeWidth="2"/>);
  }
  const phase=frame*.052;
  return <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%'}}>
    <defs>
      <linearGradient id="sky-v4" x2="0" y2="1"><stop stopColor={top}/><stop offset=".55" stopColor={mid}/><stop offset="1" stopColor="#5A635F"/></linearGradient>
      <linearGradient id="water-v4" x2="0" y2="1"><stop stopColor="#2B4B56"/><stop offset=".45" stopColor="#16313D"/><stop offset="1" stopColor="#06131D"/></linearGradient>
      <linearGradient id="hull" x2="0" y2="1"><stop stopColor="#4B4034"/><stop offset="1" stopColor="#101A21"/></linearGradient>
      <linearGradient id="sail" x2="0" y2="1"><stop stopColor="#E0D7C2"/><stop offset="1" stopColor="#938875"/></linearGradient>
      <radialGradient id="moon-v4"><stop stopColor="#E8D8B5" stopOpacity=".42"/><stop offset="1" stopColor="#BE9865" stopOpacity="0"/></radialGradient>
      <linearGradient id="bottom-v4" x2="0" y2="1"><stop stopColor="#061019" stopOpacity="0"/><stop offset="1" stopColor="#061019" stopOpacity=".68"/></linearGradient>
    </defs>
    <rect width={W} height={H} fill="url(#sky-v4)"/>
    <circle cx={W*.8} cy={H*.2} r={H*.13} fill="url(#moon-v4)"/>
    <circle cx={W*.8} cy={H*.2} r={H*.027} fill="#E2D4B7" fillOpacity=".58"/>
    {clouds}
    <path d={`M0 ${horizon} Q${W*.24} ${horizon-7} ${W*.5} ${horizon} T${W} ${horizon-5} L${W} ${H} L0 ${H}Z`} fill="url(#water-v4)"/>
    {[0,1,2,3,4,5,6].map(i=><path key={i} d={lineWave(W,horizon+H*(.04+i*.067),H*(.009+i*.001),phase+i*1.7,W*(.17+i*.01))} fill="none" stroke="#B6C8C6" strokeOpacity={.22-i*.018} strokeWidth={i<2?3:2}/>)}
    {Array.from({length:9},(_,i)=>{const x=(i*219+s*(i%2?18:-12))%(W+80)-40;return <ellipse key={i} cx={x} cy={horizon+H*(.04+(i%5)*.055)} rx={W*(.01+(i%3)*.005)} ry={H*.003} fill="#E0D6C2" opacity=".17"/>;})}
    {rain&&<g>{rainLines}</g>}
    <rect width={W} height={H} fill="url(#bottom-v4)"/>
  </svg>;
}
function MapLayer({frame,mode}){
  const {width:W,height:H}=useVideoConfig(),portrait=mode==='short',s=frame/24;
  const x=W*.09,y=portrait?H*.12:H*.11,w=W*.82,h=portrait?H*.38:H*.62;
  const local=s-(portrait?25:8), p=smooth(local/(portrait?7:21));
  const enter=spring({frame:Math.max(0,(frame-(portrait?24:7*24))),fps:24,config:{damping:20,stiffness:85}});
  return <g opacity={sceneAlpha(s,portrait?24:8,portrait?33:38)} transform={`translate(0 ${(1-enter)*42})`}>
    <rect x={x} y={y} width={w} height={h} rx="20" fill="#D9C9A2" fillOpacity=".95" stroke="#F3E4C0" strokeWidth="4"/>
    <rect x={x+16} y={y+16} width={w-32} height={h-32} rx="14" fill="none" stroke="#756448" strokeOpacity=".45" strokeWidth="2"/>
    <path d={`M${x+w*.05} ${y+h*.31} Q${x+w*.12} ${y+h*.15} ${x+w*.22} ${y+h*.24} L${x+w*.29} ${y+h*.42} Q${x+w*.27} ${y+h*.57} ${x+w*.16} ${y+h*.57} L${x+w*.08} ${y+h*.48}Z M${x+w*.68} ${y+h*.17} Q${x+w*.8} ${y+h*.13} ${x+w*.88} ${y+h*.26} L${x+w*.95} ${y+h*.44} Q${x+w*.89} ${y+h*.58} ${x+w*.78} ${y+h*.5} L${x+w*.7} ${y+h*.39}Z`} fill="#919985" stroke="#657269" strokeWidth="3"/>
    <path d={`M${x+w*.2} ${y+h*.44} C${x+w*.36} ${y+h*.16} ${x+w*.54} ${y+h*.73} ${x+w*.79} ${y+h*.4}`} fill="none" stroke="#9A4D43" strokeWidth="5" strokeDasharray={w*.66} strokeDashoffset={w*.66*(1-p)} strokeLinecap="round"/>
    <circle cx={x+w*.2} cy={y+h*.44} r="9" fill="#A4493D"/>
    <circle cx={x+w*(.2+.59*p)} cy={y+h*(.44+.055*Math.sin(s))} r="10" fill="#243943" stroke="#F4E4C1" strokeWidth="3"/>
    {[0,1,2].map(i=>{const px=x+w*(.34+.18*i);return <g key={i} opacity={smooth((p-i*.26)*4)}><circle cx={px} cy={y+h*(.52+(i%2)*.07)} r="5" fill="#A44B42"/><path d={`M${px} ${y+h*(.52+(i%2)*.07)} l10 -12`} stroke="#A44B42" strokeWidth="3"/></g>;})}
    <g transform={`translate(${x+w*.87} ${y+h*.76}) rotate(${s*10})`} stroke="#66583F" fill="none" strokeWidth="3"><circle r="32"/><path d="M0 -27 L7 0 L0 27 L-7 0Z" fill="#A94E43"/></g>
  </g>;
}
function MainArt({frame,mode}){
  const {width:W,height:H}=useVideoConfig(),portrait=mode==='short',s=frame/24;
  const [x,y,sc]=portrait?
    [W*(s<16?.5:s<27?.55:lerp(.55,.29,smooth((s-34)/9))),H*(.6+Math.sin(frame*.07)*.006),s<16?.72:s<27?.74:s<34?.72:lerp(.72,.32,smooth((s-34)/9))]:
    [W*(s<8?.56:s<35?.48:s<57?.47:s<78?.51:s<98?lerp(.58,.46,smooth((s-78)/20)):.52),H*(.59+Math.sin(frame*.075)*.007),s<8?1:s<35?.67:s<57?.95:s<78?.95:s<108?.9:s<150?.86:s<215?.75:lerp(.72,.34,smooth((s-215)/10))];
  const roll=Math.sin(frame*.043)*1.1+Math.sin(frame*.017)*.4;
  const showCrew=(portrait&&s>8&&s<16)||(!portrait&&s>33&&s<57);
  const crewCount=(!portrait&&showCrew)?10:0;
  const cargo=!portrait&&s>33&&s<58;
  const fog=!portrait&&s>53&&s<72;
  const waveRef=H*(portrait?.62:.6);
  const barrels=[];
  if(cargo)for(let i=0;i<5;i++)barrels.push(<Barrel key={i} x={x+(-.15+i*.075)*W} y={y+H*.022+Math.sin(frame*.11+i)*3} scale={portrait?.37:.67} rot={Math.sin(frame*.08+i)*2}/>);
  const rescue=!portrait&&s>66&&s<91;
  const rx=rescue?lerp(-W*.12,W*.28,smooth((s-67)/20)):0;
  const boarding=!portrait&&s>76&&s<102;
  const boatX=boarding?lerp(W*1.08,W*.7,smooth((s-76)/11)):0;
  const ladderX=x+W*.18;
  const divers=[];
  if(boarding)for(let i=0;i<3;i++){
    const p=lim((s-(78+i*2.4))/4);
    if(s>77+i*2.4)divers.push(<Sailor key={i} x={lerp(boatX+W*.02,ladderX,p)} y={lerp(H*.67,H*.515+i*H*.013,p)} scale={.8} phase={s+i} climb opacity={1}/>);
  }
  const emptySpot=portrait&&s>16&&s<26;
  const route=portrait&&s>25&&s<34;
  return <g>
      {!portrait&&s<56&&<g opacity={smooth((s-34)/2)*smooth((57-s)/2)}>{barrels}</g>}
      {cargo&&<g opacity=".52" fill="none" stroke="#DDB969" strokeWidth="2.5"><path d={`M${x-W*.08} ${y-H*.08} Q${x-W*.16} ${y-H*.19} ${x-W*.07} ${y-H*.27}`}/><path d={`M${x+W*.08} ${y-H*.07} Q${x+W*.17} ${y-H*.18} ${x+W*.1} ${y-H*.26}`}/></g>}
      <Ship x={x} y={y} scale={sc} frame={frame} roll={roll} ghost={!portrait&&s<8} crew={crewCount}/>
      {showCrew&&portrait&&Array.from({length:10},(_,i)=><Sailor key={`short-${i}`} x={W*(.31+(i%5)*.095)} y={H*.59-(i>4?7:0)} scale={.37} phase={s+i} opacity={1-smooth((s-11)/5)}/>)}
      {rescue&&<g opacity={sceneAlpha(s,66,90)}><Ship x={rx} y={H*.64} scale={.42} frame={frame} roll={Math.sin(s)*.5}/><path d={`M${rx-W*.08} ${H*.52} L${x} ${H*.63}`} stroke={GOLD} strokeWidth="2.5" strokeDasharray="8 10" opacity=".65"/></g>}
      {boarding&&<g opacity={sceneAlpha(s,76,101)}>
        <path d={`M${ladderX} ${H*.52} V${H*.68} M${ladderX+W*.026} ${H*.52} V${H*.68}`} stroke={GOLD} strokeWidth="3.2"/>
        {Array.from({length:7},(_,i)=><path key={i} d={`M${ladderX} ${H*(.53+i*.021)} H${ladderX+W*.026}`} stroke="#D2B273" strokeWidth="2.2"/>)}
        <path d={`M${x+W*.22} ${H*.55} L${boatX} ${H*.68}`} stroke="#D2B273" strokeDasharray="8 9" strokeWidth="2.5" opacity=".7"/>
        <Lifeboat x={boatX} y={H*.68} scale={.7} frame={frame}/>{divers}
      </g>}
      {emptySpot&&<g opacity={smooth((s-16)/2)*smooth((26-s)/2)}><circle cx={W*.79} cy={H*.68} r={H*.06} fill="none" stroke={GOLD} strokeDasharray="8 9" strokeWidth="3"/><path d={`M${W*.76} ${H*.68} H${W*.82}`} stroke={GOLD} strokeWidth="3"/></g>}
      {route&&<g opacity={sceneAlpha(s,25,34)}><path d={`M${W*.17} ${H*.44} Q${W*.5} ${H*.37} ${W*.83} ${H*.45}`} fill="none" stroke={GOLD} strokeWidth="3" strokeDasharray={`${W*.68}`} strokeDashoffset={`${W*.68*(1-smooth((s-25)/6))}`}/><circle cx={W*(.17+.66*smooth((s-25)/6))} cy={H*.43} r="9" fill="#A94F43"/></g>}
      {fog&&<g opacity={.5}><path d={lineWave(W,waveRef,H*.012,frame*.11,170)} fill="none" stroke="#D5E0DF" strokeWidth="2" strokeDasharray="3 5"/>{Array.from({length:44},(_,i)=><path key={i} d={`M${(i*103+frame*.72)%(W+50)-25} ${(i*97+frame*2.2)%(H+40)-20} l-10 25`} stroke="#D5E0DF" strokeOpacity=".34" strokeWidth="2"/>)}</g>}
    </g>;
}
function EvidenceLayer({frame,mode}){
  const {width:W,height:H}=useVideoConfig(),portrait=mode==='short',s=frame/24;
  const start=portrait?16:84,end=portrait?27:114,alpha=sceneAlpha(s,start,end);
  const cards=[];
  const cardW=portrait?W*.84:W*.24,cardH=portrait?H*.10:H*.2,gap=portrait?H*.02:W*.025;
  for(let i=0;i<3;i++){
    const delay=(portrait?16:108)+i*2, p=spring({frame:Math.max(0,frame-delay*24),fps:24,config:{damping:18,stiffness:95}});
    const cardX=portrait?W*.08+i*0:W*.08+i*(cardW+gap),cardY=portrait?H*(.31+i*.13):H*.38;
    cards.push(<g key={i} transform={`translate(${(1-p)*50} ${(1-p)*20})`} opacity={p}>
      <rect x={cardX} y={cardY} width={cardW} height={cardH} rx="14" fill="#102531" stroke={i===1?'#D6A550':TEAL} strokeWidth="3"/>
      <path d={`M${cardX+cardW*.1} ${cardY+cardH*.28} H${cardX+cardW*.57} M${cardX+cardW*.1} ${cardY+cardH*.48} H${cardX+cardW*.48} M${cardX+cardW*.1} ${cardY+cardH*.69} H${cardX+cardW*.54}`} stroke="#BECBC8" strokeOpacity=".5" strokeWidth="3.5" strokeLinecap="round"/>
      <circle cx={cardX+cardW*.79} cy={cardY+cardH*.5} r={cardH*.23} fill="none" stroke={GOLD} strokeWidth="3" strokeDasharray="7 7" transform={`rotate(${frame*1.7+i*45} ${cardX+cardW*.79} ${cardY+cardH*.5})`}/>
      <text x={cardX+cardW*.43} y={cardY+cardH*.88} textAnchor="middle" fill={CREAM} fontFamily="Noto Sans Arabic" fontWeight="700" fontSize={portrait?20:17} direction="rtl">{['القارب مفقود','آخر سجل معروف','الطاقم غائب'][i]}</text>
    </g>);
  }
  const pumpX=portrait?W*.5:W*.78,pumpY=portrait?H*.73:H*.68, lever=Math.sin(frame*.13)*10;
  return <g opacity={alpha}>
    {cards}
    <g transform={`translate(${pumpX} ${pumpY}) scale(${portrait?.76:1})`}>
      <rect x="-110" y="-58" width="220" height="116" rx="14" fill="#10242F" stroke={GOLD} strokeWidth="3"/>
      <path d="M-82 28 Q-42 18 0 28 T82 28 V44 H-82Z" fill="#3D8290" fillOpacity=".72"/>
      <path d={`M-58 20 V-25 M-58 -25 H35 M35 -25 V18 M35 -25 H${35+lever}`} fill="none" stroke="#E2C789" strokeWidth="7" strokeLinecap="round"/>
      <path d={`M-82 ${28-Math.sin(frame*.11)*4} Q-42 ${18-Math.sin(frame*.11)*4} 0 ${28-Math.sin(frame*.11)*4} T82 ${28-Math.sin(frame*.11)*4}`} fill="none" stroke={TEAL} strokeWidth="4"/>
    </g>
    {!portrait&&<Magnifier frame={frame} x={W*.49} y={H*.55} r={H*.1}/>}
  </g>;
}
function InvestigationLayer({frame,mode}){
  const {width:W,height:H}=useVideoConfig(),portrait=mode==='short',s=frame/24;
  const a=portrait?25:106,b=portrait?35:149,opacity=sceneAlpha(s,a,b);
  const paperW=portrait?W*.82:W*.32,paperH=portrait?H*.16:H*.32;
  return <g opacity={opacity}>
    {[0,1,2].map(i=>{
      const delay=(portrait?25:106)+i*3, p=spring({frame:Math.max(0,frame-delay*24),fps:24,config:{damping:17,stiffness:95}});
      const x=portrait?W*.09+i*W*.025:W*(.08+i*.16),y=portrait?H*.48:H*.27;
      return <g key={i} transform={`translate(${(1-p)*W*.12} ${(1-p)*H*.04}) rotate(${(i-1)*4},${x+paperW/2},${y+paperH/2})`} opacity={p}>
        <rect x={x} y={y} width={paperW} height={paperH} rx="12" fill="#D9C9A3" stroke="#F0E2C1" strokeWidth="3"/>
        {[.26,.42,.58,.74].map((line,j)=><path key={j} d={`M${x+paperW*.1} ${y+paperH*line} H${x+paperW*(.6+.12*Math.sin(frame*.025+j))}`} stroke="#70614A" strokeOpacity=".54" strokeWidth="3"/>)}
        <circle cx={x+paperW*.78} cy={y+paperH*.55} r={paperH*.12} fill="none" stroke="#A54E43" strokeWidth="4" opacity={smooth((s-120-i*3)/1.3)}/>
      </g>;
    })}
    {!portrait&&<Magnifier frame={frame} x={W*.52} y={H*.51} r={H*.12}/>}
  </g>;
}
function EndingHalo({frame,mode}){
  const {width:W,height:H}=useVideoConfig(),s=frame/24,portrait=mode==='short';
  const start=portrait?41:214,alpha=sceneAlpha(s,start,portrait?45.5:225.5,.7),p=smooth((s-start)/(portrait?4.5:10));
  return <g opacity={alpha}>
    <circle cx={W*.5} cy={H*.21} r={H*(.08+.04*p)} fill="#E9CA8E" fillOpacity=".07" stroke="#E2C282" strokeOpacity=".65" strokeWidth="2"/>
    <path d={`M${W*.32} ${H*.3} Q${W*.5} ${H*.28} ${W*.68} ${H*.3} M${W*.37} ${H*.36} Q${W*.5} ${H*.34} ${W*.63} ${H*.36}`} fill="none" stroke="#E0C182" strokeOpacity=".45" strokeWidth="2"/>
  </g>;
}
function MotionArtwork({mode}){
  const frame=useCurrentFrame(),{width:W,height:H}=useVideoConfig(),portrait=mode==='short',s=frame/24;
  const zoom=1.012+.012*Math.sin(frame*.012), shiftX=Math.sin(frame*.017)*W*.006,shiftY=Math.cos(frame*.015)*H*.005;
  const facts=s>=108&&s<151&&!portrait;
  const myths=s>=128&&s<153&&!portrait;
  return <>
    <OceanArt frame={frame} mode={mode}/>
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%',transform:`translate(${shiftX}px,${shiftY}px) scale(${zoom})`}}>
      <MainArt frame={frame} mode={mode}/>
      {!portrait&&s>=8&&s<40&&<MapLayer frame={frame} mode={mode}/>}
      {!portrait&&s>=56&&s<68&&<Logbook t={s} W={W} H={H} portrait={false}/>}
      {portrait&&s>=25&&s<35&&<Logbook t={s} W={W} H={H} portrait/>}
      {s>= (portrait?16:84) && s<=(portrait?27:114)&&<EvidenceLayer frame={frame} mode={mode}/>}
      {!portrait&&s>=106&&s<151&&<InvestigationLayer frame={frame} mode={mode}/>}
      {s>=(portrait?30:150)&&s<=(portrait?43:217)&&<TheoryCards t={frame} W={W} H={H} portrait={portrait}/>}
      <EndingHalo frame={frame} mode={mode}/>
      <rect width={W} height={H} fill="none" stroke="#D3B36F" strokeOpacity=".23" strokeWidth={3}/>
    </svg>
    {!portrait&&s<8&&titleCard(s,false)}
    {portrait&&s<7&&titleCard(s,true)}
    <SceneLabel frame={frame} vertical={portrait} mode={mode}/>
    <div style={{position:'absolute',top:portrait?75:34,right:portrait?26:50,zIndex:30,color:CREAM,font:'700 16px "Noto Sans Arabic"',textShadow:'0 2px 8px #000',direction:'rtl'}}>وراء الحكاية <span style={{color:GOLD,marginInlineStart:8}}>·</span></div>
    {facts&&<Badge vertical={false} top={112}>الوقائع الموثقة</Badge>}
    {myths&&<Badge vertical={false} top={112}>الأسطورة ليست دليلًا</Badge>}
    {!portrait&&s>=150&&s<217&&<div style={{position:'absolute',top:160,left:60,right:60,zIndex:24,textAlign:'center',font:'700 31px "Noto Naskh Arabic"',color:CREAM,textShadow:'0 3px 16px #000',opacity:sceneAlpha(s,150,217,2)}}>ثلاث فرضيات · لا جواب محسوم</div>}
    <div style={{position:'absolute',left:portrait?28:64,right:portrait?28:64,bottom:portrait?185:28,height:2,zIndex:35,background:'rgba(255,255,255,.15)'}}><div style={{width:`${100*frame/(mode==='short'?1092:5409)}%`,height:'100%',background:GOLD}}/></div>
  </>;
}
function MainVideo({cues}){
  const frame=useCurrentFrame();
  return <AbsoluteFill style={{background:INK,overflow:'hidden'}}><MotionArtwork mode="main"/><Subtitle cues={cues} frame={frame}/></AbsoluteFill>;
}
function ShortVideo({cues}){
  const frame=useCurrentFrame();
  return <AbsoluteFill style={{background:INK,overflow:'hidden'}}><MotionArtwork mode="short"/><Subtitle cues={cues} frame={frame} vertical/></AbsoluteFill>;
}
export const RemotionV4Root=()=> <>
  <Composition id="MaryCelesteMotionV4" component={MainVideo} durationInFrames={5433} width={1280} height={720} fps={24} defaultProps={{cues:[]}}/>
  <Composition id="MaryCelesteShortMotionV4" component={ShortVideo} durationInFrames={1100} width={720} height={1280} fps={24} defaultProps={{cues:[]}}/>
</>;
