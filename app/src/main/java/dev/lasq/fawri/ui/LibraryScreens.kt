package dev.lasq.fawri.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import dev.lasq.fawri.core.*
import dev.lasq.fawri.data.*
import java.text.DateFormat
import java.util.Date

@Composable
fun ClipLine(a: MainActivity, vm: AppModel, c: Clip) {
    val p = Colors.current
    var menu by remember(c.id) { mutableStateOf(false) }
    var dialog by remember(c.id) { mutableStateOf("") }
    val kind = Kind.entries.firstOrNull { it.name == c.kind } ?: Kind.TEXT
    val loc = LocaleHere.current
    val time =
        remember(c.time, loc) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, loc).format(Date(c.time))
        }
    Row(
        Modifier.fillMaxWidth()
            .clickable { vm.copy(a, c.body) }
            .padding(start = 13.dp, end = 2.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TileIcon(
            when (kind) {
                Kind.LINK -> G.LINK
                Kind.NUMBER -> G.HASH
                Kind.TEXT -> G.TEXT
            },
            green = kind == Kind.LINK,
            size = 36,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Txt(c.body, 14, bold = true, lines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.pinned) {
                    Glyph(G.PIN, Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Txt("${c.source}  ·  $time", 11, p.muted, lines = 2)
            }
            Group.entries
                .firstOrNull { it.name == c.bucket && it != Group.NONE }
                ?.let { Txt(it.label, 11, p.purple) }
        }
        Box {
            IconAction(G.MORE, "خيارات العنصر") { menu = true }
            DropdownMenu(menu, { menu = false }, containerColor = p.paper) {
                listOf(
                        "عرض النص" to G.TEXT,
                        (if (c.pinned) "إلغاء التثبيت" else "تثبيت") to G.PIN,
                        "نقل إلى مجموعة" to G.FOLDER,
                        "حذف" to G.BIN,
                    )
                    .forEachIndexed { i, (s, g) ->
                        DropdownMenuItem(
                            text = { Txt(s, 14) },
                            leadingIcon = { Glyph(g, Modifier.size(17.dp)) },
                            onClick = {
                                menu = false
                                when (i) {
                                    0 -> dialog = "view"
                                    1 -> vm.pin(c)
                                    2 -> dialog = "group"
                                    3 -> dialog = "delete"
                                }
                            },
                        )
                    }
            }
        }
    }
    when (dialog) {
        "group" ->
            Pick(
                "مجموعة العنصر",
                Group.entries.map { it.label },
                Group.entries.indexOfFirst { it.name == c.bucket },
                { dialog = "" },
            ) {
                vm.group(c, Group.entries[it])
            }
        "delete" ->
            Confirm(
                "حذف العنصر؟",
                "سيُزال هذا العنصر من السجل المحلي.",
                "حذف",
                { dialog = "" },
                danger = true,
            ) {
                dialog = ""
                vm.delete(c)
            }
        "view" ->
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Txt("تفاصيل العنصر", 20, bold = true) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Txt("${kind.label}  ·  ${c.source}\n$time", 12, p.muted)
                        Spacer(Modifier.height(12.dp))
                        Txt(c.body)
                    }
                },
                confirmButton = {
                    TextButton(
                        {
                            dialog = ""
                            vm.copy(a, c.body)
                        },
                    ) {
                        Txt("نسخ النص", color = p.purple)
                    }
                },
                dismissButton = { TextButton({ dialog = "" }) { Txt("إغلاق") } },
            )
    }
}

@Composable
fun History(a: MainActivity, vm: AppModel, lib: Library) {
    val q by vm.query.collectAsState()
    val p = Colors.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Title("سجل الحافظة", "اضغط على أي عنصر لنسخه مجدداً.") }
                Badge("${n(lib.total)} عنصر")
            }
        }
        item {
            SearchBox(
                q.text,
                { vm.query.value = q.copy(text = it.take(160), limit = 40) },
                "ابحث في النصوص أو اسم التطبيق…",
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                listOf("الكل", "المثبّتة", "شغل", "شخصي").forEachIndexed { i, s ->
                    val group =
                        when (i) {
                            2 -> Group.WORK.name
                            3 -> Group.PERSONAL.name
                            else -> ""
                        }
                    FilterChip(
                        selected = if (i == 1) q.pins else !q.pins && q.group == group,
                        onClick = { vm.query.value = q.copy(pins = i == 1, group = group, limit = 40) },
                        label = { Txt(s, 13) },
                        shape = CircleShape,
                    )
                }
            }
        }
        when {
            lib.error -> item { Panel { LibraryError(vm) } }
            !lib.loaded -> item { Loading() }
            lib.rows.isEmpty() ->
                item {
                    Panel {
                        Empty(
                            if (lib.total > 0) G.SEARCH else G.BOX,
                            if (lib.total > 0) "لا توجد نتائج مطابقة" else "السجل فارغ الآن",
                            if (lib.total > 0) "جرّب كلمة أقصر أو غيّر المجموعة."
                            else "أضف نصاً يدوياً أو شغّل المراقبة في تطبيقاتك المختارة.",
                        )
                        if (lib.total == 0)
                            Box(
                                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton({ vm.importClipboard(a) }) {
                                    Txt("إضافة من الحافظة", 14, p.purple, true)
                                }
                            }
                    }
                }
            else -> items(lib.rows, key = { it.id }) { Panel { ClipLine(a, vm, it) } }
        }
        if (lib.more)
            item {
                TextButton({ vm.query.value = q.copy(limit = q.limit + 40) }, Modifier.fillMaxWidth()) {
                    Txt("عرض المزيد", color = p.purple)
                }
            }
    }
}

@Composable
fun Applications(vm: AppModel, o: Options) {
    val list by vm.apps.collectAsState()
    var text by remember { mutableStateOf("") }
    var change by remember { mutableStateOf(false) }
    val p = Colors.current
    LaunchedEffect(Unit) { vm.loadApps() }
    val checked = if (o.allowList) o.allowed else o.denied
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { Title("نطاق التطبيقات", "تطبّق هذه القواعد على النسخ واللصق معاً.") }
        item {
            Panel {
                LineItem(
                    G.SHIELD,
                    if (o.allowList) "المسموح بها فقط" else "الكل عدا المستبعدة",
                    if (o.allowList) "اختر تطبيق المصدر وتطبيق الوجهة أدناه."
                    else "حدّد التطبيقات التي تريد استبعادها.",
                    { change = true },
                    green = true,
                )
            }
        }
        item { SearchBox(text, { text = it.take(100) }, "ابحث عن تطبيق مثبت…") }
        item {
            Section(if (o.allowList) "التطبيقات المسموحة" else "التطبيقات المستبعدة")
            Txt(
                "الحقول السرية والتطبيقات الحساسة المعروفة محجوبة دائماً. أضف يدوياً ما لم نتعرّف عليه.",
                12,
                p.muted,
            )
        }
        val rows = list?.filter { it.title.contains(text, true) || it.pkg.contains(text, true) }
        if (rows == null) item { Loading() }
        else if (rows.isEmpty())
            item {
                Panel {
                    Empty(
                        G.GRID,
                        "لم نعثر على تطبيقات",
                        "نعرض التطبيقات ذات أيقونة تشغيل على هذا الجهاز.",
                    )
                }
            }
        else
            items(rows, key = { it.pkg }) { r ->
                val mark = r.pkg in checked
                fun flip() {
                    if (!r.restricted)
                        vm.settings { s ->
                            val values = if (mark) checked - r.pkg else checked + r.pkg
                            if (s.allowList) s.copy(allowed = values) else s.copy(denied = values)
                        }
                }
                Panel {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !r.restricted) { flip() }
                            .padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(38.dp)
                                .background(
                                    if (r.restricted) p.mist else p.lilac,
                                    RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (r.restricted) Glyph(G.LOCK, Modifier.size(17.dp), p.green)
                            else Txt(r.title.take(1), 18, p.purple, true)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(r.title, 14, bold = true, lines = 1)
                            Txt(if (r.restricted) "محجوب للحماية" else r.pkg, 11, p.muted, lines = 1)
                        }
                        Checkbox(
                            mark && !r.restricted,
                            onCheckedChange = { flip() },
                            enabled = !r.restricted,
                        )
                    }
                }
            }
    }
    if (change)
        Pick(
            "سياسة التطبيقات",
            listOf("السماح للمحددة فقط (موصى به)", "السماح للكل عدا المستبعدة"),
            if (o.allowList) 0 else 1,
            { change = false },
        ) {
            vm.settings { s -> s.copy(allowList = it == 0) }
        }
}

private val examples =
    listOf(
        Snippet(-1, "رد سريع", "شكراً لك، وصلتني رسالتك وسأعود إليك قريباً."),
        Snippet(-2, "تأكيد موعد", "أؤكد حضوري في الموعد المتفق عليه. إلى اللقاء!"),
    )

@Composable
fun Templates(a: MainActivity, vm: AppModel, lib: Library) {
    var add by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Snippet?>(null) }
    val p = Colors.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Title("رفّ القوالب", "عبارات تستخدمها كثيراً، جاهزة للنسخ بنقرة.") }
        item { Primary("قالب جديد", { add = true }, icon = G.PLUS) }
        item { Section("قوالبي") }
        if (lib.error) item { Panel { LibraryError(vm) } }
        else if (!lib.loaded) item { Loading() }
        else if (lib.templates.isEmpty())
            item {
                Panel {
                    Empty(G.STACK, "مكان لعباراتك المفضلة", "أنشئ قالباً واحفظه في الخزنة المشفّرة.")
                }
            }
        items(lib.templates, key = { it.id }) { s ->
            Panel {
                LineItem(
                    G.TEXT,
                    s.title,
                    s.body.take(100),
                    { vm.copy(a, s.body) },
                    tail = { IconAction(G.BIN, "حذف القالب") { removing = s } },
                )
            }
        }
        item { Section("قوالب جاهزة") }
        items(examples) { s ->
            Panel { LineItem(G.COPY, s.title, s.body, { vm.copy(a, s.body) }, green = true) }
        }
    }
    if (add) {
        var t by remember { mutableStateOf("") }
        var b by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Txt("إنشاء قالب", 21, bold = true) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        t,
                        { t = it.take(80) },
                        label = { Txt("اسم القالب", 13) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        b,
                        { b = it.take(MAX_TEXT) },
                        label = { Txt("نص القالب", 13) },
                        minLines = 3,
                        maxLines = 6,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    {
                        add = false
                        vm.addSnippet(t, b)
                    },
                    enabled = t.isNotBlank() && b.isNotBlank(),
                ) {
                    Txt("حفظ", color = LocalContentColor.current)
                }
            },
            dismissButton = { TextButton({ add = false }) { Txt("إلغاء", color = p.muted) } },
        )
    }
    removing?.let {
        Confirm("حذف القالب؟", it.title, "حذف", { removing = null }, danger = true) {
            vm.removeSnippet(it)
            removing = null
        }
    }
}
