package app.majwuda.tools

import android.content.Intent
import app.majwuda.claude.Schema
import app.majwuda.claude.ToolDefinition
import app.majwuda.core.int
import app.majwuda.core.str
import app.majwuda.services.AccessibilityBridge
import app.majwuda.services.NotificationStore
import app.majwuda.ui.AssistActivity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Screen understanding and control through the opt-in accessibility service, and notification
 * reading through the opt-in notification listener.
 */
object ScreenTools {

    fun register(ctx: ToolContext): List<ToolSpec> = listOf(
        readScreen(ctx),
        tapText(ctx),
        typeText(ctx),
        scroll(ctx),
        globalBack(ctx),
        globalHome(ctx),
        globalRecents(ctx),
        openNotificationsShade(ctx),
        takeScreenshot(ctx),
        readNotifications(ctx),
        openVoiceAssistant(ctx),
    )

    private fun notAvailable(): ToolOutput =
        ToolOutput.fail("التحكم بالشاشة غير مفعّل — فعّل «التحكم بالشاشة» من الإعدادات ثم اطلب مني مرة أخرى.")

    private fun readScreen(ctx: ToolContext) = ToolSpec(
        id = "read_screen",
        gates = setOf(ToolGate.ACCESSIBILITY),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "read_screen",
            description = "اقرأ كل النص الظاهر على الشاشة الآن (بما فيها التطبيقات الأخرى). استخدمه لفهم ما يراه المستخدم.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            val read = AccessibilityBridge.readVisibleText() ?: return@ToolHandler notAvailable()
            if (read.text.isBlank()) {
                ToolOutput.ok("الشاشة فارغة أو لا تحتوي نصًا يمكن قراءته.", toolResultForModel = "(empty screen)")
            } else {
                ToolOutput.ok(read.text, toolResultForModel = "محتوى الشاشة الحالية (التطبيق: ${read.appPackage ?: "?"}):\n${read.text}")
            }
        },
    )

    private fun tapText(ctx: ToolContext) = ToolSpec(
        id = "tap_text",
        gates = setOf(ToolGate.ACCESSIBILITY),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "tap_text",
            description = "اضغط العنصر الذي يظهر عليه نص مطابق (أو وصف مطابق) في التطبيق الأمامي.",
            schema = Schema.object_(
                "text" to Schema.string("النص المطلوب الضغط عليه كما يظهر على الشاشة"),
                required = listOf("text"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val query = args.str("text").orEmpty()
            if (query.isBlank()) return@ToolHandler ToolOutput.fail("ما العنصر الذي تريد الضغط عليه؟")
            val tapped = AccessibilityBridge.tapText(query)
            if (tapped != null) {
                ToolOutput.ok("ضغطت «$tapped».", speak = "تم")
            } else {
                ToolOutput.fail("لم أجد «$query» على الشاشة — ربما تغيّرت الواجهة.")
            }
        },
    )

    private fun typeText(ctx: ToolContext) = ToolSpec(
        id = "type_text",
        gates = setOf(ToolGate.ACCESSIBILITY),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "type_text",
            description = "اكتب نصًا في الحقل المحدد حاليًا (الحقل الذي يملك التركيز).",
            schema = Schema.object_(
                "text" to Schema.string("النص المراد كتابته"),
                required = listOf("text"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val text = args.str("text").orEmpty()
            if (text.isBlank()) return@ToolHandler ToolOutput.fail("لا يوجد نص للكتابة.")
            if (AccessibilityBridge.typeIntoFocused(text)) {
                ToolOutput.ok("كتبت النص.", speak = "تم")
            } else {
                ToolOutput.fail("لم أجد حقلاً نشطًا للكتابة فيه.")
            }
        },
    )

    private fun scroll(ctx: ToolContext) = ToolSpec(
        id = "scroll_screen",
        gates = setOf(ToolGate.ACCESSIBILITY),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "scroll_screen",
            description = "مرّر الشاشة أو القائمة الحالية لأعلى أو لأسفل.",
            schema = Schema.object_(
                "direction" to Schema.string("اتجاه التمرير", enumValues = listOf("up", "down"), default = "down"),
                required = listOf("direction"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val direction = args.str("direction")?.lowercase() ?: "down"
            if (AccessibilityBridge.scroll(direction)) {
                ToolOutput.ok("مررت الشاشة.", speak = "تم")
            } else {
                ToolOutput.fail("تعذر التمرير — لا توجد منطقة قابلة للتمرير.")
            }
        },
    )

    private fun globalBack(ctx: ToolContext) = ToolSpec(
        id = "system_back",
        gates = setOf(ToolGate.ACCESSIBILITY),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "system_back",
            description = "نفّذ زر الرجوع في النظام.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            if (AccessibilityBridge.globalBack()) ToolOutput.ok("رجعت خطوة.", speak = "تم") else notAvailable()
        },
    )

    private fun globalHome(ctx: ToolContext) = ToolSpec(
        id = "system_home",
        gates = setOf(ToolGate.ACCESSIBILITY),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "system_home",
            description = "ارجع إلى الشاشة الرئيسية.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            if (AccessibilityBridge.globalHome()) ToolOutput.ok("الشاشة الرئيسية.", speak = "تم") else notAvailable()
        },
    )

    private fun globalRecents(ctx: ToolContext) = ToolSpec(
        id = "system_recents",
        gates = setOf(ToolGate.ACCESSIBILITY),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "system_recents",
            description = "افتح قائمة التطبيقات الحديثة.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            if (AccessibilityBridge.globalRecents()) ToolOutput.ok("فتحت التطبيقات الحديثة.", speak = "تم") else notAvailable()
        },
    )

    private fun openNotificationsShade(ctx: ToolContext) = ToolSpec(
        id = "open_notifications_shade",
        gates = setOf(ToolGate.ACCESSIBILITY),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "open_notifications_shade",
            description = "اسحب لوحة الإشعارات (مركز الإشعارات) من الأعلى.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            if (AccessibilityBridge.globalNotifications()) ToolOutput.ok("فتحت الإشعارات.", speak = "تم") else notAvailable()
        },
    )

    private fun takeScreenshot(ctx: ToolContext) = ToolSpec(
        id = "take_screenshot",
        gates = setOf(ToolGate.ACCESSIBILITY),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "take_screenshot",
            description = "التقط لقطة شاشة واعرضها. مفيدة عندما يسألك المستخدم «ماذا ترى على شاشتي؟» أو يريد مشاركة ما يراه.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            if (!AccessibilityBridge.isConnected) return@ToolHandler notAvailable()
            val dir = File(ctx.context.cacheDir, "captures")
            val file = File(dir, "screen-${System.currentTimeMillis()}.jpg")
            val ok = AccessibilityBridge.captureScreenshotTo(file)
            if (!ok || !file.exists()) {
                ToolOutput.fail("تعذر التقاط لقطة الشاشة (قد يحتاج الجهاز API 30+).")
            } else {
                ToolOutput.ok(
                    "التقطت لقطة الشاشة.",
                    speak = "تم",
                    card = ToolCard.Image(title = "لقطة الشاشة", path = file.absolutePath, mediaType = "image/jpeg"),
                    toolResultForModel = "(screenshot captured and shown to the user)",
                )
            }
        },
    )

    private fun readNotifications(ctx: ToolContext) = ToolSpec(
        id = "read_notifications",
        gates = setOf(ToolGate.NOTIFICATION_ACCESS),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "read_notifications",
            description = "اقرأ الإشعارات الأخيرة الظاهرة على الجهاز (اختياري يقرأ الأحدث أو يبحث بعبارة).",
            schema = Schema.object_(
                "query" to Schema.string("عبارة بحث اختيارية لتصفية الإشعارات"),
                "limit" to Schema.integer("عدد الإشعارات", minimum = 1, maximum = 20, default = 8),
                required = emptyList(),
            ),
        ),
        handler = ToolHandler { _, args ->
            val limit = (args.int("limit") ?: 8).coerceIn(1, 20)
            val query = args.str("query")?.trim().orEmpty()
            val items = if (query.isBlank()) NotificationStore.snapshot(limit) else NotificationStore.search(query, limit)
            if (items.isEmpty()) {
                ToolOutput.ok(if (query.isBlank()) "لا توجد إشعارات حديثة." else "لا توجد إشعارات تطابق «$query».")
            } else {
                val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale("ar")).withZone(ZoneId.systemDefault())
                val lines = items.map { "[${it.appLabel}] ${it.title.ifBlank { it.text.take(60) }} — ${fmt.format(Instant.ofEpochMilli(it.postedAt))}" }
                ToolOutput.ok(
                    "الإشعارات:\n" + lines.joinToString("\n"),
                    toolResultForModel = lines.joinToString("\n"),
                )
            }
        },
    )

    private fun openVoiceAssistant(ctx: ToolContext) = ToolSpec(
        id = "open_voice_assistant",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "open_voice_assistant",
            description = "افتح واجهة مجودة الصوتية للاستماع للمستخدم مباشرة.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            val appContext = ctx.context.applicationContext
            return@ToolHandler try {
                val intent = Intent(appContext, AssistActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(AssistActivity.EXTRA_LISTEN, true)
                }
                appContext.startActivity(intent)
                ToolOutput.ok("أنا أستمع الآن.", speak = "أنا أستمع")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر فتح واجهة الصوت: ${e.message}")
            }
        },
    )
}
