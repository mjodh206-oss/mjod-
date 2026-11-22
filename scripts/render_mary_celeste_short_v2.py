#!/usr/bin/env python3
"""Create a vertical 9:16 short from the v2 animated documentary footage."""
from pathlib import Path
import subprocess
import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageEnhance, ImageOps
import arabic_reshaper
from bidi.algorithm import get_display

ROOT=Path(__file__).resolve().parents[1]
W,H,FPS=720,1280,20
FONT_B='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_R='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
def rtl(s): return get_display(arabic_reshaper.reshape(s))

def parse_srt(path):
    cues=[]
    for block in path.read_text(encoding='utf-8').strip().split('\n\n'):
        lines=block.splitlines()
        if len(lines)<3 or '-->' not in lines[1]: continue
        def secs(v):
            hms,ms=v.strip().split(','); h,m,s=map(int,hms.split(':'))
            return h*3600+m*60+s+int(ms)/1000
        a,b=lines[1].split('-->'); cues.append((secs(a),secs(b),'\n'.join(lines[2:])))
    return cues

def draw_text_center(draw,text,y,font,fill,stroke=(0,0,0,200)):
    draw.text((W//2,y),rtl(text),font=font,anchor='mm',fill=fill,stroke_width=2,stroke_fill=stroke)

def render(output):
    ff=imageio_ffmpeg.get_ffmpeg_exe()
    video=ROOT/'Mary-Celeste-main.mp4'
    audio=ROOT/'audio/episode-01/v2-short-final-mix.mp3'
    cues=parse_srt(ROOT/'deliverables/episode-01-v2-short-subtitles-ar.srt')
    # Pick three moving passages that match the short's narration: ship, discovery deck, hypotheses at sea.
    graph='[0:v]trim=start=0:duration=14,setpts=PTS-STARTPTS[a];[0:v]trim=start=97.82:duration=14,setpts=PTS-STARTPTS[b];[0:v]trim=start=211.2:duration=14,setpts=PTS-STARTPTS[c];[a][b][c]concat=n=3:v=1:a=0,scale=960:540:flags=lanczos,fps=20,format=rgb24[v]'
    dec_cmd=[ff,'-hide_banner','-loglevel','error','-i',str(video),'-filter_complex',graph,'-map','[v]','-f','rawvideo','-pix_fmt','rgb24','-']
    dec=subprocess.Popen(dec_cmd,stdout=subprocess.PIPE)
    enc_cmd=[ff,'-hide_banner','-loglevel','error','-y','-f','rawvideo','-pixel_format','rgb24','-video_size','720x1280','-framerate',str(FPS),'-i','-','-i',str(audio),'-vf','format=yuv420p','-map','0:v:0','-map','1:a:0','-c:v','libx264','-preset','medium','-crf','21','-r',str(FPS),'-c:a','aac','-b:a','128k','-shortest','-movflags','+faststart','-metadata','title=Mary Celeste — animated vertical short | Wara al-Hikaya',str(output)]
    enc=subprocess.Popen(enc_cmd,stdin=subprocess.PIPE)
    frame_bytes=960*540*3
    font_title=ImageFont.truetype(FONT_B,31)
    font_sub=ImageFont.truetype(FONT_B,25)
    font_small=ImageFont.truetype(FONT_R,18)
    i=0
    try:
        while True:
            raw=dec.stdout.read(frame_bytes)
            if len(raw)<frame_bytes: break
            src=Image.frombytes('RGB',(960,540),raw)
            # Fill portrait canvas with the same moving shot, then layer a sharp cinematic panel.
            bg=ImageOps.fit(src,(W,H),method=Image.Resampling.LANCZOS,centering=(0.5,0.5))
            bg=ImageEnhance.Brightness(bg.filter(ImageFilter.GaussianBlur(22))).enhance(0.30).convert('RGBA')
            canvas=bg
            panel=src.resize((W,405),Image.Resampling.LANCZOS).convert('RGBA')
            # The horizontal source contains long-form captions; replace that band with a clean label.
            pd=ImageDraw.Draw(panel,'RGBA')
            pd.rectangle((0,269,W,332),fill=(5,11,20,235))
            pd.text((W//2,300),rtl('إعادة تمثيل بصري'),font=font_small,anchor='mm',fill=(230,205,158,245))
            # Border and shadow for the live-action-feeling motion window.
            y0=405
            shadow=Image.new('RGBA',(W,H),(0,0,0,0)); sd=ImageDraw.Draw(shadow)
            sd.rounded_rectangle((13,y0-7,W-13,y0+412),radius=16,fill=(0,0,0,185))
            canvas=Image.alpha_composite(canvas,shadow.filter(ImageFilter.GaussianBlur(12)))
            canvas.alpha_composite(panel,(0,y0))
            d=ImageDraw.Draw(canvas,'RGBA')
            d.rounded_rectangle((1,y0,W-1,y0+405),radius=8,outline=(214,176,104,235),width=3)
            # Brand and topical title.
            d.rounded_rectangle((W//2-174,94,W//2+174,142),radius=20,fill=(6,14,25,225),outline=(204,164,93,180),width=2)
            draw_text_center(d,'وراء الحكاية',118,font_small,(248,239,220,255))
            phase=i//280
            title=['السفينة التي عادت وحدها','أين اختفى ركّابها؟','فرضيات بلا جواب مؤكّد'][min(phase,2)]
            draw_text_center(d,title,230,font_title,(243,209,139,255))
            # Time-matched short captions, positioned below the animated picture window.
            now=i/FPS
            caption=next((text for a,b,text in cues if a<=now<b),'')
            if caption:
                lines=caption.splitlines(); box_h=32+len(lines)*34
                d.rounded_rectangle((30,885,W-30,885+box_h),radius=15,fill=(4,10,19,220),outline=(180,143,80,120),width=1)
                for j,line in enumerate(lines):
                    d.text((W//2,907+j*34),rtl(line),font=font_sub,anchor='mm',fill=(248,243,231,255),stroke_width=1,stroke_fill=(0,0,0,180))
            d.text((W//2,1194),rtl('القصة كاملة في الفيديو الرئيسي'),font=ImageFont.truetype(FONT_B,20),anchor='mm',fill=(237,220,184,255),stroke_width=2,stroke_fill=(0,0,0,190))
            enc.stdin.write(canvas.convert('RGB').tobytes()); i+=1
            if i%200==0: print(f'rendered short frames: {i}',flush=True)
    finally:
        if dec.stdout: dec.stdout.close()
        if enc.stdin: enc.stdin.close()
    dec_code=dec.wait(); enc_code=enc.wait()
    if dec_code or enc_code: raise RuntimeError(f'ffmpeg decoder={dec_code}, encoder={enc_code}')
    print(f'Wrote {output} ({i} frames, {i/FPS:.2f}s)')

if __name__=='__main__':
    render(ROOT/'Mary-Celeste-short.mp4')
