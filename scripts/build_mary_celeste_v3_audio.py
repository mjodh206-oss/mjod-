#!/usr/bin/env python3
"""Assemble the V3 Arabic narration and create a voice-led, side-chained original sound mix.

Effects are synthesized locally (no sampled recordings); the ambience is deliberately quiet
and the transient foley ducks under the narration. Requires NumPy and imageio-ffmpeg.
"""
from __future__ import annotations
import json, re, subprocess
from pathlib import Path
import numpy as np
import imageio_ffmpeg

ROOT=Path(__file__).resolve().parents[1]
AUDIO=ROOT/'audio/episode-01'
OUT=ROOT/'scripts/remotion-v3'
FS=24000; FPS=24; GAP=.26
CLIPS=['v3-01-opening.mp3','v3-02-voyage.mp3','v3-03-discovery.mp3','v3-04-inquiry.mp3','v3-05-theories.mp3','v3-06-ending.mp3']


def decode_mono(path):
    ff=imageio_ffmpeg.get_ffmpeg_exe()
    p=subprocess.run([ff,'-hide_banner','-loglevel','error','-i',str(path),'-f','s16le','-acodec','pcm_s16le','-ac','1','-ar',str(FS),'-'],stdout=subprocess.PIPE,check=True)
    return np.frombuffer(p.stdout,dtype='<i2').astype(np.float32)/32768

def stereo_voice(x):
    return np.column_stack((x,x)).astype(np.float32)

def shaped_noise(n,lo,hi,rng):
    x=rng.standard_normal(n).astype(np.float32); X=np.fft.rfft(x); f=np.fft.rfftfreq(n,1/FS)
    low=1-np.exp(-(f/max(lo,1))**2); high=np.exp(-(f/max(hi,1))**2)
    y=np.fft.irfft(X*low*high,n=n).astype(np.float32)
    return y/max(float(np.sqrt(np.mean(y*y))),1e-6)

def add_event(track,kind,start,duration,gain,pan=0.0,seed=0):
    rng=np.random.default_rng(seed+1872); n=round(duration*FS); t=np.arange(n,dtype=np.float32)/FS
    if kind=='wave':
        raw=shaped_noise(n,50,1800,rng); env=np.sin(np.pi*np.clip(t/duration,0,1))**1.4
        env*=.45+.55*np.sin(2*np.pi*.62*t)**2
    elif kind=='paper':
        raw=shaped_noise(n,850,7000,rng); env=np.minimum(1,t/.025)*np.exp(-t*3.6)*(.7+.3*np.sin(2*np.pi*9*t)**2)
    elif kind=='creak':
        f0=rng.uniform(78,110); f1=rng.uniform(155,235)
        phase=2*np.pi*(f0*t+(f1-f0)*t*t/(2*duration)); raw=np.sin(phase)+.28*np.sin(phase*2.04+.4)+.22*shaped_noise(n,180,1600,rng)
        env=np.sin(np.pi*np.clip(t/duration,0,1))**1.7
    elif kind=='rope':
        raw=np.sin(2*np.pi*(96*t+33*t*t))+.3*shaped_noise(n,500,3600,rng); env=np.sin(np.pi*np.clip(t/duration,0,1))**2
    elif kind=='impact':
        raw=np.sin(2*np.pi*(76*t-18*t*t))+0.24*np.sin(2*np.pi*145*t); raw[:max(1,int(FS*.018))]+=.5
        env=np.exp(-t*6.5)
    else: # low, filtered cinematic air movement
        raw=shaped_noise(n,35,900,rng); env=np.sin(np.pi*np.clip(t/duration,0,1))**1.8
    raw=raw*env; raw/=max(float(np.max(np.abs(raw))),1e-5)
    start=max(0,round(start*FS)); stop=min(len(track),start+n)
    if stop<=start:return
    e=raw[:stop-start]*gain; l=np.sqrt((1-pan)*.5); r=np.sqrt((1+pan)*.5)
    track[start:stop,0]+=e*l; track[start:stop,1]+=e*r

def make_sfx(duration,starts,short=False):
    n=round(duration*FS); rng=np.random.default_rng(971872); t=np.arange(n,dtype=np.float32)/FS
    sea=shaped_noise(n,45,1500,rng); swell=.48+.28*(.5+.5*np.sin(2*np.pi*t/8.2))
    wind=shaped_noise(n,24,520,rng); track=np.zeros((n,2),dtype=np.float32)
    # Quiet constant atmosphere: well beneath speech, with no exaggerated broadband roar.
    bed=sea*swell*.0105 + wind*(.008+.002*np.sin(2*np.pi*t/11))
    track[:,0]+=bed; track[:,1]+=np.roll(bed,int(FS*.019))*.96
    if short:
        events=[(.45,'impact',.70,.25,-.15),(2.2,'wave',2.5,.23,-.25),(8.8,'creak',1.45,.22,.28),
                (16.5,'paper',.9,.17,-.4),(24.7,'air',1.5,.21,.1),(33.5,'rope',1.4,.22,.35),(42.0,'wave',2.3,.22,-.1)]
    else:
        events=[]
        for i,s in enumerate(starts):
            if i==0: events += [(s+.55,'impact',.9,.25,-.15),(s+4.4,'wave',2.6,.22,-.25),(s+18,'rope',1.3,.19,.3)]
            elif i==1: events += [(s+2,'paper',1.0,.18,-.4),(s+23,'air',1.6,.16,.25)]
            elif i==2: events += [(s+3,'wave',2.4,.24,-.25),(s+16,'creak',1.5,.23,.3),(s+30,'impact',.65,.18,-.1)]
            elif i==3: events += [(s+5,'paper',1.1,.25,-.35),(s+20,'impact',.75,.24,.2)]
            elif i==4: events += [(s+1,'air',1.8,.24,-.2),(s+18,'wave',2.8,.22,.25),(s+31,'rope',1.4,.2,-.2)]
            else: events += [(s+2,'wave',2.6,.23,0),(s+17,'creak',1.5,.16,.2)]
    for i,(when,kind,dur,gain,pan) in enumerate(events): add_event(track,kind,when,dur,gain,pan,i*17)
    fade=min(round(FS*1.3),n//2); env=np.ones(n,dtype=np.float32); env[:fade]=np.linspace(0,1,fade); env[-fade:]=np.linspace(1,0,fade); track*=env[:,None]
    peak=float(np.max(np.abs(track))); track*=min(1,.40/max(peak,1e-6))
    return np.clip(track,-.95,.95)

def write_wav(path,x):
    import wave
    path.parent.mkdir(parents=True,exist_ok=True)
    pcm=(np.clip(x,-.999,.999)*32767).astype('<i2')
    with wave.open(str(path),'wb') as w:
        w.setnchannels(2);w.setsampwidth(2);w.setframerate(FS);w.writeframes(pcm.tobytes())

def text_sections():
    s=(ROOT/'content/episode-01-mary-celeste-v3.md').read_text(encoding='utf8')
    body=s.split('## التعليق الصوتي وخطة الصورة',1)[1].split('## نص الشورت',1)[0]
    return re.findall(r'\*\*التعليق:\*\*\n(.*?)(?=\n\n\*\*الصورة/الحركة)',body,re.S)

def split_caption(text,limit=42):
    sentences=[x.strip() for x in re.split(r'(?<=[.!؟؛])\s+',text.strip()) if x.strip()]
    chunks=[]
    for sent in sentences:
        words=sent.split(); cur=''
        for word in words:
            if not cur:cur=word
            elif len(cur)+1+len(word)<=limit:cur+=' '+word
            else:chunks.append(cur);cur=word
        if cur:chunks.append(cur)
    return chunks

def cues_for(sections,section_lengths,gap_samples=0):
    cue_data=[];cursor=0
    for idx,(text,length) in enumerate(zip(sections,section_lengths)):
        cues=split_caption(text)
        total_chars=sum(max(1,len(c)) for c in cues)
        start=round(cursor/FS*FPS); section_frames=round(length/FS*FPS)
        pos=start
        for j,c in enumerate(cues):
            span=max(1,round(section_frames*len(c)/total_chars))
            end=min(start+section_frames,pos+span)
            cue_data.append({'start':pos,'end':end,'text':c})
            pos=end
        if cue_data: cue_data[-1]['end']=start+section_frames
        cursor+=length
        if idx<len(sections)-1:cursor+=gap_samples
    return cue_data

def write_srt(cues,path):
    def stamp(fr):
        ms=round(fr/FPS*1000);h,ms=divmod(ms,3600000);m,ms=divmod(ms,60000);s,ms=divmod(ms,1000)
        return f'{h:02}:{m:02}:{s:02},{ms:03}'
    rows=[]
    for i,c in enumerate(cues,1):
        rows += [str(i),f"{stamp(c['start'])} --> {stamp(c['end'])}",c['text'],'']
    path.write_text('\n'.join(rows),encoding='utf8')

def mix(ff,voice,sfx,out):
    graph='[0:a]loudnorm=I=-16:TP=-2:LRA=7[voice];[1:a]volume=0.72[fx];[fx][voice]sidechaincompress=threshold=0.035:ratio=7:attack=14:release=420[duck];[voice][duck]amix=inputs=2:normalize=0,alimiter=limit=0.89,loudnorm=I=-16:TP=-1.5:LRA=7[out]'
    subprocess.run([ff,'-hide_banner','-loglevel','error','-y','-i',str(voice),'-i',str(sfx),'-filter_complex',graph,'-map','[out]','-ar','48000','-codec:a','libmp3lame','-b:a','192k',str(out)],check=True)

def main():
    ff=imageio_ffmpeg.get_ffmpeg_exe(); sections=text_sections(); clips=[decode_mono(AUDIO/n) for n in CLIPS]
    if len(sections)!=6:raise ValueError(f'Expected 6 narration blocks, got {len(sections)}')
    gap=round(GAP*FS); starts=[]; cursor=0; pieces=[]
    for i,x in enumerate(clips):
        starts.append(cursor/FS); pieces.append(x);cursor+=len(x)
        if i<len(clips)-1:pieces.append(np.zeros(gap,dtype=np.float32));cursor+=gap
    voice=np.concatenate(pieces); full=stereo_voice(voice)
    short=decode_mono(AUDIO/'v3-07-short.mp3'); short_stereo=stereo_voice(short)
    voice_wav=AUDIO/'v3-voiceover.wav';short_wav=AUDIO/'v3-short-voiceover.wav'
    write_wav(voice_wav,full);write_wav(short_wav,short_stereo)
    main_sfx=make_sfx(len(voice)/FS,starts,False); short_sfx=make_sfx(len(short)/FS,[],True)
    main_fx=AUDIO/'v3-sfx-main.wav';short_fx=AUDIO/'v3-sfx-short.wav';write_wav(main_fx,main_sfx);write_wav(short_fx,short_sfx)
    mix(ff,voice_wav,main_fx,AUDIO/'v3-final-mix.mp3');mix(ff,short_wav,short_fx,AUDIO/'v3-short-final-mix.mp3')
    voice_lengths=[len(x) for x in clips]
    section_lengths=[voice_lengths[i]+(gap if i<len(clips)-1 else 0) for i in range(6)]
    # Frame boundaries are rounded cumulatively to keep the render's final frame count exact.
    boundaries=[round(v/FS*FPS) for v in np.cumsum([0]+section_lengths)]
    section_frames=[boundaries[i+1]-boundaries[i] for i in range(6)]
    main_cues=cues_for(sections,voice_lengths,gap)
    short_text=re.split(r'## نص الشورت العمودي.*?\n\n', (ROOT/'content/episode-01-mary-celeste-v3.md').read_text(encoding='utf8'),maxsplit=1,flags=re.S)[-1].split('**عنوان مقترح:**',1)[0].strip()
    short_cues=cues_for([short_text],[len(short)])
    write_srt(main_cues,ROOT/'deliverables/episode-01-v3-subtitles-ar.srt');write_srt(short_cues,ROOT/'deliverables/episode-01-v3-short-subtitles-ar.srt')
    data={'fps':FPS,'mainDurationSeconds':len(voice)/FS,'shortDurationSeconds':len(short)/FS,'sectionFrames':section_frames,'shortSectionFrames':[round(len(short)*.30/FS*FPS),round(len(short)*.34/FS*FPS),round(len(short)*.24/FS*FPS),round(len(short)/FS*FPS)-round(len(short)*.88/FS*FPS)],'totalFrames':sum(section_frames),'shortTotalFrames':round(len(short)/FS*FPS),'cues':main_cues,'shortCues':short_cues,'voiceSectionStartsSeconds':starts,'voiceSectionLengthsSeconds':[len(x)/FS for x in clips]}
    (OUT/'timeline-v3.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')
    print(f"Main voice {data['mainDurationSeconds']:.2f}s; short {data['shortDurationSeconds']:.2f}s; frame counts {section_frames} / {data['shortSectionFrames']}")
    print('Wrote V3 audio mixes, cue files, and Remotion timeline.')
if __name__=='__main__':main()
