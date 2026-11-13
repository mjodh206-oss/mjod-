.class public Lcom/amjadhabib/smartremote/MainActivity;
.super Landroid/app/Activity;
.source "MainActivity.java"

.field private webView:Landroid/webkit/WebView;

.method public constructor <init>()V
    .registers 1

    invoke-direct {p0}, Landroid/app/Activity;-><init>()V

    return-void
.end method

.method protected onCreate(Landroid/os/Bundle;)V
    .registers 14

    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    # إخفاء شريط العنوان قبل رسم أي محتوى
    const/16 v0, 1
    invoke-virtual {p0, v0}, Landroid/app/Activity;->requestWindowFeature(I)Z

    # FLAG_FULLSCREEN = 1024
    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
    move-result-object v1

    const/16 v2, 1024
    invoke-virtual {v1, v2, v2}, Landroid/view/Window;->setFlags(II)V

    # وضع ملء الشاشة الغامر
    invoke-virtual {v1}, Landroid/view/Window;->getDecorView()Landroid/view/View;
    move-result-object v3
    const v4, 0x1706
    invoke-virtual {v3, v4}, Landroid/view/View;->setSystemUiVisibility(I)V

    # إنشاء الـ WebView برمجياً
    new-instance v5, Landroid/webkit/WebView;
    invoke-direct {v5, p0}, Landroid/webkit/WebView;-><init>(Landroid/content/Context;)V

    invoke-virtual {v5}, Landroid/webkit/WebView;->getSettings()Landroid/webkit/WebSettings;
    move-result-object v6

    const/4 v7, 1
    invoke-virtual {v6, v7}, Landroid/webkit/WebSettings;->setJavaScriptEnabled(Z)V
    invoke-virtual {v6, v7}, Landroid/webkit/WebSettings;->setDomStorageEnabled(Z)V
    invoke-virtual {v6, v7}, Landroid/webkit/WebSettings;->setDatabaseEnabled(Z)V
    invoke-virtual {v6, v7}, Landroid/webkit/WebSettings;->setAllowFileAccess(Z)V
    invoke-virtual {v6, v7}, Landroid/webkit/WebSettings;->setAllowContentAccess(Z)V
    invoke-virtual {v6, v7}, Landroid/webkit/WebSettings;->setMediaPlaybackRequiresUserGesture(Z)V

    const/16 v7, -1
    invoke-virtual {v6, v7}, Landroid/webkit/WebSettings;->setCacheMode(I)V

    const/16 v7, 100
    invoke-virtual {v6, v7}, Landroid/webkit/WebSettings;->setTextZoom(I)V

    const v7, 0xff04061a
    invoke-virtual {v5, v7}, Landroid/webkit/WebView;->setBackgroundColor(I)V

    # جسر الجافاسكربت
    new-instance v8, Lcom/amjadhabib/smartremote/IrBridge;
    invoke-direct {v8, p0}, Lcom/amjadhabib/smartremote/IrBridge;-><init>(Landroid/content/Context;)V
    const-string v9, "AndroidBridge"
    invoke-virtual {v5, v8, v9}, Landroid/webkit/WebView;->addJavascriptInterface(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v9, "file:///android_asset/www/index.html"
    invoke-virtual {v5, v9}, Landroid/webkit/WebView;->loadUrl(Ljava/lang/String;)V

    invoke-virtual {p0, v5}, Landroid/app/Activity;->setContentView(Landroid/view/View;)V

    iput-object v5, p0, Lcom/amjadhabib/smartremote/MainActivity;->webView:Landroid/webkit/WebView;

    return-void
.end method

.method protected onResume()V
    .registers 2

    invoke-super {p0}, Landroid/app/Activity;->onResume()V

    iget-object v0, p0, Lcom/amjadhabib/smartremote/MainActivity;->webView:Landroid/webkit/WebView;
    if-eqz v0, :done
    invoke-virtual {v0}, Landroid/webkit/WebView;->onResume()V
    :done
    return-void
.end method

.method protected onPause()V
    .registers 2

    invoke-super {p0}, Landroid/app/Activity;->onPause()V

    iget-object v0, p0, Lcom/amjadhabib/smartremote/MainActivity;->webView:Landroid/webkit/WebView;
    if-eqz v0, :done
    invoke-virtual {v0}, Landroid/webkit/WebView;->onPause()V
    :done
    return-void
.end method

.method public onBackPressed()V
    .registers 3

    iget-object v0, p0, Lcom/amjadhabib/smartremote/MainActivity;->webView:Landroid/webkit/WebView;
    if-eqz v0, :super

    invoke-virtual {v0}, Landroid/webkit/WebView;->canGoBack()Z
    move-result v1

    if-eqz v1, :super

    invoke-virtual {v0}, Landroid/webkit/WebView;->goBack()V
    return-void

    :super
    invoke-super {p0}, Landroid/app/Activity;->onBackPressed()V
    return-void
.end method

.method protected onDestroy()V
    .registers 2

    iget-object v0, p0, Lcom/amjadhabib/smartremote/MainActivity;->webView:Landroid/webkit/WebView;
    if-eqz v0, :super
    invoke-virtual {v0}, Landroid/webkit/WebView;->destroy()V
    :super
    invoke-super {p0}, Landroid/app/Activity;->onDestroy()V
    return-void
.end method
