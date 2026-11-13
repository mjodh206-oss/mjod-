.class public Lcom/amjadhabib/smartremote/IrBridge;
.super Ljava/lang/Object;
.source "IrBridge.java"

.field private final appContext:Landroid/content/Context;
.field private final prefs:Landroid/content/SharedPreferences;

.method public constructor <init>(Landroid/content/Context;)V
    .registers 3

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    invoke-virtual {p1}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;
    move-result-object v0

    iput-object v0, p0, Lcom/amjadhabib/smartremote/IrBridge;->appContext:Landroid/content/Context;

    const-string v1, "smartir_prefs"
    const/4 v2, 0
    invoke-virtual {v0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
    move-result-object v1

    iput-object v1, p0, Lcom/amjadhabib/smartremote/IrBridge;->prefs:Landroid/content/SharedPreferences;

    return-void
.end method

# ══ هل الجهاز يحتوي على ناشر IR؟ ══
.method public hasIrEmitter()Z
    .registers 4

    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    iget-object v0, p0, Lcom/amjadhabib/smartremote/IrBridge;->appContext:Landroid/content/Context;
    invoke-virtual {v0}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;
    move-result-object v0

    const-string v1, "android.hardware.consumerir"
    invoke-virtual {v0, v1}, Landroid/content/pm/PackageManager;->hasSystemFeature(Ljava/lang/String;)Z
    move-result v0

    return v0
.end method

# ══ إرسال نمط IR خام (التردد + الفترات بالميكروثانية) ══
.method public transmitIr(I[I)Z
    .registers 6

    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .catch Ljava/lang/Throwable; {:try_start .. :try_end} :catch_all

    iget-object v0, p0, Lcom/amjadhabib/smartremote/IrBridge;->appContext:Landroid/content/Context;
    const-string v1, "consumer_ir"
    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0

    if-eqz v0, :fail

    check-cast v0, Landroid/hardware/ConsumerIrManager;

    invoke-virtual {v0}, Landroid/hardware/ConsumerIrManager;->hasIrEmitter()Z
    move-result v1

    if-eqz v1, :fail

    :try_start
    invoke-virtual {v0, p1, p2}, Landroid/hardware/ConsumerIrManager;->transmit(I[I)V
    :try_end

    const/4 v1, 1
    return v1

    :fail
    const/4 v1, 0
    return v1

    :catch_all
    move-exception v2
    const/4 v1, 0
    return v1
.end method

# ══ اهتزاز خفيف ══
.method public vibrate(I)V
    .registers 4

    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .catch Ljava/lang/Throwable; {:try_start .. :try_end} :catch_all

    iget-object v0, p0, Lcom/amjadhabib/smartremote/IrBridge;->appContext:Landroid/content/Context;
    const-string v1, "vibrator"
    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0

    if-eqz v0, :done

    check-cast v0, Landroid/os/Vibrator;

    :try_start
    int-to-long v1, p1
    invoke-virtual {v0, v1, v2}, Landroid/os/Vibrator;->vibrate(J)V
    :try_end

    :done
    return-void

    :catch_all
    move-exception v3
    return-void
.end method

# ══ قراءة مفتاح من التخزين ══
.method public prefsGet(Ljava/lang/String;)Ljava/lang/String;
    .registers 4

    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    iget-object v0, p0, Lcom/amjadhabib/smartremote/IrBridge;->prefs:Landroid/content/SharedPreferences;

    const-string v1, ""
    invoke-interface {v0, p1, v1}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0

    return-object v0
.end method

# ══ كتابة مفتاح في التخزين ══
.method public prefsSet(Ljava/lang/String;Ljava/lang/String;)V
    .registers 4

    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    iget-object v0, p0, Lcom/amjadhabib/smartremote/IrBridge;->prefs:Landroid/content/SharedPreferences;
    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;
    move-result-object v0

    invoke-interface {v0, p1, p2}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;
    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    return-void
.end method

# ══ حذف مفتاح ══
.method public prefsRemove(Ljava/lang/String;)V
    .registers 4

    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    iget-object v0, p0, Lcom/amjadhabib/smartremote/IrBridge;->prefs:Landroid/content/SharedPreferences;
    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;
    move-result-object v0

    invoke-interface {v0, p1}, Landroid/content/SharedPreferences$Editor;->remove(Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;
    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    return-void
.end method

# ══ نبضة صوتية قصيرة (0 = قصيرة، 1 = متوسطة، 2 = تأكيد) ══
.method public playBeep(I)V
    .registers 6

    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .catch Ljava/lang/Throwable; {:try_start .. :try_end} :catch_all

    :try_start
    new-instance v0, Landroid/media/ToneGenerator;
    const/16 v1, 3
    const/16 v2, 80
    invoke-direct {v0, v1, v2}, Landroid/media/ToneGenerator;-><init>(II)V

    const/16 v1, 24
    const/4 v4, 2
    if-ne p1, v4, :picked
    const/16 v1, 23
    :picked
    const/16 v2, 130
    if-nez p1, :dur_ok
    const/16 v2, 55
    :dur_ok
    invoke-virtual {v0, v1, v2}, Landroid/media/ToneGenerator;->startTone(II)Z
    move-result v1
    :try_end

    return-void

    :catch_all
    move-exception v3
    return-void
.end method

# ══ معلومات الجهاز للعرض في «عن التطبيق» ══
.method public deviceInfo()Ljava/lang/String;
    .registers 7

    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    sget-object v0, Landroid/os/Build;->MANUFACTURER:Ljava/lang/String;
    sget-object v1, Landroid/os/Build;->MODEL:Ljava/lang/String;
    sget-object v2, Landroid/os/Build$VERSION;->RELEASE:Ljava/lang/String;

    new-instance v3, Ljava/lang/StringBuilder;
    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    const-string v4, " "
    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    const-string v4, " · Android "
    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v3

    return-object v3
.end method
