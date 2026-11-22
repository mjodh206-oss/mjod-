import React from 'react';
import {AbsoluteFill, Img, Sequence, useCurrentFrame, useVideoConfig, staticFile, interpolate, Easing} from 'remotion';
import '@fontsource/noto-sans-arabic/arabic-400.css';
import '@fontsource/noto-sans-arabic/arabic-600.css';
import '@fontsource/noto-sans-arabic/arabic-700.css';
import '@fontsource/noto-naskh-arabic/arabic-400.css';

const GOLD='#D4B06A', CREAM='#F4F0E7', INK='#08121C', MUTED='#C8C5BC';
const ASSET={ocean:'ocean.jpg',ship:'ship.png',map:'map.jpg',deck:'deck.jpg',docs:'documents.jpg'};
const clamp={extrapolateLeft:'clamp',extrapolateRight:'clamp'};
const fade=(f,a=0,b=12)=>interpolate(f,[a,b],[0,1],clamp);
const ease=Easing.bezier(0.22,0.75,0.18,1);

function Brand({vertical=false}){
  return <div style={{position:'absolute',top:vertical?66:40,right:vertical?34:66,display:'flex',alignItems:'center',gap:13,color:CREAM,zIndex:20,fontFamily:'Noto Sans Arabic',fontSize:vertical?21:19,fontWeight:700,letterSpacing:'.03em'}}>
    <span>وراء الحكاية</span><i style={{height:24,width:2,background:GOLD,display:'block'}} />
  </div>;
}
function Grain(){
  const f=useCurrentFrame();
  const dots=Array.from({length:30},(_,i)=>({x:(i*173+f*(i%3+1)*.4)%1280,y:(i*97+f*(i%4+1)*.25)%720,r:1+(i%2),o:.06+(i%4)*.012}));
  return <svg viewBox="0 0 1280 720" preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%',pointerEvents:'none',opacity:.38}}>{dots.map((d,i)=><circle key={i} cx={d.x} cy={d.y} r={d.r} fill="#D8D0BC" opacity={d.o}/>)}</svg>;
}
function WaveLines({frame,vertical=false}){
  const width=1280,height=720;
  return <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%',pointerEvents:'none',opacity:.46}}>
    {[0,1,2,3].map((j)=>{
      const y=510+j*48+(vertical?100:0); let d=`M -50 ${y}`;
      for(let x=0;x<=1360;x+=40){const yy=y+Math.sin((x+frame*(1.1+j*.15))/85+j*1.8)*7+Math.sin((x-frame*.55)/39+j)*2;d+=` L ${x} ${yy}`;}
      return <path key={j} d={d} fill="none" stroke={j===0?'#DDE6E7':'#8399A2'} strokeWidth={j===0?2:1} opacity={j===0?.28:.18}/>;
    })}
  </svg>;
}
function Backdrop({name,frame,duration,vertical=false,brightness=1}){
  const p=frame/Math.max(1,duration);
  const zoom=interpolate(p,[0,1],name==='map'?[1.03,1.14]:[1.06,1.14],{easing:ease});
  const x=interpolate(p,[0,1],[-1.2,1.2]);
  return <AbsoluteFill style={{overflow:'hidden',background:'#07111A'}}>
    <Img src={staticFile(ASSET[name]||ASSET.ocean)} style={{width:'100%',height:'100%',objectFit:'cover',transform:`scale(${zoom}) translate(${x}%,${vertical?1:0}%)`,filter:`brightness(${brightness}) saturate(.82) contrast(1.04)`}} />
    <AbsoluteFill style={{background:'linear-gradient(90deg,rgba(3,9,15,.70) 0%,rgba(3,9,15,.14) 47%,rgba(3,9,15,.58) 100%),linear-gradient(0deg,rgba(3,8,14,.82) 0%,transparent 42%,rgba(3,8,14,.26) 100%)'}}/>
    <AbsoluteFill style={{boxShadow:'inset 0 0 130px rgba(0,0,0,.56)',border:'1px solid rgba(219,190,130,.18)'}}/>
  </AbsoluteFill>;
}
function SectionTitle({kicker,title,vertical=false}){
  return <div style={{position:'absolute',top:vertical?165:152,left:vertical?38:92,right:vertical?38:650,textAlign:'right',zIndex:8,direction:'rtl',color:CREAM}}>
    <div style={{fontFamily:'Noto Sans Arabic',fontSize:vertical?17:16,fontWeight:600,color:GOLD,marginBottom:11,letterSpacing:'.02em'}}>{kicker}</div>
    <div style={{fontFamily:'Noto Naskh Arabic',fontSize:vertical?43:51,lineHeight:1.35,fontWeight:700,textShadow:'0 5px 24px rgba(0,0,0,.55)'}}>{title}</div>
  </div>;
}
function Caption({cues,frame,vertical=false}){
  const cue=cues?.find(c=>frame>=c.start&&frame<c.end); if(!cue) return null;
  const lines=(cue.text||'').split('\n');
  return <div style={{position:'absolute',bottom:vertical?250:76,left:vertical?28:150,right:vertical?28:150,zIndex:30,textAlign:'center',direction:'rtl',opacity:.97}}>
    <div style={{display:'inline-block',maxWidth:'100%',padding:vertical?'13px 18px':'12px 28px',background:'rgba(4,10,16,.72)',borderBottom:`2px solid ${GOLD}`,color:CREAM,fontFamily:'Noto Sans Arabic',fontSize:vertical?25:24,lineHeight:1.6,fontWeight:600,textShadow:'0 2px 8px rgba(0,0,0,.95)'}}>{lines.map((l,i)=><React.Fragment key={i}>{i>0&&<br/>}{l}</React.Fragment>)}</div>
  </div>;
}
function BottomProgress({sectionIndex,progress,vertical=false}){
  return <div style={{position:'absolute',left:vertical?35:72,right:vertical?35:72,bottom:vertical?178:37,height:2,zIndex:22,background:'rgba(255,255,255,.18)'}}>
    <div style={{width:`${Math.max(2,progress*100)}%`,height:'100%',background:GOLD,boxShadow:'0 0 12px rgba(212,176,106,.35)'}}/>
    {!vertical&&<span style={{position:'absolute',left:0,bottom:13,color:'rgba(244,240,231,.68)',font:'500 12px Noto Sans Arabic',direction:'rtl'}}>وراء الحكاية  /  {String(sectionIndex+1).padStart(2,'0')}</span>}
  </div>;
}
function Ship({frame,duration,vertical=false,recede=false}){
  const p=frame/Math.max(1,duration), bob=Math.sin(frame*.075)*7, roll=Math.sin(frame*.042)*1.05;
  const width=vertical?650:960;
  const scale=recede?interpolate(p,[0,1],[.72,.45]):interpolate(p,[0,1],[.72,.88]);
  const x=vertical?interpolate(p,[0,1],[-15,80]):interpolate(p,[0,1],[110,210]);
  const y=vertical?360+bob:135+bob;
  return <>
    <div style={{position:'absolute',left:`${x}px`,top:`${y}px`,width:`${width}px`,height:vertical?520:590,transform:`scale(${scale}) rotate(${roll}deg)`,transformOrigin:'center bottom',filter:'drop-shadow(0 32px 28px rgba(0,0,0,.58))',zIndex:5}}><Img src={staticFile(ASSET.ship)} style={{width:'100%',height:'100%',objectFit:'contain'}}/></div>
    <WaveLines frame={frame} vertical={vertical}/>
  </>;
}
function MapRoute({frame,duration,vertical=false}){
  const p=interpolate(frame,[0,duration*.76],[0,1],clamp); const d='M 202 425 C 334 390 444 385 566 356 S 794 310 904 291';
  const labels=[{x:196,y:465,t:'نيويورك',date:'٧ نوفمبر'},{x:610,y:327,t:'جزر الأزور',date:'٢٥ نوفمبر'},{x:951,y:267,t:'جنوة',date:'الوجهة'}];
  const shift=vertical?'translate(0 65) scale(.88 .90)':'translate(0 0)';
  return <>
    <svg viewBox="0 0 1280 720" preserveAspectRatio="none" style={{position:'absolute',inset:0,width:'100%',height:'100%',zIndex:5}}>
      <g transform={shift}><path d={d} fill="none" stroke="rgba(7,17,27,.38)" strokeWidth={vertical?2:4}/><path d={d} fill="none" stroke={GOLD} strokeWidth={vertical?2:3} strokeDasharray="900" strokeDashoffset={900*(1-p)} strokeLinecap="round"/>
      {labels.map((l,i)=><g key={l.t}><circle cx={l.x} cy={l.y} r={8} fill={GOLD} opacity={p>i*.32?.98:0}/><circle cx={l.x} cy={l.y} r={18} fill="none" stroke={GOLD} opacity={p>i*.32?.45:0}/><text x={l.x} y={l.y+42} textAnchor="middle" direction="rtl" fill={CREAM} fontFamily="Noto Sans Arabic" fontSize="21" fontWeight="700" opacity={p>i*.32?1:0}>{l.t}</text><text x={l.x} y={l.y+65} textAnchor="middle" direction="rtl" fill={GOLD} fontFamily="Noto Sans Arabic" fontSize="15" opacity={p>i*.32?1:0}>{l.date}</text></g>)}
      </g>
    </svg>
    {!vertical&&<div style={{position:'absolute',left:60,top:112,color:'rgba(244,240,231,.72)',font:'600 14px Noto Sans Arabic',letterSpacing:'.08em',zIndex:7}}>شمال الأطلسي  ·  1872</div>}
  </>;
}
function Evidence({frame,duration,vertical=false}){
  const f=(n)=>fade(frame,n,n+16);
  const items=[['01','قارب النجاة','مفقود'],['02','المضخة','مفككة جزئيًا'],['03','السفينة','بقيت طافية']];
  const right=vertical?36:90, top=vertical?750:250, width=vertical?648:430;
  return <div style={{position:'absolute',right,top,width,zIndex:10,direction:'rtl',color:CREAM}}>
    <div style={{font:'600 15px Noto Sans Arabic',color:GOLD,marginBottom:18,opacity:f(4)}}>ما وثّقه طاقم الإنقاذ</div>
    {items.map((x,i)=>{
      const op=f(12+i*15), yy=interpolate(frame,[12+i*15,28+i*15],[24,0],clamp);
      return <div key={x[0]} style={{display:'flex',alignItems:'center',gap:16,borderTop:'1px solid rgba(243,236,220,.26)',padding:'17px 2px',opacity:op,transform:`translateY(${yy}px)`}}>
        <span style={{font:'500 13px Noto Sans Arabic',color:GOLD,letterSpacing:'.08em'}}>{x[0]}</span><span style={{font:'600 23px Noto Sans Arabic',flex:1}}>{x[1]}</span><span style={{font:'400 19px Noto Naskh Arabic',color:'#D5D0C5'}}>{x[2]}</span>
      </div>;
    })}
  </div>;
}
function Investigation({frame,duration,vertical=false}){
  const lines=['إجراءات إنقاذ وفحص في جبل طارق','تساؤلات حول مكافأة الإنقاذ','لا سبب مؤكد لمغادرة السفينة'];
  return <div style={{position:'absolute',left:vertical?36:92,right:vertical?36:650,top:vertical?700:255,zIndex:12,direction:'rtl',textAlign:'right'}}>
    <div style={{fontFamily:'Noto Naskh Arabic',fontSize:vertical?36:38,color:CREAM,marginBottom:22,textShadow:'0 3px 15px #000'}}>ما الذي أثبته التحقيق؟</div>
    {lines.map((t,i)=>{
      const p=fade(frame,12+i*17,30+i*17);
      return <div key={t} style={{display:'flex',alignItems:'center',gap:16,margin:'14px 0',opacity:p,transform:`translateX(${interpolate(frame,[12+i*17,30+i*17],[38,0],clamp)}px)`}}><span style={{width:22,height:2,background:GOLD,display:'block'}}/><span style={{font:'600 21px Noto Sans Arabic',color:i===2?GOLD:CREAM}}>{t}</span></div>;
    })}
    <div style={{marginTop:25,font:'400 18px Noto Naskh Arabic',color:'rgba(244,240,231,.78)',opacity:fade(frame,58,80)}}>الشك ليس دليلًا.</div>
  </div>;
}
function HypoIcon({type}){
  const paths=[['M3 16 C8 12 13 20 18 16 S28 12 33 16','M3 24 C8 20 13 28 18 24 S28 20 33 24'],['M8 27 C4 20 13 19 9 12 C6 7 12 6 12 3','M20 27 C16 20 25 19 21 12 C18 7 24 6 24 3'],['M3 10 H22 Q29 10 29 5','M3 18 H30 Q36 18 36 13','M3 26 H22 Q29 26 29 31']][type];
  return <svg width="40" height="36" viewBox="0 0 40 36" fill="none" stroke={GOLD} strokeWidth="2" strokeLinecap="round">{paths.map((d,i)=><path key={i} d={d}/>)}</svg>;
}
function HypothesisCards({frame,duration,vertical=false}){
  const cards=[['01','الماء والمضخة','هل خشي القبطان أن يرتفع الماء؟'],['02','أبخرة الشحنة','احتمالٌ لم يثبت في السجل.'],['03','الطقس والانجراف','تفسيرٌ لمسار السفينة، لا لقرار مغادرتها.']];
  const y=vertical?520:250;
  return <div style={{position:'absolute',left:vertical?34:86,right:vertical?34:86,top:y,zIndex:11,direction:'rtl'}}>
    <div style={{font:'700 34px Noto Naskh Arabic',color:CREAM,marginBottom:20,textAlign:'right'}}>ثلاث فرضيات. ولا جواب محسوم.</div>
    <div style={{display:'grid',gridTemplateColumns:vertical?'1fr':'repeat(3, 1fr)',gap:vertical?12:18}}>{cards.map((c,i)=>{
      const op=fade(frame,12+i*9,27+i*9); const yy=interpolate(frame,[12+i*9,28+i*9],[24,0],clamp);
      return <div key={c[0]} style={{minHeight:vertical?112:190,padding:vertical?'16px 18px':'20px 22px',background:'linear-gradient(135deg,rgba(7,17,28,.86),rgba(7,17,28,.55))',borderTop:`2px solid ${GOLD}`,boxShadow:'0 14px 40px rgba(0,0,0,.2)',opacity:op,transform:`translateY(${yy}px)`,direction:'rtl',textAlign:'right'}}>
        <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',color:GOLD,font:'600 13px Noto Sans Arabic'}}><span>{c[0]}</span><HypoIcon type={i}/></div>
        <div style={{font:'700 21px Noto Sans Arabic',color:CREAM,marginTop:8}}>{c[1]}</div>
        <div style={{font:'400 16px Noto Naskh Arabic',color:MUTED,marginTop:5,lineHeight:1.6}}>{c[2]}</div>
        <div style={{font:'600 12px Noto Sans Arabic',color:GOLD,marginTop:vertical?4:12}}>فرضية — غير مثبتة</div>
      </div>;
    })}</div>
  </div>;
}
function MainScene({kind,frame,duration,index,progress,cues,timelineFrame=frame,vertical=false}){
  const {width,height}=useVideoConfig();
  const base = kind==='map'?'map':kind==='evidence'?'deck':kind==='inquiry'?'docs':'ocean';
  const sceneOpacity=interpolate(frame,[0,10,duration-10,duration],[0,1,1,0],clamp);
  const chapter=[['01','العثور','سفينة بلا طاقم'],['02','الرحلة','من نيويورك إلى الأزور'],['03','ما وُجد','السفينة بقيت'],['04','التحقيق','ما الذي لا تثبته الأدلة؟'],['05','الفرضيات','احتمالات لا أجوبة'],['06','الخاتمة','ما الذي نعرفه فعلًا؟']][index];
  return <AbsoluteFill style={{opacity:sceneOpacity,overflow:'hidden',background:INK}}>
    <Backdrop name={base} frame={frame} duration={duration} vertical={vertical} brightness={kind==='hypotheses'?.57:1}/>
    {kind==='hero'&&<><Ship frame={frame} duration={duration} vertical={vertical}/><SectionTitle kicker="ماري سيليست  /  1872" title="سفينة بلا طاقم" vertical={vertical}/><div style={{position:'absolute',left:vertical?40:90,top:vertical?345:365,zIndex:9,color:GOLD,font:'500 17px Noto Sans Arabic',direction:'rtl'}}>الأطلسي  ·  ٤ ديسمبر ١٨٧٢</div></>}
    {kind==='map'&&<><MapRoute frame={frame} duration={duration} vertical={vertical}/><SectionTitle kicker="مسار الرحلة" title="من نيويورك إلى جنوة" vertical={vertical}/><div style={{position:'absolute',left:vertical?38:92,top:vertical?405:350,zIndex:8,color:CREAM,font:'500 20px Noto Sans Arabic',direction:'rtl'}}>١٬٧٠١ برميل من الكحول الصناعي</div></>}
    {kind==='evidence'&&<><SectionTitle kicker="بعد صعود طاقم دي غراتيا" title="ما بقي على متنها" vertical={vertical}/><Evidence frame={frame} duration={duration} vertical={vertical}/></>}
    {kind==='inquiry'&&<><SectionTitle kicker="جبل طارق" title="التحقيق لم يحسم السبب" vertical={vertical}/><Investigation frame={frame} duration={duration} vertical={vertical}/></>}
    {kind==='hypotheses'&&<><SectionTitle kicker="الوقائع أولًا" title="احتمالات لا أجوبة" vertical={vertical}/><HypothesisCards frame={frame} duration={duration} vertical={vertical}/></>}
    {kind==='close'&&<><Ship frame={frame} duration={duration} vertical={vertical} recede/><div style={{position:'absolute',left:vertical?32:110,right:vertical?32:110,bottom:vertical?455:210,zIndex:12,textAlign:'center',direction:'rtl'}}><div style={{font:'700 36px Noto Naskh Arabic',color:CREAM,textShadow:'0 4px 20px #000'}}>السفينة عادت وحدها.</div><div style={{marginTop:17,font:'600 16px Noto Sans Arabic',color:GOLD}}>وراء الحكاية  ·  قصص حقيقية… وما لم يُحكَ منها</div></div></>}
    <Brand vertical={vertical}/>
    <div style={{position:'absolute',left:vertical?35:72,top:vertical?112:94,zIndex:20,color:'rgba(244,240,231,.75)',font:'500 13px Noto Sans Arabic',direction:'rtl'}}>{chapter[0]}  /  {chapter[1]}</div>
    <BottomProgress sectionIndex={index} progress={progress} vertical={vertical}/>
    <Caption cues={cues} frame={timelineFrame} vertical={vertical}/>
    <Grain/>
  </AbsoluteFill>;
}

const kinds=['hero','map','evidence','inquiry','hypotheses','close'];
function SceneSequence({kind,duration,index,from,total,cues,vertical=false}){
  const frame=useCurrentFrame();
  return <MainScene kind={kind} frame={frame} duration={duration} index={index} progress={(from+frame)/Math.max(1,total)} timelineFrame={from+frame} cues={cues} vertical={vertical}/>;
}
export const EpisodeV3=({sectionFrames=[],cues=[]})=>{
  const total=sectionFrames.reduce((a,b)=>a+b,0); let at=0;
  return <AbsoluteFill style={{background:INK}}>{sectionFrames.map((duration,index)=>{
    const from=at; at+=duration;
    return <Sequence key={index} from={from} durationInFrames={duration} premountFor={1}>
      <SceneSequence kind={kinds[index]} duration={duration} index={index} from={from} total={total} cues={cues}/>
    </Sequence>;
  })}</AbsoluteFill>;
};
export const ShortV3=({sectionFrames=[],cues=[]})=>{
  const kindsShort=['hero','map','hypotheses','close']; const total=sectionFrames.reduce((a,b)=>a+b,0); let at=0;
  return <AbsoluteFill style={{background:INK}}>{sectionFrames.map((duration,index)=>{const from=at;at+=duration;return <Sequence key={index} from={from} durationInFrames={duration} premountFor={1}><SceneSequence kind={kindsShort[index]} duration={duration} index={index===2?4:index===3?5:index} from={from} total={total} cues={cues} vertical/></Sequence>;})}</AbsoluteFill>;
};
