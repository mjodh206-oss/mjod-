package com.sahratalkhamis.couples;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
 private WebView web; private ValueCallback<Uri[]> fileCallback;
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(0xff110814);getWindow().setNavigationBarColor(0xff110814);
  web=new WebView(this);setContentView(web);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMediaPlaybackRequiresUserGesture(true);
  WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  web.setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){return loader.shouldInterceptRequest(req.getUrl());}@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return !"appassets.androidplatform.net".equals(req.getUrl().getHost());}});
  web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> cb,FileChooserParams params){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;try{startActivityForResult(params.createIntent(),123);return true;}catch(Exception ex){fileCallback=null;return false;}}});
  web.addJavascriptInterface(new SahraBridge(this),"SahraAndroid"); web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==123 && fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}}
 @Override public void onBackPressed(){web.evaluateJavascript("if(!document.querySelector('#featureSheet').hidden){document.querySelector('#sheetCloseBtn').click()}else{document.querySelector('[data-nav-tab=home]').click()}",null);}
}
