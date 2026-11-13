package com.remoty.app;

import android.app.Activity;
import android.content.Context;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public final class MainActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff0b101a);
        getWindow().setNavigationBarColor(0xff0b101a);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return !url.startsWith("file:///android_asset/"); }
        });
        web.addJavascriptInterface(new IrBridge(), "RemotyIR");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("if(document.getElementById('modal-root').innerHTML){closeModal()}else if(page!=='home'){page='home';filter=null;render()}else{void 0}", null);
    }
    public final class IrBridge {
        @JavascriptInterface public boolean available() {
            ConsumerIrManager ir = (ConsumerIrManager)getSystemService(Context.CONSUMER_IR_SERVICE);
            return ir != null && ir.hasIrEmitter();
        }
        @JavascriptInterface public String send(String profile, String command) {
            ConsumerIrManager ir = (ConsumerIrManager)getSystemService(Context.CONSUMER_IR_SERVICE);
            if (ir == null || !ir.hasIrEmitter()) return "لا يوجد مرسل أشعة تحت الحمراء في هذا الهاتف.";
            long code; int header;
            if ("samsung".equals(profile)) {
                header = 4500;
                switch(command) {case "power": code=0xE0E040BFL; break; case "up": code=0xE0E0E01FL; break; case "down": code=0xE0E0D02FL; break; case "mute": code=0xE0E0F00FL; break; case "next": code=0xE0E048B7L; break; case "prev": code=0xE0E008F7L; break; default:return "الأمر غير مدعوم.";}
            } else if ("lg".equals(profile)) {
                header = 9000;
                switch(command) {case "power": code=0x20DF10EFL; break; case "up": code=0x20DF40BFL; break; case "down": code=0x20DFC03FL; break; case "mute": code=0x20DF906FL; break; case "next": code=0x20DF00FFL; break; case "prev": code=0x20DF807FL; break; default:return "الأمر غير مدعوم.";}
            } else return "ملف الإشارات غير مدعوم.";
            int[] pattern = new int[67]; pattern[0]=header; pattern[1]=4500;
            for(int i=0;i<32;i++){pattern[2+2*i]=560; pattern[3+2*i]=((code >>> (31-i)) & 1)==1 ? 1690 : 560;}
            pattern[66]=560;
            try { ir.transmit(38000,pattern); return ""; }
            catch (RuntimeException ex) { return "تعذر إرسال الإشارة؛ قد لا يدعم الهاتف تردد 38 كيلوهرتز."; }
        }
    }
}
