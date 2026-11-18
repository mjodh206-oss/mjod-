package app.amjad.voice;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import java.util.ArrayDeque;

/** Main-thread playback pump. Writes are non-blocking and the queue is bounded. */
final class PcmOutput {
    private static final int RATE=24000, MAX_BACKLOG=RATE*2*24;
    private final Handler handler;
    private final Runnable failure;
    private final ArrayDeque<byte[]> queue=new ArrayDeque<>();
    private AudioTrack track;
    private int queued, offset, written;
    private boolean started;
    private Runnable completed;
    private final Runnable drain=this::pump;

    PcmOutput(Handler handler,Runnable failure){this.handler=handler;this.failure=failure;}
    boolean append(byte[] pcm){
        if(pcm.length==0||pcm.length%2!=0||queued+pcm.length>MAX_BACKLOG)return false;
        if(track==null){try{
            int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)return false;
            track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(Math.max(min,4800)).setTransferMode(AudioTrack.MODE_STREAM).build();
            if(track.getState()!=AudioTrack.STATE_INITIALIZED){clear();return false;}
        }catch(RuntimeException e){clear();return false;}}
        queue.addLast(pcm);queued+=pcm.length;
        schedule(0);return true;
    }
    int playedMillis(){if(track==null)return 0;return (int)((track.getPlaybackHeadPosition()&0xffffffffL)*1000/RATE);}
    void finish(Runnable done){completed=done;schedule(0);}
    private void schedule(long delay){handler.removeCallbacks(drain);handler.postDelayed(drain,delay);}
    private void pump(){
        try{
            if(track!=null){
                if(started||queued>=1920||completed!=null){
                    for(int i=0;i<8&&!queue.isEmpty();i++){
                        byte[] head=queue.peekFirst();int n=track.write(head,offset,head.length-offset,AudioTrack.WRITE_NON_BLOCKING);
                        if(n<0)throw new IllegalStateException();if(n==0)break;
                        written+=n;queued-=n;offset+=n;if(offset==head.length){offset=0;queue.removeFirst();}
                    }
                    if(!started&&written>0){track.play();started=true;}
                }
                if(!queue.isEmpty()||(completed!=null && (track.getPlaybackHeadPosition()&0xffffffffL)<written/2)){schedule(15);return;}
            }
            if(completed!=null){Runnable done=completed;clear();done.run();}
        }catch(RuntimeException e){clear();failure.run();}
    }
    void clear(){
        handler.removeCallbacks(drain);queue.clear();queued=offset=written=0;started=false;completed=null;
        if(track!=null){try{track.pause();track.flush();}catch(RuntimeException ignored){}track.release();track=null;}
    }
}
