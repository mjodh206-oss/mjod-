package dev.lasq.fawri.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import dev.lasq.fawri.core.*
import dev.lasq.fawri.platform.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun Intro(finish: (Boolean) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val p = Colors.current
    BackHandler(step > 0) { step-- }
    Column(
        Modifier.fillMaxSize()
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Logo(38)
            Spacer(Modifier.width(9.dp))
            Txt("لصق فوري", 20, bold = true, modifier = Modifier.weight(1f))
            TextButton({ finish(false) }) { Txt("استكشف التطبيق", 13, p.muted) }
        }
        Spacer(Modifier.height(26.dp))
        Column {
            Box(
                Modifier.fillMaxWidth()
                    .height(218.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(heroBrush),
            ) {
                Plot(Modifier.matchParentSize())
                PaperArt(Modifier.align(Alignment.Center).size(206.dp, 186.dp))
                Box(
                    Modifier.padding(13.dp)
                        .background(Color.White.copy(.12f), CircleShape)
                        .padding(horizontal = 11.dp, vertical = 3.dp),
                ) {
                    Txt(listOf("اللصق الذكي", "سجل محلي", "خصوصية أولاً")[step], 11, Color.White, true)
                }
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(15.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(3) {
                        Box(
                            Modifier.size(if (it == step) 22.dp else 6.dp, 5.dp)
                                .background(
                                    if (it == step) Color(0xffa0f1d1) else Color.White.copy(.32f),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Badge("دليل البداية   /   ${n(step+1)} من ${n(3)}")
            Spacer(Modifier.height(8.dp))
            Txt(
                listOf("انسخ هنا. الصق هناك.", "ذاكرة لنصوصك وروابطك.", "أنت من يحدد الصلاحيات.")[step],
                29,
                bold = true,
            )
            Spacer(Modifier.height(6.dp))
            Txt(
                listOf(
                    "انسخ نصاً، ثم انتقل إلى حقل مختلف في تطبيق مسموح. اختر اللصق الفوري أو راجع كل عملية قبل تنفيذها.",
                    "اعثر على ما نسخته في سجل مشفّر على جهازك. ثبّت المهم، ونظّم العناصر في مجموعات، وحدّد متى تُحذف.",
                    "لا يبدأ الرصد دون موافقتك. نستخدم إمكانية الوصول للحقل النشط، وعلى أندرويد ١٠ فأحدث تحتاج المراقبة إلى لوحة اللصق الاختيارية. يمكنك الاكتفاء بالسجل اليدوي.",
                )[step],
                16,
                p.muted,
            )
        }
        Spacer(Modifier.height(28.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Primary(
                if (step == 2) "مراجعة الأذونات" else "التالي",
                { if (step == 2) finish(true) else step++ },
                icon = G.LEFT,
            )
            Spacer(Modifier.height(10.dp))
            if (step > 0) TextButton({ step-- }) { Txt("السابق", 14, p.muted) }
            else Txt(CREDIT, 12, p.muted)
        }
    }
}

@Composable
fun MainShell(a: MainActivity, vm: AppModel, o: Options, setup: Boolean, onSetupOpened: () -> Unit) {
    val stack = remember { mutableStateListOf(Screen.HOME) }
    val page = stack.last()
    val p = Colors.current
    fun nav(s: Screen) {
        if (s == page) return
        if (s in Screen.entries.take(4)) {
            stack.clear()
            stack.add(Screen.HOME)
            if (s != Screen.HOME) stack.add(s)
        } else stack.add(s)
    }
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    LaunchedEffect(setup) {
        if (setup) {
            nav(Screen.PERMISSIONS)
            onSetupOpened()
        }
    }
    BackHandler(stack.size > 1) { back() }
    val lib by vm.library.collectAsState()
    val on by vm.app.active.collectAsState()
    val queue = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.app.messages.collectLatest { queue.showSnackbar(it, duration = SnackbarDuration.Short) }
    }
    Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page in Screen.entries.take(4)) {
                Logo(35)
                Spacer(Modifier.width(9.dp))
                Txt("لصق فوري", 20, bold = true, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(CircleShape)
                        .background(p.mist)
                        .clickable { nav(Screen.PRIVACY) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.SHIELD, Modifier.size(14.dp), p.green)
                    Spacer(Modifier.width(5.dp))
                    Txt("محلي وآمن", 11, p.green, true)
                }
            } else {
                IconAction(G.RIGHT, "رجوع") { back() }
                Txt(page.label, 19, bold = true, modifier = Modifier.weight(1f))
                Logo(30)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                Screen.HOME -> Home(a, vm, o, lib, on, ::nav)
                Screen.HISTORY -> History(a, vm, lib)
                Screen.APPS -> Applications(vm, o)
                Screen.SETTINGS -> Preferences(a, vm, o, ::nav)
                Screen.PERMISSIONS -> Permissions(a, vm, o, ::nav)
                Screen.ABOUT -> About(a, ::nav)
                Screen.PRIVACY -> Privacy()
                Screen.TEMPLATES -> Templates(a, vm, lib)
            }
            SnackbarHost(queue, Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                Snackbar(
                    shape = RoundedCornerShape(14.dp),
                    containerColor = p.text,
                    contentColor = p.canvas,
                ) {
                    Txt(it.visuals.message, 14, p.canvas)
                }
            }
        }
        if (page in Screen.entries.take(4)) {
            HorizontalDivider(color = p.stroke)
            Row(
                Modifier.fillMaxWidth().background(p.paper).selectableGroup().padding(vertical = 5.dp),
            ) {
                Screen.entries.take(4).forEach { tab ->
                    val active = page == tab
                    Column(
                        Modifier.weight(1f)
                            .selectable(active, role = Role.Tab) { nav(tab) }
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(51.dp, 29.dp)
                                .background(if (active) p.lilac else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph(tab.icon, Modifier.size(20.dp), if (active) p.purple else p.muted)
                        }
                        Txt(tab.label, 11, if (active) p.purple else p.muted, active)
                    }
                }
            }
        }
    }
}

@Composable
private fun Home(
    a: MainActivity,
    vm: AppModel,
    o: Options,
    lib: Library,
    on: Boolean,
    nav: (Screen) -> Unit,
) {
    val p = Colors.current
    val reason = remember(a.epoch, o, on) { Checks.reason(a) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 19.dp, end = 19.dp, top = 9.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item { Title("لوحة التحكم", "مساحة واحدة لكل ما تنسخه.") }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(heroBrush)) {
                Plot(Modifier.matchParentSize())
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1.45f)) {
                        Row(
                            Modifier.background(Color.White.copy(.13f), CircleShape)
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(6.dp)
                                    .background(
                                        if (on) Color(0xff99ecc5) else Color(0xffded4f7),
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(6.dp))
                            Txt(if (on) "الخدمة نشطة" else "الخدمة متوقفة", 11, Color.White, true)
                        }
                        Spacer(Modifier.height(8.dp))
                        Txt("محرك اللصق", 24, Color.White, true)
                        Txt(o.mode.label, 13, Color(0xffe2d9f6))
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.heightIn(min = 48.dp).toggleable(on, role = Role.Switch) {
                                if (on) Monitor.instance?.halt(true)
                                else if (reason != null) nav(Screen.PERMISSIONS) else Monitor.start(a)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Switch(
                                on,
                                null,
                                colors =
                                    SwitchDefaults.colors(
                                        checkedTrackColor = Color(0xff92e4c0),
                                        checkedThumbColor = Color(0xff334338),
                                        uncheckedTrackColor = Color.White.copy(.16f),
                                        uncheckedBorderColor = Color.White.copy(.5f),
                                        uncheckedThumbColor = Color.White,
                                    ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Txt(if (on) "إيقاف" else "تشغيل", 14, Color.White, true)
                        }
                    }
                    PaperArt(Modifier.weight(1f).height(144.dp))
                }
            }
        }
        if (reason != null)
            item {
                Panel {
                    LineItem(
                        G.KEY,
                        "إعداد اللصق التلقائي",
                        "راجع الموافقة والتطبيقات المسموحة.",
                        { nav(Screen.PERMISSIONS) },
                    )
                }
            }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Section("أدوات سريعة")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickCard("من الحافظة", "إضافة إلى السجل", G.CLIP, false, Modifier.weight(1f)) {
                        vm.importClipboard(a)
                    }
                    QuickCard(
                        "قوالب النصوص",
                        "عبارات جاهزة للنسخ",
                        G.STACK,
                        true,
                        Modifier.weight(1f),
                    ) {
                        nav(Screen.TEMPLATES)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Section("أحدث العناصر", "كل السجل") { nav(Screen.HISTORY) }
                Panel {
                    when {
                        lib.error -> LibraryError(vm)
                        !lib.loaded -> Loading()
                        lib.recent.isEmpty() ->
                            Empty(
                                G.BOX,
                                "سجلك جاهز لأول عنصر",
                                "انسخ نصاً، ثم اختر «من الحافظة» لإضافته هنا.",
                            )
                        else ->
                            lib.recent.forEachIndexed { i, clip ->
                                if (i > 0) RuleLine()
                                ClipLine(a, vm, clip)
                            }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(G.LOCK, Modifier.size(12.dp), p.muted)
                Spacer(Modifier.width(5.dp))
                Txt("على جهازك فقط  ·  تشفير AES-256", 11, p.muted)
            }
        }
    }
}

@Composable
fun QuickCard(title: String, sub: String, g: G, green: Boolean, m: Modifier, click: () -> Unit) {
    val p = Colors.current
    Column(
        m.clip(RoundedCornerShape(18.dp))
            .background(p.paper)
            .border(1.dp, p.stroke, RoundedCornerShape(18.dp))
            .clickable(onClick = click)
            .padding(13.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileIcon(g, green, 34)
            Glyph(G.CHEV, Modifier.size(12.dp), p.muted)
        }
        Spacer(Modifier.height(9.dp))
        Txt(title, 15, bold = true)
        Txt(sub, 12, p.muted)
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
fun LibraryError(vm: AppModel) {
    LineItem(G.LOCK, "تعذّر فتح السجل المشفّر", "لم نحذف بياناتك. اضغط لإعادة المحاولة.", vm::refresh)
}
