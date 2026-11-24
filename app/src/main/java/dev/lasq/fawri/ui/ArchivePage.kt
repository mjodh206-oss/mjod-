package dev.lasq.fawri.ui

import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import dev.lasq.fawri.BuildConfig
import dev.lasq.fawri.core.CREDIT
import dev.lasq.fawri.data.Preview
import dev.lasq.fawri.platform.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.collectLatest

/** The archive is the launcher. No tabs, onboarding, target-app selection, modes, or timers. */
@Composable
fun ArchivePage(a: MainActivity, vm: AppModel, locked: Boolean) {
    val p = Colors.current
    val app = vm.app
    val phase by app.recorder.state.collectAsState()
    val issue by app.recorder.problem.collectAsState()
    val lib by vm.library.collectAsState()
    val q by vm.query.collectAsState()
    val busy by vm.transferring.collectAsState()
    val enabled by app.prefs.enabled.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Preview?>(null) }
    var detail by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collectLatest { snackbar.showSnackbar(it) } }
    Box(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Logo(38)
                Spacer(Modifier.width(10.dp))
                Txt("لصق فوري", 21, bold = true, modifier = Modifier.weight(1f))
                Badge("محلي وخاص", true)
                Box {
                    IconAction(G.MORE, "خيارات السجل") { menu = true }
                    DropdownMenu(menu, { menu = false }, containerColor = p.paper) {
                        fun choose(f: () -> Unit) {
                            menu = false
                            f()
                        }
                        if (enabled)
                            DropdownMenuItem(
                                text = { Txt("إيقاف الحفظ") },
                                onClick = { choose { a.changeRecording(false) } },
                            )
                        DropdownMenuItem(
                            text = {
                                Txt(if (locked) "إلغاء قفل السجل" else "قفل السجل بالبصمة أو الرمز")
                            },
                            onClick = { choose { a.authenticate { app.prefs.lock(!locked) } } },
                        )
                        DropdownMenuItem(
                            text = { Txt("تصدير نسخة مشفّرة") },
                            enabled = !busy,
                            onClick = { choose { a.pickFile(true) } },
                        )
                        DropdownMenuItem(
                            text = { Txt("استيراد نسخة مشفّرة") },
                            enabled = !busy,
                            onClick = { choose { a.pickFile(false) } },
                        )
                        DropdownMenuItem(
                            text = { Txt("عن السجل") },
                            onClick = { choose { about = true } },
                        )
                        DropdownMenuItem(
                            text = { Txt("مسح السجل كله", color = p.red) },
                            enabled = lib.total > 0 && !busy,
                            onClick = { choose { wipe = true } },
                        )
                    }
                }
            }
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 25.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Title("سجل النسخ", "نصوصك هنا، حتى تحذفها أنت.") }
                item { StatusCard(a, phase) }
                issue?.let { message -> item { Txt(message, 13, p.red) } }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Txt("النصوص المحفوظة", 18, bold = true)
                        Spacer(Modifier.width(7.dp))
                        Badge(n(lib.total))
                        Spacer(Modifier.weight(1f))
                        if (lib.total > 0 || search)
                            IconAction(
                                if (search) G.CLOSE else G.SEARCH,
                                if (search) "إغلاق البحث" else "بحث في النصوص",
                            ) {
                                search = !search
                                vm.query.value = Query()
                            }
                    }
                }
                if (search)
                    item {
                        SearchBox(q.term, { vm.query.value = Query(it.take(160)) }, "ابحث في سجلك…")
                    }
                when {
                    lib.failed ->
                        item {
                            Panel {
                                LineItem(
                                    G.LOCK,
                                    "تعذّر فتح السجل المشفّر",
                                    "لم نحذف أي نص. اضغط لإعادة المحاولة.",
                                    { app.vault.revision.value++ },
                                )
                            }
                        }
                    !lib.loaded ->
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        }
                    lib.items.isEmpty() ->
                        item {
                            Panel {
                                Empty(
                                    if (q.term.isEmpty()) G.BOX else G.SEARCH,
                                    if (q.term.isEmpty()) "لا نصوص محفوظة بعد" else "لا توجد نتائج",
                                    if (q.term.isEmpty())
                                        "عند حفظ أول نص سيظهر هنا. لا تحتاج إلى الضغط على لصق."
                                    else "جرّب البحث بكلمة أخرى.",
                                )
                            }
                        }
                    else ->
                        items(lib.items, key = { it.id }) { row ->
                            Entry(
                                row,
                                { vm.text(row.id) { detail = row.id to it } },
                                { vm.copy(row.id) },
                                { removing = row },
                            )
                        }
                }
                if (lib.more)
                    item {
                        TextButton(
                            { vm.query.value = q.copy(limit = q.limit + 40) },
                            Modifier.fillMaxWidth(),
                        ) {
                            Txt("عرض المزيد", color = p.purple)
                        }
                    }
                item {
                    Txt(
                        CREDIT,
                        12,
                        p.muted,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        align = TextAlign.Center,
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
            Snackbar(containerColor = p.text) { Txt(it.visuals.message, 14, p.canvas) }
        }
    }
    removing?.let { row ->
        Confirm("حذف هذا النص؟", row.preview.take(100), "حذف", { removing = null }, danger = true) {
            vm.remove(row.id)
            removing = null
        }
    }
    if (wipe)
        Confirm(
            "مسح كل النصوص؟",
            "سيبقى السجل فارغاً حتى تنسخ نصاً جديداً. لا يمكن التراجع عن المسح.",
            "مسح الكل",
            { wipe = false },
            danger = true,
        ) {
            wipe = false
            vm.clear()
        }
    detail?.let { (id, body) ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Txt("النص المحفوظ", 20, bold = true) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Txt(body) } },
            confirmButton = {
                TextButton(
                    {
                        detail = null
                        vm.copy(id)
                    },
                ) {
                    Txt("نسخ النص", color = p.purple)
                }
            },
            dismissButton = { TextButton({ detail = null }) { Txt("إغلاق") } },
        )
    }
    if (about)
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Txt("عن لصق فوري", 21, bold = true) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Badge("الإصدار ${BuildConfig.VERSION_NAME}")
                    Txt(
                        "يحفظ التطبيق النص كما نُسخ، داخل سجل مشفّر على جهازك. لا إنترنت ولا حذف تلقائي ولا لصق في تطبيقات أخرى.",
                        14,
                    )
                    Txt(
                        "على أندرويد ١٠ فأحدث، لا يستطيع تطبيق عادي رصد كل النسخ في الخلفية. باختيار لوحة الحفظ، يسمح النظام بالحفظ عند النسخ. بلوحتك المعتادة يُحفظ آخر نص متاح عند فتح السجل فقط.",
                        14,
                    )
                    Txt(
                        "لا نحفظ ضغطات المفاتيح أو المحتوى المعلّم حسّاساً. بعض التطبيقات لا تعلّم الأسرار؛ يمكنك إيقاف الحفظ من القائمة. حذف التطبيق أو بياناته يزيل السجل.",
                        14,
                    )
                    if (app.integrity.second)
                        Txt("رصدنا مؤشرات روت؛ قد تكون حماية الجهاز أضعف.", 13, p.red)
                    Txt(CREDIT, 13, p.muted)
                }
            },
            confirmButton = { TextButton({ about = false }) { Txt("تم", color = p.purple) } },
        )
}

@Composable
private fun StatusCard(a: MainActivity, phase: Recorder.State) {
    val p = Colors.current
    if (phase == Recorder.State.OFF) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(heroBrush)) {
            Plot(Modifier.matchParentSize())
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.6f)) {
                    Txt("انسخ فقط.", 24, Color.White, true)
                    Txt("احفظ النص الحالي وما تنسخه بعده في سجلك الخاص.", 14, Color(0xffe9e0ff))
                    Spacer(Modifier.height(10.dp))
                    Button(
                        { a.changeRecording(true) },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xffb1eed5),
                                contentColor = Color(0xff273c32),
                            ),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Txt("بدء الحفظ", 15, LocalContentColor.current, true)
                    }
                }
                PaperArt(Modifier.weight(1f).height(122.dp))
            }
        }
        return
    }
    val instant = phase == Recorder.State.INSTANT
    Column(
        Modifier.fillMaxWidth()
            .background(if (instant) p.mist else p.lilac, RoundedCornerShape(18.dp))
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(
                if (instant) G.CHECK else G.CLIP,
                Modifier.size(19.dp),
                if (instant) p.green else p.purple,
            )
            Spacer(Modifier.width(7.dp))
            Txt(
                if (instant) "الحفظ الفوري يعمل" else "الحفظ عند فتح السجل",
                16,
                if (instant) p.green else p.purple,
                true,
            )
        }
        if (instant) Txt("انسخ نصاً من تطبيق آخر، وستجده هنا تلقائياً.", 13, p.green)
        else if (Build.VERSION.SDK_INT >= 29) {
            val available =
                remember(a.epoch) {
                    a.system<InputMethodManager>().enabledInputMethodList.any {
                        it.packageName == a.packageName
                    }
                }
            Txt(
                "نحفظ آخر نص عند عودتك. لحفظ كل نسخة فوراً دون فتح السجل، فعّل لوحة الحفظ واخترها مرة واحدة.",
                13,
                p.text,
            )
            TextButton(a::chooseBoard, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Txt(if (available) "اختيار لوحة الحفظ" else "تفعيل لوحة الحفظ", 14, p.purple, true)
                Spacer(Modifier.width(5.dp))
                Glyph(G.LEFT, Modifier.size(15.dp))
            }
        } else Txt("يُحفظ آخر نص متاح عند فتح التطبيق.", 13, p.muted)
    }
}

@Composable
private fun Entry(row: Preview, open: () -> Unit, copy: () -> Unit, remove: () -> Unit) {
    val p = Colors.current
    val locale = LocaleHere.current
    val date =
        remember(row.time, locale) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
                .format(Date(row.time))
        }
    Panel {
        Column(
            Modifier.fillMaxWidth()
                .clickable(onClick = open)
                .padding(start = 15.dp, end = 15.dp, top = 13.dp),
        ) {
            Txt(row.preview, 16, lines = 3)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 15.dp, end = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(G.CLOCK, Modifier.size(12.dp), p.muted)
            Spacer(Modifier.width(5.dp))
            Txt(date, 11, p.muted, modifier = Modifier.weight(1f))
            IconAction(G.COPY, "نسخ العنصر", copy)
            IconAction(G.BIN, "حذف العنصر", remove)
        }
    }
}
