package dev.lasq.fawri.ui

import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import dev.lasq.fawri.BuildConfig
import dev.lasq.fawri.core.*
import dev.lasq.fawri.platform.*

@Composable
fun Sheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun Note(g: G, title: String, body: String) {
    val p = Colors.current
    Row(
        Modifier.fillMaxWidth().background(p.mist, RoundedCornerShape(18.dp)).padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Glyph(g, Modifier.padding(top = 3.dp).size(19.dp), p.green)
        Column {
            Txt(title, 15, p.green, true)
            Txt(body, 13, p.green)
        }
    }
}

@Composable
fun Permissions(a: MainActivity, vm: AppModel, o: Options, nav: (Screen) -> Unit) {
    val p = Colors.current
    val tick = a.epoch
    val access = remember(tick) { Checks.access(a) }
    val notify = remember(tick, o.mode) { Checks.notifications(a) }
    val board = remember(tick) { Checks.keyboard(a) }
    val battery =
        remember(tick) { a.system<PowerManager>().isIgnoringBatteryOptimizations(a.packageName) }
    val on by vm.app.active.collectAsState()
    val reason = remember(tick, o, on) { Checks.reason(a) }
    Sheet {
        Title("إعداد الخدمة", "خطوات واضحة، ولا شيء يعمل دون إذنك.")
        Panel {
            Column(Modifier.padding(15.dp)) {
                Badge("الموافقة الصريحة", true)
                Spacer(Modifier.height(6.dp))
                Txt("ما الذي سنصل إليه؟", 18, bold = true)
                Txt(
                    "عند التشغيل نقرأ النص المنسوخ واسم التطبيق الظاهر فقط. تستخدم خدمة إمكانية الوصول خصائص الحقل النشط للتحقق من أنه مسموح وغير سري، ثم تنفّذ أمر اللصق. لا نقرأ النص المكتوب في الحقل ولا نسجّل الشاشة أو المفاتيح.",
                    14,
                    p.muted,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { vm.settings { it.copy(consent = !o.consent) } }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(o.consent, null)
                    Spacer(Modifier.width(9.dp))
                    Txt("أوافق على المراقبة واللصق المحليين عند التشغيل", 14, bold = true)
                }
            }
        }
        Panel {
            PermissionRow(
                G.HAND,
                "إمكانية الوصول",
                "افتح إعدادات النظام، واختر «لصق فوري · الحقل النشط». المخوّل فقط هو فحص خصائص الحقل وتنفيذ اللصق.",
                access,
            ) {
                a.external(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            RuleLine()
            PermissionRow(
                G.BELL,
                "إشعار الخدمة",
                "إشعار ظاهر للإيقاف، وإشعار منفصل لزر «الصق» في وضع التأكيد.",
                notify,
                a::notifyPermission,
            )
            if (Build.VERSION.SDK_INT >= 29) {
                RuleLine()
                PermissionRow(
                    G.BOARD,
                    "لوحة لصق فوري",
                    "من أندرويد ١٠ لا تقرأ خدمة الخلفية الحافظة وحدها. فعّل لوحة اللصق ثم اخترها كلوحة حالية. هي لوحة للصق وليست للكتابة؛ يمكنك العودة للوحة الكتابة في أي وقت.",
                    board,
                    a::chooseBoard,
                )
            }
        }
        Panel {
            LineItem(
                G.GRID,
                "اختيار التطبيقات",
                if (o.allowList) "المسموح بها: ${n(o.allowed.size)}. اختر المصدر والوجهة."
                else "يتم تجاهل التطبيقات المستبعدة.",
                { nav(Screen.APPS) },
            )
        }
        Panel {
            PermissionRow(
                G.BATTERY,
                "تحسين البطارية · اختياري",
                "اترك التحسين مفعّلاً أولاً. إذا أوقف جهازك الخدمة، راجع قيود الخلفية لهذا التطبيق في إعدادات البطارية لدى شاومي أو أوبو أو سامسونغ.",
                battery,
            ) {
                a.external(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        Note(
            G.INFO,
            "لا تريد تبديل لوحة المفاتيح؟",
            "استخدم «مشاركة» من أي تطبيق، أو «من الحافظة» من الرئيسية. الحفظ اليدوي لا يتطلب إمكانية الوصول ولا تشغيل المراقبة.",
        )
        if (reason != null) Txt(reason, 13, p.muted)
        Primary(
            if (on) "إيقاف الخدمة" else "تشغيل الخدمة",
            { if (on) Monitor.instance?.halt(true) else if (Monitor.start(a)) nav(Screen.HOME) },
            enabled = on || reason == null,
            icon = G.BOLT,
        )
    }
}

@Composable
private fun PermissionRow(g: G, title: String, details: String, enabled: Boolean, click: () -> Unit) {
    val p = Colors.current
    Column(Modifier.fillMaxWidth().padding(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileIcon(g, size = 33)
            Spacer(Modifier.width(9.dp))
            Txt(title, 15, bold = true, modifier = Modifier.weight(1f))
            Badge(if (enabled) "مفعّل" else "غير مفعّل", enabled)
        }
        Spacer(Modifier.height(6.dp))
        Txt(details, 13, p.muted)
        TextButton(click, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Txt(if (enabled) "مراجعة الإعداد" else "فتح الإعداد", 13, p.purple, true)
            Spacer(Modifier.width(5.dp))
            Glyph(G.LEFT, Modifier.size(14.dp))
        }
    }
}

@Composable
fun Preferences(a: MainActivity, vm: AppModel, o: Options, nav: (Screen) -> Unit) {
    val p = Colors.current
    var dialog by remember { mutableStateOf("") }
    val busy by vm.transferring.collectAsState()
    val delays = listOf(0L, 300L, 750L, 1500L)
    val delayText = listOf("دون تأخير", "٣٠٠ مللي ثانية", "٧٥٠ مللي ثانية", "ثانية ونصف")
    val days = listOf(1, 7, 30, 90)
    val dayText = listOf("يوم واحد", "٧ أيام", "٣٠ يوماً", "٩٠ يوماً")
    val clear = listOf(0, 30, 60, 300, 3600)
    val clearText = listOf("معطّل", "٣٠ ثانية", "دقيقة", "٥ دقائق", "ساعة")
    val locales = listOf("ar", "ar-EG", "ar-MA")
    val lang = listOf("العربية", "العربية · مصر", "العربية · المغرب")
    Sheet {
        Title("الإعدادات", "اضبط تجربة اللصق بما يناسبك.")
        Column {
            Section("سلوك اللصق")
            Spacer(Modifier.height(6.dp))
            Panel {
                LineItem(G.BOLT, "وضع اللصق", o.mode.label, { dialog = "mode" })
                RuleLine()
                LineItem(
                    G.CLOCK,
                    "تأخير اللصق",
                    delayText[delays.indexOf(o.delayMs).coerceAtLeast(0)],
                    { dialog = "delay" },
                )
                RuleLine()
                LineItem(
                    G.TEXT,
                    "أنواع المحتوى",
                    o.types.joinToString("، ") { it.label },
                    { dialog = "types" },
                )
                RuleLine()
                SwitchLine(
                    G.TUNE,
                    "تنظيف المسافات",
                    "اختصار المسافات المتتالية مع الحفاظ على الأسطر.",
                    o.trim,
                ) {
                    vm.settings { s -> s.copy(trim = it) }
                }
                RuleLine()
                SwitchLine(
                    G.LINK,
                    "تنظيف الروابط",
                    "إزالة معاملات التتبع الشائعة من الروابط المستقلة.",
                    o.tracking,
                ) {
                    vm.settings { s -> s.copy(tracking = it) }
                }
            }
        }
        Column {
            Section("الاحتفاظ والحماية")
            Spacer(Modifier.height(6.dp))
            Panel {
                LineItem(
                    G.HISTORY,
                    "مدة حفظ السجل",
                    dayText[days.indexOf(o.days).coerceAtLeast(0)] + " · يشمل المثبّت",
                    { dialog = "days" },
                )
                RuleLine()
                LineItem(
                    G.CLIP,
                    "مسح الحافظة بعد",
                    clearText[clear.indexOf(o.clearSeconds).coerceAtLeast(0)],
                    { dialog = "clear" },
                )
                RuleLine()
                SwitchLine(
                    G.LOCK,
                    "قفل التطبيق",
                    "بصمة قوية أو قفل الجهاز عند كل عودة للتطبيق.",
                    o.lock,
                ) { enabled ->
                    a.authenticate { vm.settings { it.copy(lock = enabled) } }
                }
                RuleLine()
                LineItem(
                    G.KEY,
                    "إدارة الأذونات",
                    "الموافقة، لوحة اللصق والبطارية",
                    { nav(Screen.PERMISSIONS) },
                )
            }
        }
        Column {
            Section("نقل السجل")
            Spacer(Modifier.height(6.dp))
            Panel {
                LineItem(
                    G.UPLOAD,
                    "تصدير ملف مشفّر",
                    "نسخة محمية بعبارة مرور تختارها.",
                    { if (!busy) a.pickFile(true) },
                )
                RuleLine()
                LineItem(
                    G.DOWNLOAD,
                    "استيراد نسخة",
                    "إضافة عناصر غير منتهية من ملف لصق فوري.",
                    { if (!busy) a.pickFile(false) },
                )
            }
            if (busy) Loading()
        }
        Column {
            Section("الواجهة")
            Spacer(Modifier.height(6.dp))
            Panel {
                LineItem(G.MOON, "المظهر", o.theme.label, { dialog = "theme" })
                RuleLine()
                LineItem(
                    G.LANG,
                    "اللغة وتنسيق الأرقام",
                    lang[locales.indexOf(o.locale).coerceAtLeast(0)],
                    { dialog = "lang" },
                )
                RuleLine()
                LineItem(G.INFO, "حول التطبيق", "النسخة، الخصوصية والتواصل", { nav(Screen.ABOUT) })
            }
        }
        Panel {
            LineItem(
                G.BIN,
                "حذف جميع البيانات",
                "مسح الخزنة والقوالب والمفتاح والإعدادات.",
                { dialog = "erase" },
            )
        }
        Txt(CREDIT, 13, p.muted, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
    }
    val close = { dialog = "" }
    when (dialog) {
        "mode" ->
            Pick("وضع اللصق", Mode.entries.map { it.label }, o.mode.ordinal, close) { i ->
                vm.settings { it.copy(mode = Mode.entries[i]) }
            }
        "delay" ->
            Pick("تأخير اللصق", delayText, delays.indexOf(o.delayMs), close) { i ->
                vm.settings { it.copy(delayMs = delays[i]) }
            }
        "days" ->
            Pick("حذف السجل تلقائياً بعد", dayText, days.indexOf(o.days), close) { i ->
                vm.settings { it.copy(days = days[i]) }
            }
        "clear" ->
            Pick(
                "مسح آخر حافظة معروفة عند إتاحة النظام",
                clearText,
                clear.indexOf(o.clearSeconds),
                close,
            ) { i ->
                vm.settings { it.copy(clearSeconds = clear[i]) }
            }
        "theme" ->
            Pick("المظهر", Theme.entries.map { it.label }, o.theme.ordinal, close) { i ->
                vm.settings { it.copy(theme = Theme.entries[i]) }
            }
        "lang" ->
            Pick("العربية وتنسيق المنطقة", lang, locales.indexOf(o.locale), close) { i ->
                vm.settings { it.copy(locale = locales[i]) }
            }
        "types" ->
            AlertDialog(
                onDismissRequest = close,
                title = { Txt("أنواع المحتوى", 21, bold = true) },
                text = {
                    Column {
                        Kind.entries.forEach { k ->
                            val checked = k in o.types
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        if (!checked || o.types.size > 1)
                                            vm.settings {
                                                it.copy(
                                                    types = if (checked) o.types - k else o.types + k,
                                                )
                                            }
                                    }
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked, null)
                                Spacer(Modifier.width(10.dp))
                                Txt(k.label)
                            }
                        }
                        Txt("يجب إبقاء نوع واحد على الأقل.", 12, p.muted)
                    }
                },
                confirmButton = { TextButton(close) { Txt("تم", color = p.purple) } },
            )
        "erase" ->
            Confirm(
                "حذف كل البيانات؟",
                "ستتوقف الخدمة ويُحذف السجل والقوالب ومفتاح التشفير والإعدادات نهائياً. تبقى الملفات التي صدّرتها خارج التطبيق.",
                "حذف نهائي",
                close,
                danger = true,
            ) {
                close()
                vm.erase { a.toast("حُذفت بيانات لصق فوري.") }
            }
    }
}

@Composable
private fun SwitchLine(g: G, title: String, detail: String, on: Boolean, change: (Boolean) -> Unit) {
    LineItem(g, title, detail, { change(!on) }, tail = { Switch(on, onCheckedChange = change) })
}

@Composable
fun About(a: MainActivity, nav: (Screen) -> Unit) {
    val p = Colors.current
    Sheet {
        Panel {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Logo(64)
                Spacer(Modifier.height(9.dp))
                Txt("لصق فوري", 27, bold = true)
                Txt("الإصدار ${BuildConfig.VERSION_NAME}", 13, p.muted)
                Spacer(Modifier.height(10.dp))
                Badge(CREDIT)
            }
        }
        Note(
            G.SHIELD,
            "خصوصيتك هي الأساس",
            "تطبيق عربي مستقل، لا إعلانات فيه ولا حسابات أو أدوات تتبّع.",
        )
        Panel {
            LineItem(
                G.SHIELD,
                "سياسة الخصوصية",
                "تعرف على ما نحفظه وما لا نصل إليه.",
                { nav(Screen.PRIVACY) },
            )
            RuleLine()
            LineItem(
                G.MAIL,
                "التواصل والإبلاغ عن مشكلة",
                "يفتح صفحة المشروع في متصفح خارجي دون إرسال سجلك.",
                {
                    a.external(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/mjodh206-oss/mjod-/issues"),
                        ),
                    )
                },
            )
        }
        Panel {
            LineItem(
                G.LOCK,
                "حالة سلامة النسخة",
                if (BuildConfig.DEBUG) "نسخة تطوير للاختبار" else "توقيع النسخة مطابق لبصمة البناء.",
                green = true,
            )
            RuleLine()
            LineItem(
                G.SHIELD,
                "فحص الجهاز",
                if (a.vm.app.integrity.second) "رصدنا مؤشرات روت. حماية الجهاز المعدّل قد تكون أضعف."
                else "لم تُرصد مؤشرات روت شائعة؛ هذا ليس ضماناً لسلامة النظام.",
            )
        }
        Txt(
            "الحقوق محفوظة لأمجد حبيب. خط IBM Plex Sans Arabic مرخّص بموجب SIL Open Font License.",
            12,
            p.muted,
            align = TextAlign.Center,
        )
    }
}

@Composable
fun Privacy() {
    Sheet {
        Title("سياسة الخصوصية", "بكلمات واضحة، دون بنود خفية.")
        Note(
            G.LOCK,
            "لا شيء يغادر جهازك تلقائياً",
            "لا نطلب إذن الإنترنت، ولا نستخدم خادماً أو إعلانات أو تحليلات.",
        )
        listOf(
                "ما يُحفظ" to
                    "النص أو الرابط المنسوخ، نوعه، وقت النسخ، اسم التطبيق الظاهر عند التغيير، والمجموعة والتثبيت. لا يتيح أندرويد دائماً معرفة المصدر الحقيقي. نسمي الإدخال اليدوي والمشاركة بوضوح.",
                "ما لا يُقرأ" to
                    "لا نقرأ نص الحقول أو ضغطات المفاتيح ولا نلتقط صوراً للشاشة. تُفحص خصائص الحقل النشط، ويُتجاهل المحتوى المعلّم حساساً والتطبيقات المحجوبة. لا توجد طريقة مضمونة لاكتشاف كل تطبيق مالي؛ القائمة المسموحة هي الخيار الافتراضي.",
                "كيف تُحمى البيانات" to
                    "السجل والقوالب داخل Room مشفّرة بالكامل بـ SQLCipher وAES-256. سرّ قاعدة البيانات عشوائي ومحمي بمفتاح غير قابل للتصدير في Android Keystore. النسخ الاحتياطي للنظام معطّل ولقطات واجهة التطبيق محظورة.",
                "متى يتم اللصق" to
                    "في تطبيق مسموح مختلف، أو حقل آخر ذي معرّف واضح. يُستهلك الطلب مرة واحدة وينتهي بعد ٤٥ ثانية ولا يعمل عند قفل الجهاز. يتطلب التأكيد بقاء الحقل والحافظة دون تغيير. قد ترفض بعض المحررات أمر اللصق.",
                "المسح والنسخ الاحتياطي" to
                    "تُحذف العناصر المنتهية عند فتح التطبيق أو في مهمة نظام محلية قد تتأخر أثناء النوم. لا نمسح حافظة أحدث، وقد ننتظر عودة صلاحية الوصول. التصدير اختياري ومشفّر بعبارة مرور، ولا يشمل مفاتيح الجهاز. احفظ الملف والعبارة بنفسك.",
                "أنت صاحب القرار" to
                    "يمكنك الإيقاف من الإشعار، وسحب الموافقة، وتعطيل لوحة اللصق وإمكانية الوصول من النظام، أو حذف جميع البيانات من الإعدادات. قفل الواجهة لا يلغي خدمة سبق أن شغّلتها على جهاز مفتوح.",
            )
            .forEachIndexed { i, (t, b) ->
                Panel {
                    Column(Modifier.padding(16.dp)) {
                        Badge("${n(i+1)}  /  الخصوصية", i % 2 == 0)
                        Spacer(Modifier.height(5.dp))
                        Txt(t, 17, bold = true)
                        Txt(b, 14, Colors.current.muted)
                    }
                }
            }
    }
}
