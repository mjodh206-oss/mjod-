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
function MapJourneyScene({frame,portrait}){
  const {width:W,height:H}=useVideoConfig(),f=frame/24;
  const p=smooth((f-(portrait?0:8))/(portrait?10:22));
  const px=lerp(W*.19,W*.80,p),py=H*(portrait?.55:.59)+Math.sin(frame*.035)*H*.018;
  const mapY=portrait?H*.28:H*.19,mapH=portrait?H*.36:H*.54;
  return <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%'}}>
    <defs><linearGradient id="map-bg-v5" x2="0" y2="1"><stop stopColor="#102633"/><stop offset="1" stopColor="#07131D"/></linearGradient><radialGradient id="map-glow-v5"><stop stopColor="#E9C985" stopOpacity=".15"/><stop offset="1" stopColor="#E9C985" stopOpacity="0"/></radialGradient></defs>
    <rect width={W} height={H} fill="url(#map-bg-v5)"/><circle cx={W*.52} cy={H*.48} r={H*.58} fill="url(#map-glow-v5)"/>
    <text x={W*.5} y={H*.105} textAnchor="middle" fill={GOLD} fontFamily="Noto Sans Arabic" fontSize={portrait?22:25} fontWeight="700" direction="rtl">رحلة عبر الأطلسي · نوفمبر ١٨٧٢</text>
    <g transform={`translate(${W*.08} ${mapY})`}>
      <rect width={W*.84} height={mapH} rx={portrait?22:28} fill="#D8C99F" stroke="#F1E4C5" strokeWidth="4"/>
      {[.2,.4,.6,.8].map((n,i)=><path key={`v${i}`} d={`M${W*.84*n} 0 V${mapH}`} stroke="#827B65" strokeOpacity=".2" strokeDasharray="5 8"/>)}
      {[.25,.5,.75].map((n,i)=><path key={`h${i}`} d={`M0 ${mapH*n} H${W*.84}`} stroke="#827B65" strokeOpacity=".2" strokeDasharray="5 8"/>)}
      <path d={`M${W*.10} ${mapH*.18} Q${W*.18} ${mapH*.08} ${W*.25} ${mapH*.22} L${W*.28} ${mapH*.46} Q${W*.21} ${mapH*.6} ${W*.12} ${mapH*.5}Z M${W*.68} ${mapH*.15} Q${W*.78} ${mapH*.07} ${W*.9} ${mapH*.21} L${W*.95} ${mapH*.46} Q${W*.9} ${mapH*.58} ${W*.8} ${mapH*.51} L${W*.7} ${mapH*.38}Z`} fill="#8B977F" stroke="#687365" strokeWidth="3"/>
      <path d={`M${W*.18} ${mapH*.48} C${W*.34} ${mapH*.15} ${W*.57} ${mapH*.79} ${W*.83} ${mapH*.45}`} fill="none" stroke="#A14E43" strokeWidth={portrait?4:6} strokeDasharray={W*.76} strokeDashoffset={W*.76*(1-p)} strokeLinecap="round"/>
      <circle cx={W*.18} cy={mapH*.48} r={portrait?7:10} fill="#A14E43"/><circle cx={W*.83} cy={mapH*.45} r={portrait?7:10} fill="#A14E43"/>
      <g transform={`translate(${W*.84*p} ${mapH*(.48-.15*p)})`}>
        <circle r={portrait?12:15} fill="#112935" stroke="#F4E6C7" strokeWidth="3"/>
        <path d={`M${-W*.012} 2 l${W*.012} -${H*.018} l${W*.012} ${H*.018}Z`} fill={GOLD}/>
      </g>
      <text x={W*.18} y={mapH*.68} textAnchor="middle" fill="#3E5148" fontFamily="Noto Sans Arabic" fontSize={portrait?15:18} direction="rtl">نيويورك</text>
      <text x={W*.83} y={mapH*.68} textAnchor="middle" fill="#3E5148" fontFamily="Noto Sans Arabic" fontSize={portrait?15:18} direction="rtl">الأزور</text>
    </g>
    <g transform={`translate(${W*.5} ${H*(portrait?.77:.80)})`}>
      <circle r={portrait?30:35} fill="none" stroke={TEAL} strokeOpacity=".28" strokeWidth="2"/>
      <path d="M0 -25 L7 0 0 25 -7 0Z" fill={GOLD} transform={`rotate(${frame*.55})`}/>
      <text y={portrait?66:63} textAnchor="middle" fill="#CFD6D0" fontFamily="Noto Sans Arabic" fontSize={portrait?16:19} direction="rtl">المسار التقريبي عبر الأطلسي</text>
    </g>
    <path d={`M${px} ${py} l-22 -12 18 2 10 -12 2 16 16 10 -20 -2 -8 13Z`} fill="#F4E6C5" stroke="#8B6240" strokeWidth="2"/>
  </svg>;
}
function CargoScene({frame,portrait}){
  const {width:W,height:H}=useVideoConfig(),f=frame/24,p=smooth((f-(portrait?8:34))/3);
  const count=Math.round(1701*p).toLocaleString('ar-EG');
  return <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%'}}>
    <defs><linearGradient id="cargo-bg-v5" x2="0" y2="1"><stop stopColor="#292920"/><stop offset="1" stopColor="#0B1720"/></linearGradient><linearGradient id="cargo-card-v5" x2="0" y2="1"><stop stopColor="#E4D4B0"/><stop offset="1" stopColor="#B9A47A"/></linearGradient></defs>
    <rect width={W} height={H} fill="url(#cargo-bg-v5)"/><circle cx={W*.5} cy={H*.52} r={H*.52} fill="#D8B675" fillOpacity=".07"/>
    <g transform={`translate(${W*.5} ${H*(portrait?.42:.42)}) scale(${.7+.3*p})`}>
      <rect x={-W*.34} y={-H*.21} width={W*.68} height={H*.42} rx={portrait?22:26} fill="url(#cargo-card-v5)" stroke="#F1E3C1" strokeWidth="4"/>
      <path d={`M${-W*.29} ${-H*.12} H${W*.29} M${-W*.29} ${H*.12} H${W*.29}`} stroke="#806C49" strokeOpacity=".45" strokeWidth="3"/>
      <text y={-H*.075} textAnchor="middle" fill="#67543A" fontFamily="Noto Sans Arabic" fontSize={portrait?20:24} direction="rtl">سجل الشحنة</text>
      <text y={portrait?H*.035:H*.045} textAnchor="middle" fill="#20323A" fontFamily="Noto Sans Arabic" fontSize={portrait?59:76} fontWeight="700" direction="rtl">{count}</text>
      <text y={H*.105} textAnchor="middle" fill="#67543A" fontFamily="Noto Sans Arabic" fontSize={portrait?19:23} direction="rtl">برميل كحول مُدرج في السجل</text>
      <circle cx={W*.25} cy={-H*.13} r={portrait?19:23} fill="none" stroke="#9E4C40" strokeWidth="4" strokeDasharray="5 4" transform={`rotate(${frame*1.3})`}/>
    </g>
    {Array.from({length:5},(_,i)=>{
      const x=W*(.22+i*.14),y=H*(portrait?.72:.76),rise=(1-p)*H*.07;
      return <g key={i} transform={`translate(${x} ${y+rise})`} opacity={p}>
        <ellipse cy="0" rx={portrait?34:43} ry={portrait?12:15} fill="#050B0F" opacity=".55"/>
        <path d={`M-28 -8 Q-35 -36 -24 -61 Q0 -72 24 -61 Q35 -36 28 -8 Q0 1 -28 -8Z`} fill={i%2?'#71563A':'#86643F'} stroke={GOLD} strokeWidth="3"/>
        <path d="M-31 -22 Q0 -12 31 -22 M-29 -45 Q0 -35 29 -45" fill="none" stroke="#C9A36B" strokeWidth="4"/>
        <ellipse cy="-61" rx="24" ry="7" fill="#A38355" stroke={GOLD} strokeWidth="2"/>
      </g>;
    })}
  </svg>;
}
function LogbookScene({frame,portrait}){
  const {width:W,height:H}=useVideoConfig(),f=frame/24,start=portrait?25:56,p=smooth((f-start)/1.2),line=smooth((f-start-1)/2.6);
  const x=W*(portrait?.08:.13),y=H*(portrait?.19:.18),w=W*(portrait?.84:.74),h=H*(portrait?.61:.64);
  return <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%'}}>
    <defs><linearGradient id="paper-bg-v5" x2="0" y2="1"><stop stopColor="#23313A"/><stop offset="1" stopColor="#09141D"/></linearGradient><linearGradient id="paper-v5" x2="0" y2="1"><stop stopColor="#EEE1C2"/><stop offset="1" stopColor="#C8B58E"/></linearGradient></defs>
    <rect width={W} height={H} fill="url(#paper-bg-v5)"/><rect x={x} y={y+H*.018} width={w} height={h} rx={portrait?15:19} fill="#050B10" opacity=".4"/>
    <g transform={`translate(${x} ${y}) rotate(${Math.sin(frame*.025)*.5} ${w/2} ${h/2})`} opacity={p}>
      <rect width={w} height={h} rx={portrait?14:18} fill="url(#paper-v5)" stroke="#F7EBCB" strokeWidth="4"/>
      <path d={`M${w*.14} 0 V${h} M${w*.18} ${h*.13} H${w*.91}`} stroke="#A16B51" strokeOpacity=".48" strokeWidth="2"/>
      <text x={w*.55} y={h*.09} textAnchor="middle" fill="#7D5944" fontFamily="Noto Naskh Arabic" fontSize={portrait?20:25} direction="rtl">مقتطف من سجلّ السفينة</text>
      {[.25,.37,.49,.61,.73].map((r,i)=><path key={i} d={`M${w*.24} ${h*r} H${w*(.24+.65*line)}`} stroke="#6B6554" strokeOpacity=".54" strokeWidth={portrait?2.2:3} strokeDasharray={w*.65} strokeDashoffset={w*.65*(1-line)}/>)}
      <text x={w*.77} y={h*.86} textAnchor="middle" fill="#9D493D" fontFamily="Noto Sans Arabic" fontSize={portrait?22:28} fontWeight="700" direction="rtl">٢٥ نوفمبر ١٨٧٢</text>
      <g transform={`translate(${w*(.31+.47*line)} ${h*.62}) rotate(-18)`}>
        <path d={`M0 0 L${W*.012} ${-H*.15} Q${W*.017} ${-H*.18} ${W*.02} ${-H*.15} L${W*.007} 0Z`} fill="#283741"/>
        <path d={`M0 0 L${W*.007} ${-H*.018} L${W*.014} 0Z`} fill="#A74D40"/>
      </g>
    </g>
    <circle cx={W*.83} cy={H*.68} r={H*.055} fill="none" stroke="#B45B4C" strokeWidth="4" strokeDasharray="8 7" opacity={p}/>
  </svg>;
}
function CrewScene({frame}){
  const {width:W,height:H}=useVideoConfig(),p=smooth((frame-8*24)/18);
  return <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%'}}>
    <defs><radialGradient id="crew-bg-v5"><stop stopColor="#26404A"/><stop offset="1" stopColor="#07131D"/></radialGradient></defs>
    <rect width={W} height={H} fill="url(#crew-bg-v5)"/><circle cx={W*.5} cy={H*.48} r={H*.3} fill="none" stroke={GOLD} strokeOpacity=".14" strokeWidth="2" strokeDasharray="5 12"/>
    <text x={W*.5} y={H*.15} textAnchor="middle" fill={GOLD} fontFamily="Noto Sans Arabic" fontSize="24" direction="rtl">القائمة الأخيرة · عشرة أسماء</text>
    <g opacity={p}>
      {Array.from({length:10},(_,i)=>{
        const col=i%5,row=Math.floor(i/5),x=W*(.15+col*.175),y=H*(.39+row*.20),pop=spring({frame:Math.max(0,frame-(8*24+i*5)),fps:24,config:{damping:14,stiffness:110}});
        return <g key={i} transform={`translate(${x} ${y}) scale(${.65+.35*pop})`} opacity={pop}>
          <circle r={H*.064} fill="#112936" stroke={i<3?'#D7AE67':TEAL} strokeWidth="3"/>
          <circle cy={-H*.018} r={H*.012} fill="#E7DFCC"/>
          <path d={`M${-H*.016} ${H*.014} Q0 ${H*.002} ${H*.016} ${H*.014} L${H*.022} ${H*.037} H${-H*.022}Z`} fill="#C3B48F"/>
          <text y={H*.055} textAnchor="middle" fill={GOLD} fontFamily="Noto Sans Arabic" fontSize="12" direction="rtl">{i+1}</text>
        </g>;
      })}
    </g>
  </svg>;
}
function MissingBoatScene({frame,portrait}){
  const {width:W,height:H}=useVideoConfig(),f=frame/24,p=smooth((f-(portrait?16:0))/4),y=H*(portrait?.64:.64);
  const bx=W*(.5+.025*Math.sin(frame*.05)),by=y+Math.sin(frame*.08)*H*.008;
  return <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%'}}>
    <defs><linearGradient id="empty-sky-v5" x2="0" y2="1"><stop stopColor="#556A70"/><stop offset=".62" stopColor="#263F48"/><stop offset=".63" stopColor="#183541"/><stop offset="1" stopColor="#07151E"/></linearGradient></defs>
    <rect width={W} height={H} fill="url(#empty-sky-v5)"/><circle cx={W*.77} cy={H*.24} r={H*.12} fill="#E7D7B1" fillOpacity=".08"/>
    {[0,1,2,3].map(i=><path key={i} d={lineWave(W,y+H*(.06+i*.075),H*.009,frame*.035+i*2,W*.15)} fill="none" stroke="#A7C0C2" strokeOpacity={.23-i*.035} strokeWidth="2"/>)}
    <text x={W*.5} y={H*.18} textAnchor="middle" fill={GOLD} fontFamily="Noto Sans Arabic" fontSize={portrait?22:27} direction="rtl">السفينة طافية… لكن قارب النجاة اختفى</text>
    <g transform={`translate(${bx} ${by})`} opacity={p}>
      <ellipse cy={H*.025} rx={W*(portrait?.25:.12)} ry={H*.016} fill="#03090D" opacity=".65"/>
      <path d={`M${-W*(portrait?.22:.12)} 0 Q0 ${H*.045} ${W*(portrait?.22:.12)} 0 L${W*(portrait?.17:.12)} ${H*.045} Q0 ${H*.07} ${-W*(portrait?.17:.12)} ${H*.045}Z`} fill="none" stroke={GOLD} strokeWidth={portrait?4:5} strokeDasharray="12 8"/>
      <path d={`M${-W*.05} ${H*.012} V${-H*.06} M${W*.05} ${H*.012} V${-H*.06}`} stroke="#D7BE89" strokeWidth="3" strokeDasharray="6 5"/>
      <path d={`M${-W*.05} ${-H*.055} Q0 ${-H*.073} ${W*.05} ${-H*.055}`} fill="none" stroke="#D7BE89" strokeWidth="3"/>
    </g>
    <circle cx={bx} cy={by} r={H*(.1+.025*Math.sin(frame*.08))} fill="none" stroke={GOLD} strokeOpacity={.22+.2*p} strokeWidth="2"/>
  </svg>;
}
function InvestigationScene({frame,portrait}){
  const {width:W,height:H}=useVideoConfig(),f=frame/24,p=smooth((f-(portrait?35:102))/2);
  const positions=portrait?[[.12,.25],[.56,.25],[.12,.52],[.56,.52]]:[[.09,.25],[.37,.25],[.65,.25],[.22,.52],[.52,.52]];
  return <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%'}}>
    <defs><radialGradient id="board-bg-v5"><stop stopColor="#253740"/><stop offset="1" stopColor="#09141D"/></radialGradient></defs>
    <rect width={W} height={H} fill="url(#board-bg-v5)"/><path d={`M${W*.05} ${H*.1} H${W*.95} V${H*.88} H${W*.05}Z`} fill="#172831" stroke="#B89058" strokeOpacity=".35" strokeWidth="3"/>
    <text x={W*.5} y={H*.16} textAnchor="middle" fill={GOLD} fontFamily="Noto Sans Arabic" fontSize={portrait?22:26} direction="rtl">ما الذي كان معروفًا عند العثور عليها؟</text>
    <path d={`M${W*.2} ${H*.36} Q${W*.5} ${H*.48} ${W*.78} ${H*.36} M${W*.22} ${H*.66} Q${W*.48} ${H*.53} ${W*.76} ${H*.66}`} fill="none" stroke="#A85348" strokeWidth="2.5" strokeDasharray="8 10" strokeDashoffset={1200*(1-p)} opacity=".7"/>
    {positions.map(([ax,ay],i)=>{
      const delay=(portrait?35:102)+i*1.8,sp=spring({frame:Math.max(0,frame-delay*24),fps:24,config:{damping:15,stiffness:105}}),x=W*ax,y=H*ay;
      return <g key={i} transform={`translate(${(1-sp)*W*.08} ${(1-sp)*H*.04})`} opacity={sp}>
        <rect x={x} y={y} width={W*(portrait?.31:.23)} height={H*(portrait?.17:.19)} rx="14" fill={i===2?'#DED0AD':'#11232E'} stroke={i===2?'#D2A85E':TEAL} strokeWidth="3"/>
        {i===0&&<g transform={`translate(${x+W*.115} ${y+H*.10})`}><rect x={-W*.04} y={-H*.055} width={W*.08} height={H*.11} rx="8" fill="#172F39" stroke={GOLD} strokeWidth="3"/><path d={`M${-W*.025} ${H*.018} Q0 ${-H*.005} ${W*.025} ${H*.018}`} fill="none" stroke={TEAL} strokeWidth="4"/></g>}
        {i===1&&<g transform={`translate(${x+W*.115} ${y+H*.10})`}><circle r={H*.045} fill="none" stroke={GOLD} strokeWidth="4"/><path d={`M${W*.03} ${H*.03} L${W*.06} ${H*.06}`} stroke={GOLD} strokeWidth="6"/></g>}
        {i===2&&<g stroke="#766349" strokeWidth="3" opacity=".65"><path d={`M${x+W*.035} ${y+H*.05} H${x+W*.16} M${x+W*.035} ${y+H*.085} H${x+W*.17} M${x+W*.035} ${y+H*.12} H${x+W*.13}`}/></g>}
        {i===3&&<g transform={`translate(${x+W*.11} ${y+H*.12})`}><path d="M-30 12 Q0 -8 30 12" fill="none" stroke={TEAL} strokeWidth="5"/><path d="M-20 2 V-20 M20 2 V-20 M-20 -20 H20" fill="none" stroke={GOLD} strokeWidth="5"/></g>}
        {i===4&&<g transform={`translate(${x+W*.11} ${y+H*.12})`}><circle r={H*.04} fill="none" stroke="#A84E43" strokeWidth="4" strokeDasharray="6 5"/><text y={H*.015} textAnchor="middle" fill="#A84E43" fontSize={H*.05} fontFamily="Noto Sans Arabic">?</text></g>}
        <text x={x+W*.115} y={y+H*.174} textAnchor="middle" fill={i===2?'#62533B':CREAM} fontFamily="Noto Sans Arabic" fontSize={portrait?15:15} fontWeight="600" direction="rtl">{['المضخة','البوصلة','السجل','مستوى الماء','قارب النجاة'][i]}</text>
      </g>;
    })}
  </svg>;
}
function TheoryScene({frame,portrait}){
  const {width:W,height:H}=useVideoConfig(),f=frame/24,start=portrait?30:150;
  const defs=[['الماء والمضخة','ماذا تعني آثار الماء؟'],['أبخرة الشحنة','هل خشي الطاقم حريقًا؟'],['الطقس والتيارات','هل غيّر البحر مسارها؟']];
  return <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%'}}>
    <defs><linearGradient id="theory-bg-v5" x2="0" y2="1"><stop stopColor="#142B37"/><stop offset="1" stopColor="#07131D"/></linearGradient></defs>
    <rect width={W} height={H} fill="url(#theory-bg-v5)"/><circle cx={W*.5} cy={H*.53} r={H*.48} fill="#6BA3A7" fillOpacity=".055"/>
    <text x={W*.5} y={H*(portrait?.13:.15)} textAnchor="middle" fill={GOLD} fontFamily="Noto Naskh Arabic" fontSize={portrait?28:37} fontWeight="700" direction="rtl">ثلاث فرضيات… ولا واحدة مثبتة</text>
    {defs.map(([title,sub],i)=>{
      const delay=start+i*4,sp=spring({frame:Math.max(0,(f-delay)*24),fps:24,config:{damping:17,stiffness:95}});
      const w=portrait?W*.82:W*.27,h=portrait?H*.17:H*.40,x=portrait?W*.09:W*(.06+i*.30),y=portrait?H*(.24+i*.19):H*.31;
      const hot=Math.max(0,1-Math.abs((f-(delay+3))/3));
      return <g key={title} transform={`translate(0 ${(1-sp)*H*.05})`} opacity={sp}>
        <rect x={x} y={y} width={w} height={h} rx="18" fill={hot>.05?'#1D3843':'#10242E'} stroke={i===1?'#D5AB60':TEAL} strokeOpacity={.55+.4*hot} strokeWidth="3"/>
        <circle cx={x+w*.5} cy={y+h*.36} r={h*.2} fill="#17313B" stroke={GOLD} strokeOpacity=".5" strokeWidth="2"/>
        {i===0&&<g transform={`translate(${x+w*.5} ${y+h*.36})`}><path d="M-34 5 Q-16 -5 0 5 T34 5 M-23 0 V-28 M23 0 V-28 M-23 -28 H23" fill="none" stroke={TEAL} strokeWidth="4"/><path d="M0 -25 V-41" stroke={GOLD} strokeWidth="4"/></g>}
        {i===1&&<g transform={`translate(${x+w*.5} ${y+h*.36})`}>{[0,1,2].map((n)=><circle key={n} cx={Math.sin(frame*.04+n)*18} cy={-n*18} r={7-n} fill={GOLD} opacity={.8-n*.15}/>)}</g>}
        {i===2&&<g transform={`translate(${x+w*.5} ${y+h*.36})`}>{[-1,0,1].map((n)=><path key={n} d={`M-38 ${n*15} Q0 ${n*15-8*Math.sin(frame*.03+n)} 38 ${n*15}`} fill="none" stroke={TEAL} strokeWidth="3"/>)}<path d="M0 -32 V32" stroke={GOLD} strokeDasharray="5 5"/></g>}
        <text x={x+w*.5} y={y+h*.73} textAnchor="middle" fill={CREAM} fontFamily="Noto Sans Arabic" fontSize={portrait?21:23} fontWeight="700" direction="rtl">{title}</text>
        <text x={x+w*.5} y={y+h*.86} textAnchor="middle" fill="#C4CFCC" fontFamily="Noto Sans Arabic" fontSize={portrait?15:16} direction="rtl">{sub}</text>
      </g>;
    })}
  </svg>;
}
function FinalScene({frame,portrait}){
  const {width:W,height:H}=useVideoConfig(),p=smooth((frame/24-(portrait?42:217))/2);
  return <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%'}}>
    <defs><linearGradient id="final-bg-v5" x2="0" y2="1"><stop stopColor="#1C3340"/><stop offset=".55" stopColor="#52636A"/><stop offset=".56" stopColor="#17333F"/><stop offset="1" stopColor="#07131D"/></linearGradient><radialGradient id="final-light-v5"><stop stopColor="#E9D6AD" stopOpacity=".3"/><stop offset="1" stopColor="#E9D6AD" stopOpacity="0"/></radialGradient></defs>
    <rect width={W} height={H} fill="url(#final-bg-v5)"/><circle cx={W*.5} cy={H*.39} r={H*.34} fill="url(#final-light-v5)"/>
    {[0,1,2,3,4].map(i=><path key={i} d={lineWave(W,H*(.58+i*.075),H*.008,frame*.025+i*1.4,W*.17)} fill="none" stroke="#B7C5C0" strokeOpacity={.22-i*.025} strokeWidth="2"/>)}
    <circle cx={W*.5} cy={H*.36} r={H*(.09+.025*p)} fill="none" stroke={GOLD} strokeOpacity={.55} strokeWidth="3" strokeDasharray="8 10" transform={`rotate(${frame*.25} ${W*.5} ${H*.36})`}/>
    <text x={W*.5} y={H*.39} textAnchor="middle" fill={CREAM} fontFamily="Noto Naskh Arabic" fontSize={portrait?54:68} fontWeight="700">؟</text>
    <path d={`M${W*.22} ${H*.80} Q${W*.5} ${H*.77} ${W*.78} ${H*.80}`} fill="none" stroke={GOLD} strokeOpacity=".55" strokeWidth="3" strokeDasharray={W*.56} strokeDashoffset={W*.56*(1-p)}/>
  </svg>;
}
function MotionArtwork({mode}){
  const frame=useCurrentFrame(),{width:W,height:H}=useVideoConfig(),portrait=mode==='short',s=frame/24;
  const active=portrait
    ?(s<8?'opening':s<16?'crew':s<25?'missing':s<30?'log':s<43?'theory':'final')
    :(s<8?'opening':s<34?'journey':s<56?'cargo':s<67?'log':s<103?'discovery':s<151?'investigation':s<217?'theory':'final');
  const ranges=portrait
    ?{opening:[0,8],crew:[8,16],missing:[16,25],log:[25,30],theory:[30,43],final:[42,45.84]}
    :{opening:[0,8],journey:[8,34],cargo:[34,56],log:[56,67],discovery:[67,103],investigation:[102,151],theory:[150,217],final:[217,226.38]};
  const [a,b]=ranges[active],sceneStyle={position:'absolute',inset:0,opacity:sceneAlpha(s,a,b,.85),transform:`scale(${1.025-.025*smooth((s-a)/1.3)})`};
  return <>
    <div style={sceneStyle}>
      {active==='opening'&&<><OceanArt frame={frame} mode={mode}/><svg viewBox={`0 0 ${W} ${H}`} style={{position:'absolute',inset:0,width:'100%',height:'100%'}}><MainArt frame={frame} mode={mode}/></svg></>}
      {active==='journey'&&<MapJourneyScene frame={frame} portrait={false}/>}
      {active==='cargo'&&<CargoScene frame={frame} portrait={false}/>}
      {active==='log'&&<LogbookScene frame={frame} portrait={portrait}/>}
      {active==='crew'&&<CrewScene frame={frame}/>}
      {active==='missing'&&<MissingBoatScene frame={frame} portrait/>}
      {active==='discovery'&&<><OceanArt frame={frame} mode={mode}/><svg viewBox={`0 0 ${W} ${H}`} style={{position:'absolute',inset:0,width:'100%',height:'100%'}}><MainArt frame={frame} mode={mode}/></svg></>}
      {active==='investigation'&&<InvestigationScene frame={frame} portrait={false}/>}
      {active==='theory'&&<TheoryScene frame={frame} portrait={portrait}/>}
      {active==='final'&&<FinalScene frame={frame} portrait={portrait}/>}
    </div>
    {!portrait&&s<8&&titleCard(s,false)}
    {portrait&&s<7&&titleCard(s,true)}
    <div style={{position:'absolute',top:portrait?75:34,right:portrait?26:50,zIndex:30,color:CREAM,font:'700 16px "Noto Sans Arabic"',textShadow:'0 2px 8px #000',direction:'rtl'}}>وراء الحكاية <span style={{color:GOLD,marginInlineStart:8}}>·</span></div>
    <div style={{position:'absolute',left:portrait?28:64,right:portrait?28:64,bottom:portrait?185:28,height:2,zIndex:35,background:'rgba(255,255,255,.15)'}}><div style={{width:`${100*frame/(portrait?1100:5433)}%`,height:'100%',background:GOLD}}/></div>
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
  <Composition id="MaryCelesteMotionV5" component={MainVideo} durationInFrames={5433} width={1280} height={720} fps={24} defaultProps={{cues:[]}}/>
  <Composition id="MaryCelesteShortMotionV5" component={ShortVideo} durationInFrames={1100} width={720} height={1280} fps={24} defaultProps={{cues:[]}}/>
</>;
