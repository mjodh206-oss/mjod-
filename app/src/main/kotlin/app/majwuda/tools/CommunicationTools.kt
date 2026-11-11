package app.majwuda.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import app.majwuda.claude.Schema
import app.majwuda.claude.ToolDefinition
import app.majwuda.core.ArabicText
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import app.majwuda.core.str

/** Contacts, calls, SMS, email — all on-device actions. */
object CommunicationTools {

    fun register(ctx: ToolContext): List<ToolSpec> = listOf(
        searchContacts(ctx),
        makeCall(ctx),
        dialOnly(ctx),
        sendSms(ctx),
        readSms(ctx),
        sendEmail(ctx),
    )

    // ------------------------------------------------------------------ contacts

    private fun searchContacts(ctx: ToolContext) = ToolSpec(
        id = "search_contacts",
        gates = setOf(ToolGate.CONTACTS),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "search_contacts",
            description = "ابحث في جهات الاتصال على الهاتف حسب الاسم. استخدمها قبل الاتصال أو إرسال رسالة لمعرفة رقم الشخص. تعيد قائمة الاسم والأرقام الأقرب.",
            schema = Schema.object_(
                "query" to Schema.string("الاسم أو جزء منه كما ينطقه المستخدم"),
                "limit" to Schema.integer("عدد النتائج القصوى", minimum = 1, maximum = 10, default = 6),
                required = listOf("query"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val query = args.str("query").orEmpty()
            val limit = (args["limit"] as? JsonPrimitive)?.intOrNull?.coerceIn(1, 10) ?: 6
            val results = ctx.contacts.search(query, limit)
            if (results.isEmpty()) {
                ToolOutput.ok("لم أجد جهة اتصال باسم «$query» في جهات الاتصال.")
            } else {
                val lines = results.map { c ->
                    "${c.displayName}: ${c.phoneNumbers.joinToString("، ")}"
                }
                ToolOutput.ok(
                    "وجدت ${results.size} جهة اتصال:\n" + lines.joinToString("\n"),
                    toolResultForModel = "اتصالات مطابقة لـ «$query»:\n" + lines.joinToString("\n"),
                )
            }
        },
    )

    // ------------------------------------------------------------------ call

    private fun makeCall(ctx: ToolContext) = ToolSpec(
        id = "make_call",
        gates = setOf(ToolGate.CALL),
        risk = ToolRisk.CONFIRM,
        confirmationPrompt = { args ->
            val name = args.str("target").orEmpty()
            "هل أتصل بـ «$name» الآن؟"
        },
        definition = ToolDefinition(
            name = "make_call",
            description = "اتصال هاتفي مباشر بجهة اتصال أو رقم. الترجيع: استخدم search_contacts أولاً إن لم يكن الرقم عندك. هو إجراء حساس ولهذا اسأل المستخدم قبل التنفيذ ما لم تكن خاصية «الاتصال التلقائي» مفعلة.",
            schema = Schema.object_(
                "target" to Schema.string("الاسم كما قاله المستخدم أو رقم الهاتف كما هو"),
                required = listOf("target"),
            ),
            destructive = false,
            communication = true,
        ),
        handler = ToolHandler { _, args ->
            val target = args.str("target").orEmpty()
            val (number, label) = resolveTarget(ctx, target)
                ?: return@ToolHandler ToolOutput.fail("لم أجد «$target» في جهات الاتصال ولم أتعرف على رقم صالح.")
            val appContext = ctx.context.applicationContext
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return@ToolHandler try {
                appContext.startActivity(intent)
                ToolOutput.ok("أتصل الآن بـ $label.", speak = "أتصل الآن بـ $label")
            } catch (e: SecurityException) {
                openDialerFallback(ctx, number, label)
            } catch (e: Exception) {
                ToolOutput.fail("تعذر إجراء الاتصال: ${e.message}")
            }
        },
    )

    private fun dialOnly(ctx: ToolContext) = ToolSpec(
        id = "open_dialer",
        gates = setOf(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "open_dialer",
            description = "افتح واجهة الاتصال (لوحة الأرقام) مملوءة بالرقم دون اتصال فعلي. استخدمها عندما لا يملك التطبيق إذن الاتصال أو طلب المستخدم فتح الرقم فقط.",
            schema = Schema.object_(
                "number" to Schema.string("رقم الهاتف مع مفتاح الدولة إن وجد"),
                required = listOf("number"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val (number, label) = resolveTarget(ctx, args.str("number").orEmpty()) ?: (args.str("number").orEmpty() to "الرقم")
            if (number.isBlank()) return@ToolHandler ToolOutput.fail("لا يوجد رقم لفتحه.")
            openDialerFallback(ctx, number, label)
        },
    )

    private fun openDialerFallback(ctx: ToolContext, number: String, label: String): ToolOutput {
        val appContext = ctx.context.applicationContext
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            ToolOutput.ok("فتحت لوحة الاتصال على $label.", speak = "فتحت لوحة الاتصال على $label")
        } catch (e: Exception) {
            ToolOutput.fail("تعذر فتح لوحة الاتصال: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ sms

    private fun sendSms(ctx: ToolContext) = ToolSpec(
        id = "send_sms",
        gates = setOf(ToolGate.SMS),
        risk = ToolRisk.CONFIRM,
        confirmationPrompt = { args ->
            val name = args.str("target").orEmpty()
            val body = args.str("message").orEmpty()
            "أأرسل الرسالة التالية إلى «$name»؟\n«${body.take(140)}»"
        },
        definition = ToolDefinition(
            name = "send_sms",
            description = "أرسل رسالة SMS إلى جهة اتصال أو رقم. استخدم search_contacts أولاً إن لم تعرف الرقم. هذا إجراء يُرسل رسالة حقيقية لكلفة على المستخدم، لذا أكّد قبل الإرسال ما لم تكن «إرسال الرسائل دون تأكيد» مفعلة.",
            schema = Schema.object_(
                "target" to Schema.string("الاسم كما ينطقه المستخدم أو رقم الهاتف"),
                "message" to Schema.string("نص الرسالة كاملة كما يريد المستخدم إرسالها"),
                required = listOf("target", "message"),
            ),
            communication = true,
        ),
        handler = ToolHandler { _, args ->
            val target = args.str("target").orEmpty()
            val message = args.str("message").orEmpty()
            if (message.isBlank()) return@ToolHandler ToolOutput.fail("نص الرسالة فارغ.")
            val (number, label) = resolveTarget(ctx, target)
                ?: return@ToolHandler ToolOutput.fail("لم أجد «$target» في جهات الاتصال ولم أتعرف على رقم صالح.")
            val appContext = ctx.context.applicationContext
            return@ToolHandler try {
                val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= 31) {
                    appContext.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                if (smsManager == null) return@ToolHandler openSmsComposer(ctx, number, message, label)
                val parts = smsManager.divideMessage(message)
                if (parts.size == 1) {
                    smsManager.sendTextMessage(number, null, message, null, null)
                } else {
                    smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                }
                ToolOutput.ok("أرسلت الرسالة إلى $label.", speak = "تم إرسال الرسالة إلى $label")
            } catch (e: SecurityException) {
                openSmsComposer(ctx, number, message, label)
            } catch (e: Exception) {
                ToolOutput.fail("تعذر إرسال الرسالة: ${e.message}")
            }
        },
    )

    private fun openSmsComposer(ctx: ToolContext, number: String, message: String, label: String): ToolOutput {
        val appContext = ctx.context.applicationContext
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            ToolOutput.ok("فتحت تطبيق الرسائل بنص الرسالة إلى $label لإتمام الإرسال يدويًا.", speak = "فتحت تطبيق الرسائل لإتمام الإرسال")
        } catch (e: Exception) {
            ToolOutput.fail("تعذر إرسال الرسالة: ${e.message}")
        }
    }

    private fun readSms(ctx: ToolContext) = ToolSpec(
        id = "read_sms",
        gates = emptySet(), // READ_SMS checked explicitly below
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "read_sms",
            description = "اقرأ أحدث الرسائل الواردة غير المقروءة (أو كلها). استخدمه عندما يسأل المستخدم «هل فيه رسائل؟» أو نحوها. يعيد المرسل والنص والتاريخ.",
            schema = Schema.object_(
                "limit" to Schema.integer("عدد الرسائل القصوى", minimum = 1, maximum = 20, default = 8),
                "unreadOnly" to Schema.boolean("غير المقروءة فقط", default = true),
                required = emptyList(),
            ),
        ),
        handler = ToolHandler { _, args ->
            val granted = ContextCompat.checkSelfPermission(ctx.context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return@ToolHandler ToolOutput.fail("لا أملك إذن قراءة الرسائل — يمكنك تفعيله من الإعدادات أو سأفعل ذلك عند طلبك.")
            val limit = (args["limit"] as? JsonPrimitive)?.intOrNull?.coerceIn(1, 20) ?: 8
            val unreadOnly = (args["unreadOnly"] as? JsonPrimitive)?.booleanOrNull != false
            val entries = if (unreadOnly) ctx.sms.recentUnread(limit) else ctx.sms.recent(limit)
            if (entries.isEmpty()) {
                ToolOutput.ok(if (unreadOnly) "لا توجد رسائل غير مقروءة." else "لا توجد رسائل حديثة.")
            } else {
                val lines = entries.map { "من ${it.address}: ${it.body.take(140)}" }
                ToolOutput.ok(
                    "أحدث الرسائل:\n" + lines.joinToString("\n"),
                    toolResultForModel = lines.joinToString("\n"),
                )
            }
        },
    )

    // ------------------------------------------------------------------ email

    private fun sendEmail(ctx: ToolContext) = ToolSpec(
        id = "send_email",
        gates = emptySet(),
        risk = ToolRisk.CONFIRM,
        confirmationPrompt = { args ->
            "هل أفتح مسودة رسالة إلى «${args.str("to")}» بالموضوع «${args.str("subject").orEmpty()}»؟"
        },
        definition = ToolDefinition(
            name = "send_email",
            description = "افتح تطبيق البريد مملوءًا بمسودة رسالة (المستلم، الموضوع، النص) ليرسلها المستخدم بنفسه. لا أرسل البريد تلقائيًا أبدًا.",
            schema = Schema.object_(
                "to" to Schema.string("عنوان البريد أو أكثر مفصولة بفواصل"),
                "subject" to Schema.string("موضوع الرسالة"),
                "body" to Schema.string("نص الرسالة"),
                required = listOf("to", "subject", "body"),
            ),
            communication = true,
        ),
        handler = ToolHandler { _, args ->
            val to = args.str("to").orEmpty()
            if (to.isBlank()) return@ToolHandler ToolOutput.fail("لا يوجد مستلم للبريد.")
            val subject = args.str("subject").orEmpty()
            val body = args.str("body").orEmpty()
            val appContext = ctx.context.applicationContext
            return@ToolHandler try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$to")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                ToolOutput.ok("فتحت مسودة البريد إلى $to — تبقى المراجعة والإرسال بيدك.", speak = "فتحت مسودة البريد لتراجعها وترسلها")
            } catch (e: Exception) {
                ToolOutput.fail("تعذر فتح تطبيق البريد: ${e.message}")
            }
        },
    )

    // ------------------------------------------------------------------ shared resolution

    /**
     * Resolves [target] (contact name or phone number) to a (number, displayLabel) pair.
     * Arabic-normalized fuzzy matching is used for names.
     */
    private suspend fun resolveTarget(ctx: ToolContext, target: String): Pair<String, String>? {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) return null
        // Looks like a phone number already?
        val digits = trimmed.filter { it.isDigit() || it == '+' }
        if (digits.length >= 7 && trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }) {
            return digits to trimmed
        }
        val exact = ctx.contacts.search(target, 3).firstOrNull()
        if (exact != null) return (exact.primaryNumber ?: return null) to exact.displayName
        val best = ctx.contacts.bestMatch(target, minScore = 0.45)
        if (best != null) return (best.primaryNumber ?: return null) to best.displayName
        return null
    }
}

