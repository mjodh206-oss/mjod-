package app.amjad.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.media.AudioAttributes;
import android.os.SystemClock;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

public final class MainActivity extends Activity implements InterpreterService.Listener {
    private static final String APP_HOST="interpreter.invalid";
    private WebView view;
    private TextToSpeech tts;
    private boolean arabicVoice, ready, greeted, ttsInitializing=true;
    private RelayProfile pendingRelay;
    private String pairingChallenge;
    private int pairingVersion;
    private boolean invalidRelay;
    private String activePrompt;
    private String queuedPrompt;
    private static final int MIC_REQUEST=805;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(12,10,20));
        getWindow().setNavigationBarColor(Color.rgb(12,10,20));
        view=new WebView(this);
        view.setBackgroundColor(Color.rgb(12,10,20));
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if(Build.VERSION.SDK_INT>=35){
            view.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;
            });
        }
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        view.getSettings().setAllowFileAccess(false);
        view.getSettings().setAllowContentAccess(false);
        view.getSettings().setBuiltInZoomControls(false);
        view.getSettings().setTextZoom(100);
        view.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){
                Uri u=r.getUrl();String p=u.getPath();
                if(!"https".equals(u.getScheme())||!APP_HOST.equals(u.getHost())||p==null||!p.matches("/(?:flags/)?[a-z.]+")||p.contains(".."))return denied();
                String type=p.endsWith(".html")?"text/html":p.endsWith(".js")?"text/javascript":p.endsWith(".css")?"text/css":p.endsWith(".svg")?"image/svg+xml":p.endsWith(".jpg")?"image/jpeg":null;
                if(type==null)return denied();
                try{return new WebResourceResponse(type,p.endsWith(".jpg")?null:"UTF-8",getAssets().open(p.substring(1)));}catch(IOException error){return denied();}
            }
        });
        view.addJavascriptInterface(new Bridge(),"VoiceApp");
        setContentView(view);
        readIntent(getIntent());
        view.loadUrl("https://"+APP_HOST+"/index.html");
        tts=new TextToSpeech(this,status->runOnUiThread(()->{
            if(tts==null||isDestroyed())return;
            ttsInitializing=false;
            if(status==TextToSpeech.SUCCESS){
                int result=tts.setLanguage(new Locale("ar"));arabicVoice=result>=TextToSpeech.LANG_AVAILABLE;
                Voice best=null;
                if(tts.getVoices()!=null)for(Voice voice:tts.getVoices()){
                    if(!"ar".equals(voice.getLocale().getLanguage())||voice.isNetworkConnectionRequired()||(voice.getFeatures()!=null&&voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)))continue;
                    if(best==null||voice.getQuality()>best.getQuality())best=voice;
                }
                if(best!=null && tts.setVoice(best)==TextToSpeech.SUCCESS)arabicVoice=true;
                tts.setSpeechRate(.92f);
                tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    @Override public void onStart(String id){}
                    @Override public void onDone(String id){runOnUiThread(()->endPrompt(id));}
                    @Override public void onStop(String id,boolean interrupted){runOnUiThread(()->endPrompt(id));}
                    @Override public void onError(String id){runOnUiThread(()->endPrompt(id));}
                });
            }
            if(queuedPrompt!=null){String p=queuedPrompt;queuedPrompt=null;prompt(p);}
        }));
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);readIntent(intent);if(ready)offerRelay();}
    private void readIntent(Intent intent){
        if(intent==null||!Intent.ACTION_VIEW.equals(intent.getAction()))return;
        pendingRelay=RelayProfile.fromLink(intent.getData());invalidRelay=pendingRelay==null;pairingVersion++;
        pairingChallenge=pendingRelay==null?null:java.util.UUID.randomUUID().toString();
        // Do not retain a bearer grant in the activity's saved intent.
        setIntent(new Intent(this,MainActivity.class));
    }
    private void offerRelay(){
        if(pendingRelay!=null)js("pair("+JSONObject.quote(pairingChallenge)+","+JSONObject.quote(pendingRelay.host())+")");
        else if(invalidRelay){invalidRelay=false;prompt("رابط التفعيل غير صالح أو انتهت مدته. اطلب رابطاً جديداً.");}
    }
    private WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",new HashMap<>(),new ByteArrayInputStream(new byte[0]));}
    @Override protected void onStart(){super.onStart();InterpreterService.listener=this;refresh();}
    @Override protected void onStop(){if(InterpreterService.listener==this)InterpreterService.listener=null;if(tts!=null)tts.stop();endPrompt(activePrompt);super.onStop();}
    @Override protected void onDestroy(){if(tts!=null){tts.shutdown();tts=null;}if(view!=null){view.removeJavascriptInterface("VoiceApp");view.destroy();view=null;}super.onDestroy();}
    @Override public void onBackPressed(){if(!ready||view==null){super.onBackPressed();return;}view.evaluateJavascript("window.studio.back()",answer->{if(!"true".equals(answer))super.onBackPressed();});}
    private void js(String command){if(ready&&view!=null)view.evaluateJavascript("window.studio."+command,null);}
    private void refresh(){
        InterpreterService current=InterpreterService.instance;
        if(current!=null)current.snapshot(this);
        else {state("idle",false);quality(InterpreterService.endpointUsable(this)?"weak":"off");}
    }
    @Override public void state(String state,boolean running){js("state("+JSONObject.quote(state)+","+running+")");}
    @Override public void level(double level){js("level("+level+")");}
    @Override public void quality(String quality){js("quality("+JSONObject.quote(quality)+")");}
    @Override public void command(String command){js("command("+JSONObject.quote(command)+")");}
    @Override public void prompt(String phrase){
        // The webpage also exposes these messages to TalkBack, without painting them.
        if(tts!=null&&arabicVoice){
            activePrompt="voice-"+SystemClock.elapsedRealtimeNanos();
            InterpreterService service=InterpreterService.instance;if(service!=null)service.localPrompt(true);
            if(tts.speak(phrase,TextToSpeech.QUEUE_FLUSH,null,activePrompt)!=TextToSpeech.SUCCESS)endPrompt(activePrompt);
        }
        else if(ttsInitializing)queuedPrompt=phrase;
    }
    private void endPrompt(String id){
        if(id==null||!id.equals(activePrompt))return;activePrompt=null;
        InterpreterService service=InterpreterService.instance;if(service!=null)service.localPrompt(false);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==MIC_REQUEST){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)prompt("تم السماح بالميكروفون. المس زر الميكروفون للبدء.");else prompt("لم يُمنح إذن الميكروفون. يمكنك السماح به من إعدادات الجهاز.");}
    }
    private final class Bridge {
        @JavascriptInterface public boolean hasMic(){return checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
        @JavascriptInterface public void ready(){runOnUiThread(()->{ready=true;refresh();if(!greeted){if(pendingRelay==null&&!invalidRelay)prompt("أهلاً بك في المترجم الصوتي الفوري");greeted=true;}offerRelay();});}
        @JavascriptInterface public void pair(String challenge){runOnUiThread(()->{
            if(challenge==null||!challenge.equals(pairingChallenge)||pendingRelay==null)return;
            RelayProfile profile=pendingRelay;pendingRelay=null;pairingChallenge=null;final int version=pairingVersion;
            prompt("جارٍ التحقق من رابط تفعيل الترجمة.");
            new Thread(()->{boolean verified=profile.check();runOnUiThread(()->{
                if(isDestroyed()||version!=pairingVersion)return;
                if(verified&&profile.save(MainActivity.this)){InterpreterService current=InterpreterService.instance;if(current!=null)current.requestStop();refresh();quality("weak");prompt("تم ربط الخادم. المس زر الميكروفون لبدء الترجمة.");}
                else prompt("تعذّر التحقق من التفعيل. تأكد من الاتصال أو اطلب رابطاً جديداً.");
            });},"relay-activation").start();
        });}
        @JavascriptInterface public void unlink(){runOnUiThread(()->{pairingVersion++;pendingRelay=null;pairingChallenge=null;InterpreterService s=InterpreterService.instance;if(s!=null)s.requestStop();RelayProfile.clear(MainActivity.this);quality("off");prompt("تم إلغاء ربط خادم الترجمة.");});}
        @JavascriptInterface public void requestMic(){runOnUiThread(()->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_REQUEST));}
        @JavascriptInterface public void say(String text){if(text!=null&&text.length()<=260)runOnUiThread(()->prompt(text));}
        @JavascriptInterface public void start(String config,boolean push){if(config==null||config.length()>1024)return;runOnUiThread(()->{
            if(!hasMic()||isFinishing())return;
            if(tts!=null)tts.stop();endPrompt(activePrompt);
            if(!InterpreterService.endpointUsable(MainActivity.this)){state("error",false);quality("off");prompt("خادم الترجمة غير مرتبط. افتح رابط التفعيل الذي حصلت عليه من مشغّل الخدمة.");return;}
            Intent intent=new Intent(MainActivity.this,InterpreterService.class).putExtra("options",config).putExtra("push",push);
            try{startForegroundService(intent);}catch(RuntimeException exception){state("error",false);prompt("تعذّر بدء الاستماع. حاول مرة أخرى والتطبيق مفتوح.");}
        });}
        @JavascriptInterface public void stop(){runOnUiThread(()->{InterpreterService s=InterpreterService.instance;if(s!=null)s.requestStop();else stopService(new Intent(MainActivity.this,InterpreterService.class));});}
        @JavascriptInterface public void release(){runOnUiThread(()->{InterpreterService s=InterpreterService.instance;if(s!=null)s.releasePush();else stopService(new Intent(MainActivity.this,InterpreterService.class));});}
        @JavascriptInterface public void repeat(){runOnUiThread(()->{InterpreterService s=InterpreterService.instance;if(s!=null)s.repeat();else prompt("لا توجد ترجمة سابقة في الجلسة الحالية.");});}
        @JavascriptInterface public void configure(String options){if(options==null||options.length()>1024)return;runOnUiThread(()->{InterpreterService s=InterpreterService.instance;if(s!=null)s.configure(options);});}
    }
}
