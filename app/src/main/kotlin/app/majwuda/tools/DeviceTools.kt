package app.majwuda.tools

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import app.majwuda.claude.Schema
import app.majwuda.claude.ToolDefinition
import app.majwuda.core.bool
import app.majwuda.core.int
import app.majwuda.core.str
import app.majwuda.core.strList

/**
 * Device control: volume, brightness & flashlight, battery, Do-Not-Disturb, airplane mode,
 * opening system settings, alarms, timers, music and wallpaper-ish actions.
 */
object DeviceTools {

    fun register(ctx: ToolContext): List<ToolSpec> = listOf(
        setVolume(ctx),
        adjustVolume(ctx),
        setBrightness(ctx),
        setFlashlight(ctx),
        getBattery(ctx),
        batterySaver(ctx),
        setDoNotDisturb(ctx),
        toggleAirplaneMode(ctx),
        openSettings(ctx),
        setAlarm(ctx),
        setTimer(ctx),
        cancelTimer(ctx),
        playMusic(ctx),
        mediaControl(ctx),
        toggleWifi(ctx),
        toggleBluetooth(ctx),
        toggleLocation(ctx),
    )

    // ------------------------------------------------------------------ volume

    private fun setVolume(ctx: ToolContext) = ToolSpec(
        id = "set_volume",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "set_volume",
            description = "اضبط مستوى الصوت (الوسائط أو النغمة أو المكالمة أو المنبه) لقيمة من 0 إلى 100.",
            schema = Schema.object_(
                "stream" to Schema.string("نوع الصوت", enumValues = listOf("media", "ring", "call", "alarm", "notification", "system"), default = "media"),
                "level" to Schema.integer("المستوى من 0 إلى 100", minimum = 0, maximum = 100),
                required = listOf("level"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val level = args.int("level")?.coerceIn(0, 100) ?: return@ToolHandler ToolOutput.fail("المستوى غير مفهوم.")
            val stream = when (args.str("stream")?.lowercase()) {
                "ring" -> android.media.AudioManager.STREAM_RING
                "call" -> android.media.AudioManager.STREAM_VOICE_CALL
                "alarm" -> android.media.AudioManager.STREAM_ALARM
                "notification" -> android.media.AudioManager.STREAM_NOTIFICATION
                "system" -> android.media.AudioManager.STREAM_SYSTEM
                else -> android.media.AudioManager.STREAM_MUSIC
            }
            val audio = ctx.context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val max = audio.getStreamMaxVolume(stream)
            val value = (max * level / 100.0).toInt().coerceIn(0, max)
            audio.setStreamVolume(stream, value, android.media.AudioManager.FLAG_SHOW_UI)
            ToolOutput.ok("تم ضبط الصوت إلى $level%.", speak = "تم")
        },
    )

    private fun adjustVolume(ctx: ToolContext) = ToolSpec(
        id = "adjust_volume",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "adjust_volume",
            description = "ارفع أو اخفض مستوى صوت الوسائط بمقدار نسبي (مثل «ارفع الصوت» أو «اخفض الصوت كثيراً»).",
            schema = Schema.object_(
                "direction" to Schema.string("اتجاه التغيير", enumValues = listOf("up", "down", "mute", "unmute")),
                "amount" to Schema.string("حجم التغيير", enumValues = listOf("small", "normal", "large"), default = "normal"),
                required = listOf("direction"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val audio = ctx.context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val direction = args.str("direction")?.lowercase() ?: "up"
            val amount = args.str("amount")?.lowercase() ?: "normal"
            val stream = android.media.AudioManager.STREAM_MUSIC
            val step = when (amount) {
                "small" -> 1
                "large" -> 5
                else -> 3
            }
            when (direction) {
                "mute" -> audio.setStreamVolume(stream, 0, android.media.AudioManager.FLAG_SHOW_UI)
                "unmute" -> {
                    val max = audio.getStreamMaxVolume(stream)
                    audio.setStreamVolume(stream, max / 2, android.media.AudioManager.FLAG_SHOW_UI)
                }
                "down" -> repeat(step) { audio.adjustStreamVolume(stream, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI) }
                else -> repeat(step) { audio.adjustStreamVolume(stream, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI) }
            }
            ToolOutput.ok("تم تعديل الصوت.", speak = "تم")
        },
    )

    // ------------------------------------------------------------------ brightness & flashlight

    private fun setBrightness(ctx: ToolContext) = ToolSpec(
        id = "set_brightness",
        gates = setOf(ToolGate.WRITE_SETTINGS),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "set_brightness",
            description = "غيّر سطوع الشاشة. يحتاج إذن «الكتابة على إعدادات النظام»؛ إن لم يكن ممنوحًا سيفتح التطبيق صفحة منح الإذن.",
            schema = Schema.object_(
                "level" to Schema.integer("قيمة السطوع من 0 (مظلمة) إلى 100 (ساطعة)", minimum = 0, maximum = 100),
                "auto" to Schema.boolean("إعادة السطوع التلقائي"),
                required = listOf("level"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val auto = args.bool("auto") ?: false
            try {
                if (auto) {
                    Settings.System.putInt(ctx.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                    return@ToolHandler ToolOutput.ok("أعيد تشغيل السطوع التلقائي.", speak = "تم")
                }
                Settings.System.putInt(ctx.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                val level = ((args.int("level") ?: 50).coerceIn(0, 100) * 255.0 / 100.0).toInt()
                Settings.System.putInt(ctx.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
                ToolOutput.ok("تم ضبط السطوع.", speak = "تم")
            } catch (e: SecurityException) {
                openWriteSettings(ctx)
            } catch (e: Exception) {
                ToolOutput.fail("تعذر تغيير السطوع: ${e.message}")
            }
        },
    )

    private fun openWriteSettings(ctx: ToolContext): ToolOutput {
        val appContext = ctx.context.applicationContext
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:${appContext.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            ToolOutput.ok("فتحت صفحة منح إذن التحكم بالسطوع — فعّله ثم اطلب مني مرة أخرى.", speak = "فتحت صفحة إذن التحكم بالسطوع")
        } catch (e: Exception) {
            ToolOutput.fail("تعذر فتح صفحة الإذن: ${e.message}")
        }
    }

    private fun setFlashlight(ctx: ToolContext) = ToolSpec(
        id = "set_flashlight",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "set_flashlight",
            description = "شغّل أو أطفئ فلاش الكاميرا (الكشاف).",
            schema = Schema.object_(
                "on" to Schema.boolean("true للتشغيل و false للإطفاء"),
                required = listOf("on"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val on = args.bool("on") ?: true
            val appContext = ctx.context.applicationContext
            val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                ?: return@ToolHandler ToolOutput.fail("هذا الجهاز لا يدعم التحكم بالفلاش.")
            return@ToolHandler try {
                val cameraId = manager.cameraIdList.firstOrNull { id ->
                    val chars = manager.getCameraCharacteristics(id)
                    chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
                } ?: manager.cameraIdList.firstOrNull()
                    ?: return@ToolHandler ToolOutput.fail("لا توجد كاميرا بفلاش متاحة.")
                manager.setTorchMode(cameraId, on)
                ToolOutput.ok(if (on) "أضأت الكشاف." else "أطفأت الكشاف.", speak = "تم")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر التحكم بالكشاف: ${e.message}")
            }
        },
    )

    // ------------------------------------------------------------------ battery

    private fun getBattery(ctx: ToolContext) = ToolSpec(
        id = "get_battery_status",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "get_battery_status",
            description = "اعرض نسبة شحن البطارية وحالة الشحن (متصل بالشاحن أم لا).",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            val appContext = ctx.context.applicationContext
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = try {
                appContext.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { sticky ->
                    val s = sticky.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    val plugged = sticky.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
                    val charging = s == android.os.BatteryManager.BATTERY_STATUS_CHARGING || s == android.os.BatteryManager.BATTERY_STATUS_FULL
                    val source = when (plugged) {
                        android.os.BatteryManager.BATTERY_PLUGGED_AC -> "شاحن كهربائي"
                        android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                        android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "شاحن لاسلكي"
                        else -> "بدون شاحن"
                    }
                    Pair(charging, source)
                }
            } catch (_: Exception) {
                null
            }
            val msg = buildString {
                append("البطارية مشحونة ").append(level).append("%")
                if (status != null) {
                    if (status.first) append(" وتوصل الشحن عبر ").append(status.second)
                    else append(" ولا تتصل بالشاحن")
                }
                if (bm.isCharging) append(" (الشحن نشط)")
            }
            ToolOutput.ok(msg, speak = "البطارية " + level + " بالمئة" + (status?.let { if (it.first) " والجهاز يشحن" else "" } ?: ""))
        },
    )

    private fun batterySaver(ctx: ToolContext) = ToolSpec(
        id = "open_battery_saver",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "open_battery_saver",
            description = "افتح صفحة «موفر البطارية» في إعدادات النظام.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ -> openSystemScreen(ctx, Settings.ACTION_BATTERY_SAVER_SETTINGS, "فتحت صفحة موفر البطارية") },
    )

    // ------------------------------------------------------------------ DND / airplane / connectivity

    private fun setDoNotDisturb(ctx: ToolContext) = ToolSpec(
        id = "set_do_not_disturb",
        gates = setOf(ToolGate.ALERT_POLICY),
        risk = ToolRisk.CONFIRM,
        confirmationPrompt = { args ->
            if (args.bool("on") == true) "هل أشغّل وضع «عدم الإزعاج» الآن؟" else "هل أوقف وضع «عدم الإزعاج»؟"
        },
        definition = ToolDefinition(
            name = "set_do_not_disturb",
            description = "شغّل أو أوقف وضع عدم الإزعاج كاملًا. يتطلب إذن «الوصول إلى عدم الإزعاج»؛ إن لم يكن ممنوحًا فُتحت صفحة منحه.",
            schema = Schema.object_(
                "on" to Schema.boolean("true تشغيل و false إيقاف"),
                required = listOf("on"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val on = args.bool("on") ?: true
            val nm = ctx.context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                return@ToolHandler openSystemScreen(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, "فتحت صفحة منح إذن عدم الإزعاج — فعّله ثم اطلب مني مرة أخرى")
            }
            try {
                nm.setInterruptionFilter(if (on) android.app.NotificationManager.INTERRUPTION_FILTER_NONE else android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                ToolOutput.ok(if (on) "فعّلت عدم الإزعاج." else "أوقفت عدم الإزعاج.", speak = "تم")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر تغيير عدم الإزعاج: ${e.message}")
            }
        },
    )

    private fun toggleAirplaneMode(ctx: ToolContext) = ToolSpec(
        id = "toggle_airplane_mode",
        gates = setOf(ToolGate.WRITE_SETTINGS),
        risk = ToolRisk.CONFIRM,
        confirmationPrompt = { args ->
            if (args.bool("on") == true) "هل أشغّل وضع الطيران؟ (ستنقطع الاتصالات)" else "هل أوقف وضع الطيران؟"
        },
        definition = ToolDefinition(
            name = "toggle_airplane_mode",
            description = "شغّل أو أوقف وضع الطيران. يتطلب إذن «الكتابة على إعدادات النظام».",
            schema = Schema.object_(
                "on" to Schema.boolean("true تشغيل و false إيقاف"),
                required = listOf("on"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val on = args.bool("on") ?: true
            try {
                Settings.Global.putInt(ctx.context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (on) 1 else 0)
                // Notify the system so the radio actually toggles (needs WRITE_SECURE_SETTINGS on some builds; harmless if it fails).
                val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                intent.putExtra("state", on)
                ctx.context.sendBroadcast(intent)
                ToolOutput.ok(if (on) "فعّلت وضع الطيران (إن لم تستجب الشبكة تلقائيًا فغيّره يدويًا من الإعدادات)." else "أوقفت وضع الطيران.", speak = "تم")
            } catch (e: SecurityException) {
                openWriteSettings(ctx)
            } catch (e: Exception) {
                openSystemScreen(ctx, Settings.ACTION_AIRPLANE_MODE_SETTINGS, "فتحت صفحة وضع الطيران — لأن النظام يقيّد التحكم التلقائي به")
            }
        },
    )

    private fun toggleWifi(ctx: ToolContext) = ToolSpec(
        id = "toggle_wifi",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "toggle_wifi",
            description = "افتح صفحة إعدادات Wi-Fi (النظام لا يسمح للتطبيقات بتشغيل/إطفاء Wi-Fi مباشرة).",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ -> openSystemScreen(ctx, Settings.ACTION_WIFI_SETTINGS, "فتحت إعدادات Wi-Fi") },
    )

    private fun toggleBluetooth(ctx: ToolContext) = ToolSpec(
        id = "toggle_bluetooth",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "toggle_bluetooth",
            description = "افتح صفحة إعدادات البلوتوث.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ -> openSystemScreen(ctx, Settings.ACTION_BLUETOOTH_SETTINGS, "فتحت إعدادات البلوتوث") },
    )

    private fun toggleLocation(ctx: ToolContext) = ToolSpec(
        id = "toggle_location",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "toggle_location",
            description = "افتح صفحة إعدادات الموقع الجغرافي.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ -> openSystemScreen(ctx, Settings.ACTION_LOCATION_SOURCE_SETTINGS, "فتحت إعدادات الموقع") },
    )

    /** Open any settings screen by intent action name. */
    private fun openSettings(ctx: ToolContext) = ToolSpec(
        id = "open_settings",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "open_settings",
            description = "افتح شاشة محددة من إعدادات النظام (مثلاً: الواي فاي، البلوتوث، التطبيقات، البطارية، الإشعارات، الشاشة). القائمة الكاملة في الوسائط المسموحة.",
            schema = Schema.object_(
                "screen" to Schema.string(
                    "الشاشة المطلوبة",
                    enumValues = listOf(
                        "wifi", "bluetooth", "location", "battery", "apps", "app_details", "notifications",
                        "display", "sound", "storage", "security", "accessibility", "about", "date",
                        "language", "accounts", "privacy", "data_usage", "hotspot", "nfc", "main",
                    ),
                ),
                "packageName" to Schema.string("اسم الحزمة عند اختيار app_details"),
                required = listOf("screen"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val screen = args.str("screen")?.lowercase() ?: "main"
            val appContext = ctx.context.applicationContext
            val action = when (screen) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "apps" -> Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS
                "app_details" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                "notifications" -> if (Build.VERSION.SDK_INT >= 26) Settings.ACTION_APP_NOTIFICATION_SETTINGS else Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound" -> Settings.ACTION_SOUND_SETTINGS
                "storage" -> if (Build.VERSION.SDK_INT >= 26) Settings.ACTION_INTERNAL_STORAGE_SETTINGS else Settings.ACTION_MEMORY_CARD_SETTINGS
                "security" -> Settings.ACTION_SECURITY_SETTINGS
                "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                "about" -> Settings.ACTION_DEVICE_INFO_SETTINGS
                "date" -> Settings.ACTION_DATE_SETTINGS
                "language" -> Settings.ACTION_LOCALE_SETTINGS
                "accounts" -> Settings.ACTION_SYNC_SETTINGS
                "privacy" -> Settings.ACTION_PRIVACY_SETTINGS
                "data_usage" -> Settings.ACTION_DATA_USAGE_SETTINGS
                "hotspot" -> if (Build.VERSION.SDK_INT >= 26) Settings.ACTION_WIRELESS_SETTINGS else Settings.ACTION_WIFI_SETTINGS
                "nfc" -> "android.settings.NFC_SETTINGS"
                else -> Settings.ACTION_SETTINGS
            }
            return@ToolHandler try {
                val intent = Intent(action)
                if (screen == "app_details") {
                    val pkg = args.str("packageName") ?: appContext.packageName
                    intent.data = Uri.parse("package:$pkg")
                }
                if (screen == "notifications" && Build.VERSION.SDK_INT >= 26) {
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
                ToolOutput.ok("فتحت الإعدادات.", speak = "تم")
            } catch (e: Exception) {
                openSystemScreen(ctx, Settings.ACTION_SETTINGS, "فتحت الإعدادات (الصفحة المطلوبة غير متاحة في هذا الجهاز)")
            }
        },
    )

    // ------------------------------------------------------------------ alarms & timers

    private fun setAlarm(ctx: ToolContext) = ToolSpec(
        id = "set_alarm",
        gates = emptySet(),
        risk = ToolRisk.CONFIRM,
        confirmationPrompt = { args ->
            val h = args.int("hour")
            val m = args.int("minute") ?: 0
            val label = args.str("label").orEmpty()
            "هل أضيف منبهًا على الساعة $h:${m.toString().padStart(2, '0')}${if (label.isNotBlank()) " باسم «$label»" else ""}؟"
        },
        definition = ToolDefinition(
            name = "set_alarm",
            description = "أنشئ منبهًا في تطبيق الساعة النظامية. الطلب دائم التأكيد لأنه سينطلق فعلًا.",
            schema = Schema.object_(
                "hour" to Schema.integer("الساعة بصيغة 24 (0-23)", minimum = 0, maximum = 23),
                "minute" to Schema.integer("الدقائق (0-59)", minimum = 0, maximum = 59, default = 0),
                "label" to Schema.string("تسمية المنبه"),
                "days" to Schema.array(Schema.string("يوم الأسبوع بصيغة أرقام الاثنين=1 ... الأحد=7"), "أيام التكرار الأسبوعي"),
                required = listOf("hour"),
            ),
            destructive = false,
        ),
        handler = ToolHandler { _, args ->
            val hour = args.int("hour") ?: return@ToolHandler ToolOutput.fail("الساعة غير واضحة.")
            val minute = args.int("minute") ?: 0
            val label = args.str("label").orEmpty()
            val appContext = ctx.context.applicationContext
            return@ToolHandler try {
                val alarmIntent = Intent("android.intent.action.SET_ALARM").apply {
                    putExtra("android.intent.extra.alarm.HOUR", hour)
                    putExtra("android.intent.extra.alarm.MINUTES", minute)
                    putExtra("android.intent.extra.alarm.MESSAGE", label)
                    args.strList("days").mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }?.let {
                        putExtra("android.intent.extra.alarm.DAYS", ArrayList(it))
                        putExtra("android.intent.extra.alarm.SKIP_UI", false)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(alarmIntent)
                ToolOutput.ok("فتحت تطبيق الساعة مع المنبه — راجعه واحفظه من هناك.", speak = "فتحت المنبه في تطبيق الساعة")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر ضبط المنبه: ${e.message}")
            }
        },
    )

    private fun setTimer(ctx: ToolContext) = ToolSpec(
        id = "set_timer",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "set_timer",
            description = "ابدأ مؤقتًا (عداد تنازلي) بعدد دقائق — مباشرة دون تأكيد.",
            schema = Schema.object_(
                "minutes" to Schema.integer("عدد الدقائق", minimum = 1, maximum = 1440),
                "label" to Schema.string("تسمية المؤقت"),
                required = listOf("minutes"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val minutes = args.int("minutes") ?: return@ToolHandler ToolOutput.fail("عدد الدقائق غير واضح.")
            val appContext = ctx.context.applicationContext
            val seconds = minutes * 60
            return@ToolHandler try {
                val intent = Intent("android.intent.action.SET_TIMER").apply {
                    putExtra("android.intent.extra.alarm.LENGTH", seconds)
                    putExtra("android.intent.extra.alarm.MESSAGE", args.str("label").orEmpty())
                    putExtra("android.intent.extra.alarm.SKIP_UI", false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                ToolOutput.ok("بدأت مؤقتًا لمدة $minutes دقيقة.", speak = "تم")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر بدء المؤقت: ${e.message}")
            }
        },
    )

    private fun cancelTimer(ctx: ToolContext) = ToolSpec(
        id = "cancel_timer",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "cancel_timer",
            description = "افتح تطبيق الساعة لصفحة المؤقتات لإيقاف واحد — النظام لا يسمح بإلغاء مؤقّت مباشرة.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            val appContext = ctx.context.applicationContext
            return@ToolHandler try {
                val intent = Intent("android.intent.action.SHOW_ALARMS")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
                ToolOutput.ok("فتحت صفحة التنبيهات والمؤقتات في تطبيق الساعة لإيقافه.", speak = "فتحت تطبيق الساعة")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر فتح تطبيق الساعة: ${e.message}")
            }
        },
    )

    // ------------------------------------------------------------------ music

    private fun playMusic(ctx: ToolContext) = ToolSpec(
        id = "play_music",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "play_music",
            description = "شغّل أغنية أو فنانًا أو قائمة تشغيل عن طريق «تشغيل من البحث الصوتي» — تفتح تطبيق الموسيقى وينفذ الطلب تلقائيًا في معظم التطبيقات (يوتيوب ميوزيك، سبوتيفاي...).",
            schema = Schema.object_(
                "query" to Schema.string("اسم الأغنية أو الفنان أو القائمة"),
                required = listOf("query"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val query = args.str("query").orEmpty()
            if (query.isBlank()) return@ToolHandler ToolOutput.fail("ما الذي تريد سماعه؟")
            val appContext = ctx.context.applicationContext
            return@ToolHandler try {
                val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                ToolOutput.ok("أشغّل «$query».", speak = "تشغيل $query")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر تشغيل الموسيقى: ${e.message}")
            }
        },
    )

    private fun mediaControl(ctx: ToolContext) = ToolSpec(
        id = "media_control",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "media_control",
            description = "أرسل أمر تحكم بتشغيل الوسائط الحالية عبر زرار الوسائط (إيقاف مؤقت/تشغيل، الأغنية التالية، السابقة). يعمل مع التطبيقات التي تدعم أزرار الوسائط.",
            schema = Schema.object_(
                "action" to Schema.string("الأمر", enumValues = listOf("play_pause", "next", "previous", "stop")),
                required = listOf("action"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val action = args.str("action")?.lowercase() ?: "play_pause"
            val keyCode = when (action) {
                "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                "previous" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                "stop" -> android.view.KeyEvent.KEYCODE_MEDIA_STOP
                else -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            val appContext = ctx.context.applicationContext
            return@ToolHandler try {
                val down = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
                }
                val up = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
                }
                appContext.sendOrderedBroadcast(down, null)
                appContext.sendOrderedBroadcast(up, null)
                ToolOutput.ok("أرسلت أمر التحكم بالوسائط.", speak = "تم")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر التحكم بالوسائط: ${e.message}")
            }
        },
    )

    // ------------------------------------------------------------------ shared

    private fun openSystemScreen(ctx: ToolContext, action: String, message: String): ToolOutput {
        val appContext = ctx.context.applicationContext
        return try {
            val intent = Intent(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            ToolOutput.ok(message, speak = "تم")
        } catch (e: Exception) {
            ToolOutput.fail("تعذر فتح الإعداد المطلوب: ${e.message}")
        }
    }
}
