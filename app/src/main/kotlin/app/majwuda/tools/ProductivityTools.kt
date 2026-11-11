package app.majwuda.tools

import android.Manifest
import android.content.pm.PackageManager
import android.provider.AlarmClock
import androidx.core.content.ContextCompat
import app.majwuda.claude.Schema
import app.majwuda.claude.ToolDefinition
import app.majwuda.core.TimeSupport
import app.majwuda.core.bool
import app.majwuda.core.int
import app.majwuda.core.long
import app.majwuda.core.str
import app.majwuda.data.db.NoteEntity
import app.majwuda.data.db.Recurrence
import app.majwuda.data.db.ReminderEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Notes, reminders, calendar, clipboard, memory and screen-time-ish actions. */
object ProductivityTools {

    fun register(ctx: ToolContext): List<ToolSpec> = listOf(
        saveNote(ctx),
        listNotes(ctx),
        searchNotes(ctx),
        deleteNote(ctx),
        addReminder(ctx),
        listReminders(ctx),
        completeReminder(ctx),
        cancelReminder(ctx),
        addCalendarEvent(ctx),
        listCalendarEvents(ctx),
        searchCalendarEvents(ctx),
        copyToClipboard(ctx),
        readClipboard(ctx),
        rememberMemory(ctx),
        recallMemories(ctx),
        forgetMemory(ctx),
    )

    private val ARABIC = Locale("ar")

    private fun fmt(millis: Long): String =
        DateTimeFormatter.ofPattern("EEEE d MMMM، الساعة h:mm a", ARABIC)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(millis))

    // ------------------------------------------------------------------ notes

    private fun saveNote(ctx: ToolContext) = ToolSpec(
        id = "save_note",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "save_note",
            description = "احفظ ملاحظة سريعة (عنوان ونص). استخدمها عندما يطلب المستخدم «دوّن...» أو «اكتب ملاحظة...» أو «سجل...».",
            schema = Schema.object_(
                "title" to Schema.string("عنوان قصير للملاحظة"),
                "body" to Schema.string("نص الملاحظة كاملة"),
                "tags" to Schema.array(Schema.string("وسم"), "وسوم اختيارية"),
                required = listOf("title", "body"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val title = args.str("title")?.trim().orEmpty()
            val body = args.str("body")?.trim().orEmpty()
            if (body.isEmpty()) return@ToolHandler ToolOutput.fail("نص الملاحظة فارغ.")
            val tags = (args["tags"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?.joinToString(",") ?: ""
            val now = System.currentTimeMillis()
            val id = ctx.db.noteDao().insert(
                NoteEntity(title = title.ifBlank { body.take(40) }, body = body, tags = tags, createdAt = now, updatedAt = now),
            )
            ToolOutput.ok(
                "حفظت الملاحظة «${title.ifBlank { body.take(40) }}».",
                speak = "تم حفظ الملاحظة",
                card = app.majwuda.tools.ToolCard.Note(title = title.ifBlank { body.take(40) }, body = body, tags = tags.split(",").filter { it.isNotBlank() }),
            )
        },
    )

    private fun listNotes(ctx: ToolContext) = ToolSpec(
        id = "list_notes",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "list_notes",
            description = "اعرض أحدث الملاحظات المحفوظة.",
            schema = Schema.object_(
                "limit" to Schema.integer("عدد الملاحظات", minimum = 1, maximum = 20, default = 10),
                required = emptyList(),
            ),
        ),
        handler = ToolHandler { _, args ->
            val limit = args.int("limit")?.coerceIn(1, 20) ?: 10
            val notes = ctx.db.noteDao().all(limit)
            if (notes.isEmpty()) {
                ToolOutput.ok("لا توجد ملاحظات محفوظة بعد.")
            } else {
                val rows = notes.map { CardRow(it.title, it.body.take(90), tagsSub(it.tags)) }
                ToolOutput.ok(
                    "آخر الملاحظات:\n" + notes.joinToString("\n") { "- ${it.title}" },
                    card = ToolCard.ListItems("الملاحظات", rows),
                    toolResultForModel = notes.joinToString("\n") { "#${it.id} ${it.title}: ${it.body.take(140)}" },
                )
            }
        },
    )

    private fun searchNotes(ctx: ToolContext) = ToolSpec(
        id = "search_notes",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "search_notes",
            description = "ابحث في الملاحظات المحفوظة بعبارة.",
            schema = Schema.object_(
                "query" to Schema.string("العبارة المطلوب البحث عنها"),
                required = listOf("query"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val query = args.str("query").orEmpty()
            val notes = ctx.db.noteDao().search(query)
            if (notes.isEmpty()) {
                ToolOutput.ok("لا توجد ملاحظات تطابق «$query».")
            } else {
                ToolOutput.ok(
                    "وجدت ${notes.size} ملاحظة تطابق «$query».",
                    card = ToolCard.ListItems(
                        "نتائج البحث عن «$query»",
                        notes.map { CardRow(it.title, it.body.take(90)) },
                    ),
                    toolResultForModel = notes.joinToString("\n") { "#${it.id} ${it.title}: ${it.body.take(200)}" },
                )
            }
        },
    )

    private fun deleteNote(ctx: ToolContext) = ToolSpec(
        id = "delete_note",
        gates = emptySet(),
        risk = ToolRisk.DESTRUCTIVE,
        confirmationPrompt = { args -> "هل أحذف الملاحظة رقم ${args.str("id")} نهائيًا؟" },
        definition = ToolDefinition(
            name = "delete_note",
            description = "احذف ملاحظة بمعرّفها (id). عملية حذف نهائية، تُطلب دائمًا تأكيدًا.",
            schema = Schema.object_(
                "id" to Schema.integer("معرّف الملاحظة"),
                required = listOf("id"),
            ),
            destructive = true,
        ),
        handler = ToolHandler { _, args ->
            val id = args.long("id") ?: return@ToolHandler ToolOutput.fail("المعرّف غير واضح.")
            val note = ctx.db.noteDao().get(id) ?: return@ToolHandler ToolOutput.fail("لا توجد ملاحظة برقم $id.")
            ctx.db.noteDao().delete(note)
            ToolOutput.ok("حذفت الملاحظة «${note.title}».", speak = "تم الحذف")
        },
    )

    private fun tagsSub(tags: String): String? = tags.split(",").filter { it.isNotBlank() }
        .joinToString(" #", prefix = "#")
        .ifEmpty { null }

    // ------------------------------------------------------------------ reminders

    private fun addReminder(ctx: ToolContext) = ToolSpec(
        id = "add_reminder",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "add_reminder",
            description = "أضف تذكيرًا أو موعدًا بوقت محدد؛ مثل «ذكّرني بكرة الساعة 7 صباحًا أن أتصل بأحمد». يظهر كإشعار في وقته.",
            schema = Schema.object_(
                "title" to Schema.string("موضوع التذكير باختصار"),
                "body" to Schema.string("تفاصيل إضافية (اختياري)"),
                "at" to Schema.string("الموعد بأي صيغة طبيعية: «بكرة الساعة 7 صباحًا»، «الثلاثاء 9:30 مساء»، «بعد 3 ساعات» — أو ISO 8601 إن توفّر لديك"),
                "recurrence" to Schema.string("التكرار", enumValues = listOf("none", "daily", "weekly", "monthly", "yearly"), default = "none"),
                required = listOf("title", "at"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val title = args.str("title")?.trim().orEmpty()
            if (title.isBlank()) return@ToolHandler ToolOutput.fail("ما هو موضوع التذكير؟")
            val whenText = args.str("at")?.trim().orEmpty()
            val now = LocalDateTime.now()
            val dueAt: Long = TimeSupport.parseDateTimePhrase(whenText, now, defaultHour = 9)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: return@ToolHandler ToolOutput.fail("لم أفهم موعد «$whenText». جرّب صيغة مثل: بكرة الساعة 7 صباحًا.")
            val recurrence = Recurrence.entries.firstOrNull { it.name.equals(args.str("recurrence") ?: "none", ignoreCase = true) } ?: Recurrence.NONE
            val requestCode = (System.currentTimeMillis() % 1_000_000_000L).toInt() + (1..1000).random()
            val id = ctx.db.reminderDao().insert(
                ReminderEntity(
                    title = title,
                    body = args.str("body")?.trim()?.takeIf { it.isNotEmpty() },
                    dueAt = dueAt,
                    recurrence = recurrence.name,
                    requestCode = requestCode,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            ctx.reminders.scheduleById(id)
            ToolOutput.ok(
                "ذكّرتك: ${fmt(dueAt)} — «$title»",
                speak = "تم ضبط التذكير",
                card = ToolCard.Reminder(title = title, body = args.str("body"), at = dueAt, done = false),
            )
        },
    )

    private fun listReminders(ctx: ToolContext) = ToolSpec(
        id = "list_reminders",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "list_reminders",
            description = "اعرض التذكيرات القادمة.",
            schema = Schema.object_(
                "limit" to Schema.integer("العدد", minimum = 1, maximum = 20, default = 10),
                required = emptyList(),
            ),
        ),
        handler = ToolHandler { _, args ->
            val limit = args.int("limit")?.coerceIn(1, 20) ?: 10
            val items = ctx.db.reminderDao().nextPendingAfter(System.currentTimeMillis() - 1).take(limit)
            if (items.isEmpty()) {
                ToolOutput.ok("لا توجد تذكيرات قادمة.")
            } else {
                ToolOutput.ok(
                    "تذكيراتك القادمة:\n" + items.joinToString("\n") { "- ${fmt(it.dueAt)}: ${it.title}" },
                    card = ToolCard.ListItems(
                        "التذكيرات",
                        items.map { CardRow(it.title, fmt(it.dueAt), it.body) },
                    ),
                    toolResultForModel = items.joinToString("\n") { "#${it.id} ${fmt(it.dueAt)} ${it.title}" },
                )
            }
        },
    )

    private fun completeReminder(ctx: ToolContext) = ToolSpec(
        id = "complete_reminder",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "complete_reminder",
            description = "علّم تذكيرًا كمنجز (done) بمعرّفه.",
            schema = Schema.object_(
                "id" to Schema.integer("معرّف التذكير"),
                required = listOf("id"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val id = args.long("id") ?: return@ToolHandler ToolOutput.fail("المعرّف غير واضح.")
            val reminder = ctx.db.reminderDao().get(id) ?: return@ToolHandler ToolOutput.fail("لا يوجد تذكير برقم $id.")
            ctx.db.reminderDao().markDone(id, System.currentTimeMillis())
            ctx.reminders.cancelById(id)
            ToolOutput.ok("أنجزت التذكير «${reminder.title}».", speak = "تم")
        },
    )

    private fun cancelReminder(ctx: ToolContext) = ToolSpec(
        id = "cancel_reminder",
        gates = emptySet(),
        risk = ToolRisk.DESTRUCTIVE,
        confirmationPrompt = { args -> "هل أحذف التذكير رقم ${args.str("id")} نهائيًا؟" },
        definition = ToolDefinition(
            name = "cancel_reminder",
            description = "احذف تذكيرًا بمعرّفه نهائيًا.",
            schema = Schema.object_(
                "id" to Schema.integer("معرّف التذكير"),
                required = listOf("id"),
            ),
            destructive = true,
        ),
        handler = ToolHandler { _, args ->
            val id = args.long("id") ?: return@ToolHandler ToolOutput.fail("المعرّف غير واضح.")
            val reminder = ctx.db.reminderDao().get(id) ?: return@ToolHandler ToolOutput.fail("لا يوجد تذكير برقم $id.")
            ctx.reminders.cancelById(id)
            ctx.db.reminderDao().delete(reminder)
            ToolOutput.ok("حذفت التذكير «${reminder.title}».", speak = "تم الحذف")
        },
    )

    // ------------------------------------------------------------------ calendar

    private fun addCalendarEvent(ctx: ToolContext) = ToolSpec(
        id = "add_calendar_event",
        gates = setOf(ToolGate.CALENDAR),
        risk = ToolRisk.CONFIRM,
        confirmationPrompt = { args ->
            "هل أضيف موعد «${args.str("title")}» إلى التقويم؟"
        },
        definition = ToolDefinition(
            name = "add_calendar_event",
            description = "أضف حدثًا إلى تقويم الجهاز. يحتاج إذن التقويم ويؤكد المستخدم قبل الإضافة.",
            schema = Schema.object_(
                "title" to Schema.string("عنوان الموعد"),
                "at" to Schema.string("موعد البداية بالصيغة الطبيعية («الثلاثاء 5 مساء») أو ISO 8601"),
                "durationMinutes" to Schema.integer("المدة بالدقائق (افتراضي 60)", minimum = 5, default = 60),
                "location" to Schema.string("المكان (اختياري)"),
                "notes" to Schema.string("ملاحظات (اختياري)"),
                required = listOf("title", "at"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val hasWrite = ContextCompat.checkSelfPermission(ctx.context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
            if (!hasWrite) return@ToolHandler ToolOutput.fail("لا أملك إذن الكتابة في التقويم — فعّله من الإعدادات.")
            val title = args.str("title").orEmpty().ifBlank { return@ToolHandler ToolOutput.fail("عنوان الموعد فارغ.") }
            val now = LocalDateTime.now()
            val start = TimeSupport.parseDateTimePhrase(args.str("at").orEmpty(), now, defaultHour = 9)
                ?: return@ToolHandler ToolOutput.fail("لم أفهم موعد «${args.str("at")}».")
            val duration = args.int("durationMinutes") ?: 60
            val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = startMillis + duration * 60_000L
            val id = ctx.calendar.insertEvent(title, startMillis, endMillis, args.str("notes"), args.str("location"))
                ?: return@ToolHandler ToolOutput.fail("تعذرت إضافة الموعد — تأكد أن التقويم مُعد في الهاتف.")
            ToolOutput.ok(
                "أضفت موعد «$title» في ${fmt(startMillis)}.",
                speak = "تمت إضافة الموعد",
                card = ToolCard.KeyValues("تمت الإضافة إلى التقويم", listOf(CardRow("الموعد", title), CardRow("الوقت", fmt(startMillis)), CardRow("المكان", args.str("location") ?: "—"))),
            )
        },
    )

    private fun listCalendarEvents(ctx: ToolContext) = ToolSpec(
        id = "list_calendar_events",
        gates = setOf(ToolGate.CALENDAR),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "list_calendar_events",
            description = "اعرض مواعيد التقويم «اليوم» أو نطاق أيام قادم.",
            schema = Schema.object_(
                "range" to Schema.string("النطاق", enumValues = listOf("today", "tomorrow", "week", "month"), default = "today"),
                required = emptyList(),
            ),
        ),
        handler = ToolHandler { _, args ->
            val hasRead = ContextCompat.checkSelfPermission(ctx.context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            if (!hasRead) return@ToolHandler ToolOutput.fail("لا أملك إذن قراءة التقويم — فعّله من الإعدادات.")
            val range = args.str("range")?.lowercase() ?: "today"
            val now = LocalDateTime.now()
            val events = when (range) {
                "tomorrow" -> ctx.calendar.eventsOn(now.toLocalDate().plusDays(1))
                "week" -> ctx.calendar.upcomingEvents(daysAhead = 7, limit = 12)
                "month" -> ctx.calendar.upcomingEvents(daysAhead = 31, limit = 20)
                else -> ctx.calendar.eventsOn(now.toLocalDate())
            }
            if (events.isEmpty()) {
                ToolOutput.ok(if (range == "today") "لا توجد مواعيد اليوم." else "لا توجد مواعيد قادمة.")
            } else {
                ToolOutput.ok(
                    "مواعيدك:\n" + events.joinToString("\n") { "- ${fmt(it.startMillis)}: ${it.title}" },
                    card = ToolCard.ListItems(
                        if (range == "today") "مواعيد اليوم" else "مواعيدك",
                        events.map { CardRow(it.title, fmt(it.startMillis), it.location) },
                    ),
                    toolResultForModel = events.joinToString("\n") { "${fmt(it.startMillis)} ${it.title} (id=${it.id})" },
                )
            }
        },
    )

    private fun searchCalendarEvents(ctx: ToolContext) = ToolSpec(
        id = "search_calendar_events",
        gates = setOf(ToolGate.CALENDAR),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "search_calendar_events",
            description = "ابحث في مواعيد التقويم بعبارة.",
            schema = Schema.object_(
                "query" to Schema.string("العبارة المطلوب البحث عنها في عناوين المواعيد"),
                required = listOf("query"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val hasRead = ContextCompat.checkSelfPermission(ctx.context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            if (!hasRead) return@ToolHandler ToolOutput.fail("لا أملك إذن قراءة التقويم — فعّله من الإعدادات.")
            val query = args.str("query").orEmpty()
            val events = ctx.calendar.searchEvents(query)
            if (events.isEmpty()) {
                ToolOutput.ok("لا توجد مواعيد تطابق «$query».")
            } else {
                ToolOutput.ok(
                    "وجدت ${events.size} موعدًا مطابقًا لـ «$query».",
                    card = ToolCard.ListItems("نتائج البحث عن «$query»", events.map { CardRow(it.title, fmt(it.startMillis), it.location) }),
                    toolResultForModel = events.joinToString("\n") { "${fmt(it.startMillis)} ${it.title}" },
                )
            }
        },
    )

    // ------------------------------------------------------------------ clipboard

    private fun copyToClipboard(ctx: ToolContext) = ToolSpec(
        id = "copy_to_clipboard",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "copy_to_clipboard",
            description = "انسخ نصًا إلى حافظة الجهاز (مثلاً رقم أو لصق نص للمستخدم).",
            schema = Schema.object_(
                "text" to Schema.string("النص المراد نسخه"),
                "label" to Schema.string("تسمية قصيرة (اختياري)"),
                required = listOf("text"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val text = args.str("text").orEmpty()
            if (text.isBlank()) return@ToolHandler ToolOutput.fail("لا يوجد نص لنسخه.")
            val clipboard = ctx.context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(args.str("label") ?: "nedim", text))
            ToolOutput.ok("نسخت النص إلى الحافظة.", speak = "تم النسخ")
        },
    )

    private fun readClipboard(ctx: ToolContext) = ToolSpec(
        id = "read_clipboard",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "read_clipboard",
            description = "اقرأ النص الموجود حاليًا في الحافظة.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            val clipboard = ctx.context.getSystemService(android.content.ClipboardManager::class.java)
            val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            val text = item?.coerceToText(ctx.context)?.toString().orEmpty()
            if (text.isBlank()) ToolOutput.ok("الحافظة فارغة.") else ToolOutput.ok(text, toolResultForModel = "الحافظة تحتوي: $text")
        },
    )

    // ------------------------------------------------------------------ memory

    private fun rememberMemory(ctx: ToolContext) = ToolSpec(
        id = "remember_memory",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "remember_memory",
            description = "احفظ حقيقة عن المستخدم ليستدل بها لاحقًا (اسمه، تفضيلاته، عاداته، عمله). استخدمها عندما يقول «تذكر...» أو يشاركك معلومة شخصية مهمة.",
            schema = Schema.object_(
                "fact" to Schema.string("الحقيقة بصيغة جملة قصيرة"),
                "tags" to Schema.array(Schema.string("وسم"), "وسوم (اختياري)"),
                required = listOf("fact"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val fact = args.str("fact")?.trim().orEmpty()
            if (fact.isBlank()) return@ToolHandler ToolOutput.fail("ما الذي أحفظه؟")
            val tags = (args["tags"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?.joinToString(",") ?: ""
            ctx.db.memoryDao().insert(app.majwuda.data.db.MemoryEntity(text = fact, tags = tags, createdAt = System.currentTimeMillis()))
            ToolOutput.ok("حفظت في ذاكرتي: $fact", speak = "تم الحفظ في الذاكرة")
        },
    )

    private fun recallMemories(ctx: ToolContext) = ToolSpec(
        id = "recall_memories",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "recall_memories",
            description = "ابحث في ذاكرة مجودة عن حقائق محفوظة عن المستخدم.",
            schema = Schema.object_(
                "query" to Schema.string("العبارة للبحث، أو فارغة لعرض آخر ما حُفظ"),
                required = emptyList(),
            ),
        ),
        handler = ToolHandler { _, args ->
            val query = args.str("query")?.trim().orEmpty()
            val items = if (query.isBlank()) ctx.db.memoryDao().all().take(15) else ctx.db.memoryDao().search(query)
            if (items.isEmpty()) {
                ToolOutput.ok(if (query.isBlank()) "ذاكرتي فارغة حتى الآن." else "لا أذكر شيئًا يطابق «$query».")
            } else {
                ToolOutput.ok(
                    "ما أتذكره:\n" + items.joinToString("\n") { "- ${it.text}" },
                    card = ToolCard.ListItems("الذاكرة", items.map { CardRow(it.text, null, tagsSub(it.tags)) }),
                    toolResultForModel = items.joinToString("\n") { "#${it.id} ${it.text}" },
                )
            }
        },
    )

    private fun forgetMemory(ctx: ToolContext) = ToolSpec(
        id = "forget_memory",
        gates = emptySet(),
        risk = ToolRisk.DESTRUCTIVE,
        confirmationPrompt = { args -> "هل أحذف الذكر رقم ${args.str("id")} نهائيًا؟" },
        definition = ToolDefinition(
            name = "forget_memory",
            description = "احذف حقيقة من ذاكرة مجودة بمعرّفها.",
            schema = Schema.object_(
                "id" to Schema.integer("معرّف الذكر"),
                required = listOf("id"),
            ),
            destructive = true,
        ),
        handler = ToolHandler { _, args ->
            val id = args.long("id") ?: return@ToolHandler ToolOutput.fail("المعرّف غير واضح.")
            ctx.db.memoryDao().deleteById(id)
            ToolOutput.ok("نسيت ذلك.", speak = "تم")
        },
    )
}
