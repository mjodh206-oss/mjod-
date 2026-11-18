package app.amjad.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.media.AudioDeviceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.media.audiofx.AudioEffect;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.SystemClock;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.OpeningHandshakeException;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class InterpreterService extends Service {
    interface Listener {void state(String state,boolean running);void quality(String quality);void level(double value);void prompt(String phrase);void command(String command);}
    static InterpreterService instance;
    static Listener listener;
    private static final String STOP="app.amjad.voice.STOP", CHANNEL="spoken_session";
    private static final int CAPTURE_RATE=16000, CHUNK=1280, MAX_SAVED=960_000;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final PcmOutput output=new PcmOutput(main,()->fatal("تعذّر تشغيل الصوت. جرّب تغيير مخرج الصوت."));
    private final ArrayDeque<byte[]> firstFrames=new ArrayDeque<>();
    private final ByteArrayOutputStream response=new ByteArrayOutputStream();
    private JSONObject opts=new JSONObject();
    private AudioManager audio;
    private AudioFocusRequest focus;
    private volatile long localPromptUntil;
    private int previousMode;
    private boolean previousSpeaker;
    private boolean running, push, released, opened, ready, outputAllowed, savedTooLong, routeApplied;
    private int attempt, recordingId, frameCounter;
    private volatile AudioRecord recorder;
    private WebSocket socket;
    private AtomicInteger sendQueue;
    private String phase="idle", network="weak", item;
    private byte[] lastAudio;
    private Runnable timeout;

    static String value(Context context,String name){
        Resources r=context.getResources();int id=r.getIdentifier(name,"string",context.getPackageName());return id==0?"":r.getString(id).trim();
    }
    static boolean endpointUsable(Context c){return RelayProfile.current(c)!=null;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){super.onCreate();instance=this;audio=getSystemService(AudioManager.class);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null||STOP.equals(intent.getAction())){requestStop();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        if(!endpointUsable(this)){fatal("لم يُهيّأ خادم الترجمة.");return START_NOT_STICKY;}
        try{
            Notification note=notification();
            if(Build.VERSION.SDK_INT>=29)startForeground(12,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);else startForeground(12,note);
            opts=parse(intent.getStringExtra("options"));tunePlayback();push=intent.getBooleanExtra("push",false)&&"push".equals(opts.optString("mode"));
            running=true;
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)requestStop();},main).build();
            if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){fatal("الصوت مشغول الآن. أوقف المكالمة أو التطبيق الآخر وحاول مجدداً.");return START_NOT_STICKY;}
            previousMode=audio.getMode();previousSpeaker=audio.isSpeakerphoneOn();routeApplied=true;applyRoute();
            setState("connecting");setQuality("weak");connect();
            if(push)beginCapture();
        }catch(RuntimeException e){fatal("تعذّر بدء جلسة الميكروفون.");}
        return START_NOT_STICKY;
    }
    private Notification notification(){
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"جلسة الترجمة الصوتية",NotificationManager.IMPORTANCE_LOW));
        int safe=PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open=PendingIntent.getActivity(this,12,new Intent(this,MainActivity.class),safe);
        PendingIntent stop=PendingIntent.getService(this,13,new Intent(this,InterpreterService.class).setAction(STOP),safe);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.status_icon).setContentTitle("المترجم الصوتي الفوري")
                .setContentText("جلسة صوتية نشطة").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_SECRET).addAction(new Notification.Action.Builder(null,"إيقاف",stop).build()).build();
    }
    void snapshot(Listener to){to.state(phase,running);to.quality(network);}
    private void setState(String next){phase=next;if(listener!=null)listener.state(next,running);}
    private void setQuality(String next){network=next;if(listener!=null)listener.quality(next);}
    private void level(double n){if(listener!=null)listener.level(n);}
    private void prompt(String text){if(listener!=null)listener.prompt(text);}
    private void buzz(){Vibrator v=getSystemService(Vibrator.class);if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(18,70));}
    private static JSONObject parse(String text){try{return new JSONObject(text);}catch(Exception e){return new JSONObject();}}
    private static JSONObject msg(String type,Object... pairs){JSONObject m=new JSONObject();try{m.put("type",type);for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);}catch(Exception ignored){}return m;}
    private void send(JSONObject event){if(socket!=null&&socket.isOpen())socket.sendText(event.toString());}
    private void connect(){
        if(!running)return;
        try{
            final AtomicInteger pending=new AtomicInteger();
            RelayProfile relay=RelayProfile.current(this);if(relay==null){fatal("انتهى التفعيل. افتح رابط تفعيل جديداً.");return;}
            final WebSocket ws=new WebSocketFactory().setConnectionTimeout(6000).setSocketTimeout(35_000).createSocket(relay.url);
            socket=ws;sendQueue=pending;opened=false;ready=false;
            ws.addHeader("Authorization","Bearer "+relay.token);
            ws.addListener(new WebSocketAdapter(){
                @Override public void onConnected(WebSocket w,Map<String,List<String>> headers){main.post(()->{
                    if(socket!=w||!running){w.disconnect();return;}opened=true;send(msg("start","options",opts,"push",push));
                    while(!firstFrames.isEmpty()&&socket==w&&opened)sendPcm(firstFrames.removeFirst());
                    if(released)send(msg("release"));
                });}
                @Override public void onTextMessage(WebSocket w,String text){if(text.length()<=4096) main.post(()->{if(socket==w)event(parse(text));});}
                @Override public void onBinaryMessage(WebSocket w,byte[] pcm){if(pcm.length<=500_000)main.post(()->{if(socket==w&&outputAllowed)play(pcm);});}
                @Override public void onFrameSent(WebSocket w,WebSocketFrame frame){if(frame.isBinaryFrame())pending.decrementAndGet();}
                @Override public void onConnectError(WebSocket w,WebSocketException e){main.post(()->{if(socket==w){if(e instanceof OpeningHandshakeException&&((OpeningHandshakeException)e).getStatusLine()!=null&&((OpeningHandshakeException)e).getStatusLine().getStatusCode()==401)fatal("انتهى تفعيل الترجمة أو لم يعد صالحاً. اطلب رابطاً جديداً.");else retry("network");}});}
                @Override public void onDisconnected(WebSocket w,WebSocketFrame server,WebSocketFrame client,boolean byServer){main.post(()->{if(socket==w)retry("network");});}
            });
            ws.connectAsynchronously();arm(11_000,()->retry("network"));
        }catch(Exception e){retry("network");}
    }
    private void arm(long ms,Runnable task){disarm();timeout=task;main.postDelayed(task,ms);}
    private void disarm(){if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}}
    private void event(JSONObject event){
        switch(event.optString("type")){
            case "ready": ready=true;disarm();setQuality("good");
                if(!released){setState("listening");if(!push)beginCapture();buzz();
                    if("eleven".equals(event.optString("engine"))&&"stream".equals(opts.optString("mode")))prompt("في الخطة المجانية، تبدأ الترجمة بعد وقفة قصيرة في الكلام.");}else arm(30_000,()->fatal("تأخرت الترجمة. حاول مرة ثانية."));break;
            case "quality": String q=event.optString("quality");if(q.equals("good")||q.equals("weak"))setQuality(q);break;
            case "interrupt": interrupt();break;
            case "listening":setState("listening");break;
            case "translating":setState("translating");break;
            case "speaking":interrupt();item=event.optString("item");outputAllowed=true;savedTooLong=false;response.reset();setState("speaking");if(push)arm(60_000,()->fatal("تعذّر إكمال النطق. حاول مرة ثانية."));break;
            case "done":
                if(!outputAllowed){if(push&& !"speaking".equals(phase))requestStop();break;}
                if(!event.optBoolean("complete")){interrupt();if(push)requestStop();break;}
                if(!savedTooLong&&response.size()>0)lastAudio=response.toByteArray();
                outputAllowed=false;output.finish(()->{item=null;response.reset();level(0);buzz();if(push)requestStop();else setState("listening");});break;
            case "clarify":
            case "empty":prompt("ما سمعتك بوضوح، ممكن تعيد؟");if(push)requestStop();else setState("listening");break;
            case "unsupported":prompt("اللغة المسموعة ليست إحدى اللغتين المختارتين.");if(push)requestStop();else setState("listening");break;
            case "problem":retry(event.optString("reason"));break;
            case "command":applyCommand(event.optString("action"));break;
        }
    }
    private void applyCommand(String action){
        if(action.equals("stop")){requestStop();return;}
        if(action.equals("repeat")){repeat();return;}
        if(action.equals("remember")){prompt("سأراعي نطق الاسم في هذه المحادثة.");return;}
        if(action.equals("swap")){
            String[] codes={"en","tr","fr","de","zh"};
            String[] names={"الإنجليزية","التركية","الفرنسية","الألمانية","الصينية الماندرين"};
            int next=(Arrays.asList(codes).indexOf(opts.optString("other"))+1)%codes.length;
            if(listener!=null)listener.command("language:"+codes[next]);
            JSONObject updated=parse(opts.toString());
            try{updated.put("other",codes[next]);}catch(Exception ignored){}
            configure(updated.toString());prompt(names[next]);return;
        }
        if(action.equals("slow")||action.equals("fast")){
            try{opts.put("speed",action);}catch(Exception ignored){}tunePlayback();if(listener!=null)listener.command(action);
            send(msg("configure","options",opts));
        }
    }
    private void play(byte[] pcm){
        if(!output.append(pcm)){fatal("تعذّر تشغيل الصوت.");return;}
        if(!savedTooLong){if(response.size()+pcm.length<=MAX_SAVED)response.write(pcm,0,pcm.length);else{savedTooLong=true;response.reset();}}
        if(frameCounter++%3==0)level(rms(pcm));
    }
    private void interrupt(){
        if(item!=null&&item.startsWith("item_"))send(msg("truncate","item",item,"ms",output.playedMillis()));
        item=null;outputAllowed=false;output.clear();response.reset();level(0);
    }
    void repeat(){
        if(!running||lastAudio==null){prompt("لا توجد ترجمة سابقة في الجلسة الحالية.");return;}
        interrupt();setState("speaking");
        if(!output.append(lastAudio)){fatal("تعذّر تشغيل الترجمة السابقة.");return;}
        output.finish(()->{level(0);if(push)requestStop();else setState("listening");});
    }
    void configure(String config){
        JSONObject next=parse(config);boolean routeChanged=!next.optString("route").equals(opts.optString("route"));
        boolean fresh=!next.optString("other").equals(opts.optString("other"))||!next.optString("voice").equals(opts.optString("voice"));
        opts=next;tunePlayback();
        if(routeChanged)applyRoute();
        if(!running)return;
        if(fresh&&!push){resetConnection();attempt=0;setState("connecting");setQuality("weak");connect();}
        else if(ready&&!fresh)send(msg("configure","options",opts));
    }
    private void tunePlayback(){
        String s=opts.optString("speed"),p=opts.optString("pitch");
        output.tune("slow".equals(s)?.86f:"fast".equals(s)?1.13f:1f,"low".equals(p)?.94f:"high".equals(p)?1.06f:1f);
    }
    private void applyRoute(){
        try{
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);boolean speaker=!"headphones".equals(opts.optString("route"));
            if(Build.VERSION.SDK_INT>=31){audio.clearCommunicationDevice();
                for(AudioDeviceInfo d:audio.getAvailableCommunicationDevices()){
                    int t=d.getType();boolean headset=t==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||t==AudioDeviceInfo.TYPE_BLE_HEADSET||t==AudioDeviceInfo.TYPE_WIRED_HEADSET||t==AudioDeviceInfo.TYPE_WIRED_HEADPHONES||t==AudioDeviceInfo.TYPE_USB_HEADSET;
                    if((speaker&&t==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER||!speaker&&headset)&&audio.setCommunicationDevice(d))break;
                }
            }else audio.setSpeakerphoneOn(speaker);
        }catch(RuntimeException ignored){}
    }
    private void beginCapture(){
        if(recorder!=null||!running||released)return;
        AudioRecord record=null;
        try{
            int min=AudioRecord.getMinBufferSize(CAPTURE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)throw new IllegalStateException();
            record=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,CAPTURE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4*CHUNK));
            if(record.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException();
            record.startRecording();if(record.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException();
            recorder=record;int generation=++recordingId;AudioRecord capture=record;
            new Thread(()->capture(capture,generation),"voice-pcm-capture").start();
        }catch(RuntimeException e){if(record!=null)record.release();recorder=null;fatal("تعذّر الوصول إلى الميكروفون. تأكد من الإذن وأعد المحاولة.");}
    }
    private void capture(AudioRecord capture,int generation){
        AudioEffect[] effects=new AudioEffect[3];
        try{
            try{int id=capture.getAudioSessionId();if(AcousticEchoCanceler.isAvailable())effects[0]=AcousticEchoCanceler.create(id);if(NoiseSuppressor.isAvailable())effects[1]=NoiseSuppressor.create(id);if(AutomaticGainControl.isAvailable())effects[2]=AutomaticGainControl.create(id);for(AudioEffect f:effects)if(f!=null)f.setEnabled(true);}catch(RuntimeException ignored){}
            byte[] buffer=new byte[CHUNK];
            while(recorder==capture){
                int n=capture.read(buffer,0,buffer.length,AudioRecord.READ_BLOCKING);
                if(n<0){main.post(()->{if(recordingId==generation)fatal("انقطع الميكروفون. حاول بدء الجلسة مجدداً.");});break;}
                if(n<2)continue;
                byte[] pcm=Arrays.copyOf(buffer,n&~1);
                if(SystemClock.elapsedRealtime()<localPromptUntil)Arrays.fill(pcm,(byte)0);
                double a=rms(pcm);
                main.post(()->{if(recordingId!=generation||!running||released)return;if(!"speaking".equals(phase)&&frameCounter++%2==0)level(a);sendPcm(pcm);});
            }
        }finally{for(AudioEffect f:effects)if(f!=null)f.release();capture.release();}
    }
    void localPrompt(boolean speaking){localPromptUntil=SystemClock.elapsedRealtime()+(speaking?18_000:160);}
    private static double rms(byte[] data){double sum=0;int samples=data.length/2;if(samples==0)return 0;for(int i=0;i<data.length-1;i+=2){double x=(short)((data[i]&255)|(data[i+1]<<8))/32768.0;sum+=x*x;}return Math.min(1,Math.sqrt(sum/samples)*5.5);}
    private void sendPcm(byte[] pcm){
        if(!opened||socket==null){if(push){if(firstFrames.size()>=45){fatal("الاتصال ضعيف. حاول مرة ثانية.");return;}firstFrames.addLast(pcm);}return;}
        if(sendQueue.incrementAndGet()>60){retry("network");return;}socket.sendBinary(pcm);
    }
    private void stopCapture(){AudioRecord previous=recorder;recorder=null;recordingId++;if(previous!=null)try{previous.stop();}catch(RuntimeException ignored){}}
    void releasePush(){
        if(!running||!push||released)return;released=true;stopCapture();level(0);setState("translating");if(opened)send(msg("release"));arm(32_000,()->fatal("لم تصل الترجمة. حاول مرة ثانية."));
    }
    private void retry(String reason){
        if(!running)return;
        if("ended".equals(reason)){prompt("انتهت الجلسة. المس الميكروفون لبدء جلسة جديدة.");requestStop();return;}
        if("quota".equals(reason)){fatal("تم بلوغ حد الاستخدام المجاني. جرّب لاحقاً بعد تجدد الرصيد.");return;}
        if("upstream".equals(reason)||"protocol".equals(reason)){fatal("خدمة الترجمة غير متاحة الآن. حاول لاحقاً.");return;}
        if(push||attempt>=5){fatal("الاتصال ضعيف، حاول مرة ثانية.");return;}
        resetConnection();setState("connecting");setQuality("weak");
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);NetworkCapabilities n=cm.getNetworkCapabilities(cm.getActiveNetwork());
        if(n==null||!n.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))setQuality("off");
        if(attempt==0)prompt("الاتصال ضعيف. نحاول إعادة الاتصال.");
        arm(500L<<attempt++,this::connect);
    }
    private void resetConnection(){
        stopCapture();disarm();WebSocket old=socket;socket=null;opened=ready=false;
        output.clear();item=null;outputAllowed=false;response.reset();firstFrames.clear();level(0);
        if(old!=null)old.disconnect();
    }
    private void fatal(String message){running=false;resetConnection();setQuality("off");setState("error");prompt(message);stopSelf();}
    void requestStop(){running=false;resetConnection();setState("idle");stopSelf();}
    @Override public void onDestroy(){
        running=false;resetConnection();lastAudio=null;
        if(routeApplied)try{if(Build.VERSION.SDK_INT>=31)audio.clearCommunicationDevice();else audio.setSpeakerphoneOn(previousSpeaker);audio.setMode(previousMode);}catch(RuntimeException ignored){}
        if(focus!=null)audio.abandonAudioFocusRequest(focus);
        stopForeground(STOP_FOREGROUND_REMOVE);
        if(instance==this)instance=null;super.onDestroy();
    }
}
