package app.majwuda.tools

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import app.majwuda.claude.Schema
import app.majwuda.claude.ToolDefinition
import app.majwuda.core.str

/** Opening apps and URLs, fuzzy app lookup. */
object AppTools {

    fun register(ctx: ToolContext): List<ToolSpec> = listOf(
        openApp(ctx),
        findApp(ctx),
        openUrl(ctx),
        openLocation(ctx),
        openDialPad(ctx),
    )

    private suspend fun resolvePackage(ctx: ToolContext, query: String): Pair<String, String>? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val exact = ctx.apps.launchableApps().firstOrNull {
            it.label.equals(trimmed, ignoreCase = true) || it.packageName.equals(trimmed, ignoreCase = true)
        }
        if (exact != null) return exact.packageName to exact.label
        val found = ctx.apps.find(trimmed, limit = 1, minScore = 0.45).firstOrNull()
        return found?.let { it.packageName to it.label }
    }

    private fun openApp(ctx: ToolContext) = ToolSpec(
        id = "open_app",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "open_app",
            description = "افتح تطبيقًا مثبتًا على الجهاز باسمه (عربي أو إنجليزي أو اسم الحزمة). إذا لم يكن الاسم مطابقًا تمامًا يجرب البحث التقريبي عن أقرب تطبيق.",
            schema = Schema.object_(
                "name" to Schema.string("اسم التطبيق كما ينطقه المستخدم"),
                required = listOf("name"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val query = args.str("name").orEmpty()
            val resolved = resolvePackage(ctx, query) ?: return@ToolHandler ToolOutput.fail("لم أجد تطبيقًا باسم «$query» على الجهاز.")
            val launched = try {
                app.majwuda.data.AppIntents.launch(ctx.context, resolved.first)
            } catch (e: Exception) {
                false
            }
            if (launched) {
                ToolOutput.ok("فتحت ${resolved.second}.", speak = "فتحت ${resolved.second}")
            } else {
                ToolOutput.fail("تعذر فتح ${resolved.second} — قد يكون أُزيل أو يحتاج إذنًا.")
            }
        },
    )

    private fun findApp(ctx: ToolContext) = ToolSpec(
        id = "find_app",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "find_app",
            description = "ابحث عن اسم حزمة لتطبيق مثبت دون فتحه. أعد أقرب النتائج.",
            schema = Schema.object_(
                "query" to Schema.string("اسم التطبيق"),
                required = listOf("query"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val results = ctx.apps.find(args.str("query").orEmpty(), limit = 6)
            if (results.isEmpty()) {
                ToolOutput.ok("لم أجد تطبيقات مطابقة.")
            } else {
                ToolOutput.ok(
                    "أفضل المطابقات:\n" + results.joinToString("\n") { "- ${it.label} (${it.packageName})" },
                    toolResultForModel = results.joinToString("\n") { "${it.label} -> ${it.packageName}" },
                )
            }
        },
    )

    private fun openUrl(ctx: ToolContext) = ToolSpec(
        id = "open_url",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "open_url",
            description = "افتح رابط https في المتصفح الافتراضي (مثلاً موقع بحث أو موقع معين ذكره المستخدم).",
            schema = Schema.object_(
                "url" to Schema.string("الرابط كاملًا ويبدأ بـ https:// أو http://"),
                required = listOf("url"),
            ),
        ),
        handler = ToolHandler { _, args ->
            var url = args.str("url").orEmpty().trim()
            if (url.isEmpty()) return@ToolHandler ToolOutput.fail("لا يوجد رابط.")
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            val appContext = ctx.context.applicationContext
            return@ToolHandler try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
                ToolOutput.ok("فتحت الرابط.", speak = "فتحت الرابط")
            } catch (e: ActivityNotFoundException) {
                ToolOutput.fail("لا يوجد متصفح متاح لفتح الرابط.")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر فتح الرابط: ${e.message}")
            }
        },
    )

    private fun openLocation(ctx: ToolContext) = ToolSpec(
        id = "open_location",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "open_location",
            description = "افتح موقعًا على الخريطة أو ابدأ الملاحة إليه (وضع القيادة أو المشي).",
            schema = Schema.object_(
                "query" to Schema.string("اسم المكان أو العنوان"),
                "mode" to Schema.string("طريقة الملاحة", enumValues = listOf("show", "navigate_drive", "navigate_walk", "navigate_bike"), default = "show"),
                required = listOf("query"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val query = args.str("query").orEmpty()
            if (query.isBlank()) return@ToolHandler ToolOutput.fail("ما المكان الذي تريده؟")
            val appContext = ctx.context.applicationContext
            val encoded = Uri.encode(query)
            val intent = when (args.str("mode")?.lowercase()) {
                "navigate_drive" -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded&mode=d"))
                "navigate_walk" -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded&mode=w"))
                "navigate_bike" -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded&mode=b"))
                else -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return@ToolHandler try {
                appContext.startActivity(intent)
                ToolOutput.ok("فتحت الخريطة لـ «$query».", speak = "فتحت الخريطة")
            } catch (e: ActivityNotFoundException) {
                ToolOutput.fail("لا توجد تطبيقات خريطة مثبتة.")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر فتح الخريطة: ${e.message}")
            }
        },
    )

    private fun openDialPad(ctx: ToolContext) = ToolSpec(
        id = "open_dial_pad",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "open_dial_pad",
            description = "افتح لوحة الأرقام الهاتفية فارغة (بدون رقم).",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            val appContext = ctx.context.applicationContext
            return@ToolHandler try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
                ToolOutput.ok("فتحت لوحة الأرقام.", speak = "فتحت لوحة الأرقام")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر فتح لوحة الأرقام: ${e.message}")
            }
        },
    )
}
