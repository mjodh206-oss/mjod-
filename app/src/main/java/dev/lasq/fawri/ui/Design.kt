package dev.lasq.fawri.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import dev.lasq.fawri.R
import dev.lasq.fawri.core.Theme
import java.text.NumberFormat
import java.util.Locale

val Plex =
    FontFamily(
        Font(R.font.plex_arabic, FontWeight.Normal),
        Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    )

data class Ink(
    val canvas: Color,
    val paper: Color,
    val text: Color,
    val muted: Color,
    val stroke: Color,
    val purple: Color,
    val lilac: Color,
    val green: Color,
    val mist: Color,
    val red: Color,
)

private fun c(n: Long) = Color(n)

private val daylight =
    Ink(
        c(0xfff7f7fb),
        Color.White,
        c(0xff24243a),
        c(0xff64677e),
        c(0xffe4e4ee),
        c(0xff6248c7),
        c(0xffeeebfc),
        c(0xff18775d),
        c(0xffe3f5ee),
        c(0xffad374c),
    )
private val night =
    Ink(
        c(0xff15151e),
        c(0xff20212d),
        c(0xfff3f1fb),
        c(0xffb4b4cb),
        c(0xff363748),
        c(0xffc2b3ff),
        c(0xff38304f),
        c(0xff8cdebd),
        c(0xff203c32),
        c(0xffff9aaa),
    )
val Colors = staticCompositionLocalOf { daylight }
val LocaleHere = staticCompositionLocalOf { Locale.forLanguageTag("ar") }

@Composable fun n(value: Number): String = NumberFormat.getInstance(LocaleHere.current).format(value)

@Composable
fun LasqTheme(t: Theme, locale: String, content: @Composable () -> Unit) {
    val dark = t == Theme.DARK || (t == Theme.SYSTEM && isSystemInDarkTheme())
    val p = if (dark) night else daylight
    val scheme =
        (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = p.purple,
            onPrimary = if (dark) p.canvas else Color.White,
            secondary = p.green,
            surface = p.paper,
            onSurface = p.text,
            surfaceVariant = p.lilac,
            onSurfaceVariant = p.muted,
            background = p.canvas,
            onBackground = p.text,
            outline = p.stroke,
            error = p.red,
        )
    CompositionLocalProvider(
        Colors provides p,
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocaleHere provides Locale.forLanguageTag(locale),
    ) {
        MaterialTheme(colorScheme = scheme) {
            ProvideTextStyle(TextStyle(fontFamily = Plex, color = p.text, fontSize = 15.sp)) {
                content()
            }
        }
    }
}

@Composable
fun Txt(
    s: String,
    size: Int = 15,
    color: Color = Colors.current.text,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
    lines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        s,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        lineHeight = (size * 1.49).sp,
        fontFamily = Plex,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

enum class G(val path: String) {
    HOME("M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"),
    HISTORY("M3 11a9 9 0 1 1 1.8 6.5M3 4v6h6M12 7v5l3 2"),
    GRID("M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z"),
    TUNE("M3 6h5m4 0h9M3 12h10m4 0h4M3 18h3m4 0h11M8 3v6m5 0v6m-7 0v6"),
    SHIELD("M12 2l8 3v6c0 5-3 8-8 11-5-3-8-6-8-11V5zM8.5 11.8l2.3 2.3 4.8-5"),
    BOLT("M14 2L4 14h7l-1 8 10-13h-7z"),
    COPY("M8 8h13v13H8zM16 5V3H3v13h2"),
    CLIP("M8 5H5v16h14V5h-3M9 3h6v4H9zM8 13h8m-8 3h5"),
    STACK("M12 3l10 5-10 5L2 8zM3 12l9 5 9-5M3 16l9 5 9-5"),
    LEFT("M19 12H5m6-6-6 6 6 6"),
    RIGHT("M5 12h14m-6-6 6 6-6 6"),
    CHEV("M14.5 5.5L8 12l6.5 6.5"),
    LOCK("M5 10h14v11H5zM8 10V7a4 4 0 0 1 8 0v3M12 14v3"),
    KEY("M14 4a5 5 0 1 1-1 8L5 20H2v-3l8-8a5 5 0 0 1 4-5zM16 7h.1"),
    PIN("M8 3h8l-1 6 3 4v2H6v-2l3-4zM12 15v7"),
    CLOCK("M21 12a9 9 0 1 1-18 0 9 9 0 1 1 18 0zM12 7v5l4 2"),
    LINK(
        "M9 15l6-6M13 6l2-2a4.3 4.3 0 0 1 6 6l-4 4a4.3 4.3 0 0 1-6 0M11 18l-2 2a4.3 4.3 0 0 1-6-6l4-4a4.3 4.3 0 0 1 6 0",
    ),
    TEXT("M3 5h18M3 10h13M3 15h18M3 20h9"),
    HASH("M8 3L5 21m13-18-3 18M3 9h18M2 15h18"),
    MORE(
        "M5 11a1 1 0 1 1 0 2 1 1 0 0 1 0-2zM12 11a1 1 0 1 1 0 2 1 1 0 0 1 0-2zM19 11a1 1 0 1 1 0 2 1 1 0 0 1 0-2z",
    ),
    BIN("M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"),
    SEARCH("M17 10a7 7 0 1 1-14 0 7 7 0 1 1 14 0zm-2 5 6 6"),
    CHECK("M4 12l5 5L20 6"),
    PLUS("M12 4v16M4 12h16"),
    CLOSE("M5 5l14 14M19 5L5 19"),
    BOX("M6 4h12l4 11v5H2v-5zM3 15h5l2 3h4l2-3h5"),
    FOLDER("M3 5h7l2 3h9v12H3z"),
    BELL("M3 17l3-4V9a6 6 0 0 1 12 0v4l3 4zM9 20a3 3 0 0 0 6 0"),
    BATTERY("M2 6h17v12H2zM22 10v4M6 10v4m4-4v4"),
    BOARD("M2 5h20v14H2zM5 9h1m3 0h1m3 0h1m3 0h1M5 12h1m3 0h1m3 0h1m3 0h1M8 16h8"),
    INFO("M21 12a9 9 0 1 1-18 0 9 9 0 1 1 18 0zM12 11v6m0-10v.2"),
    MOON("M20 15A9 9 0 0 1 9 4a9 9 0 1 0 11 11z"),
    LANG("M3 4h12M9 2v2M5 7c1 4 4 7 8 9M13 4c-1 6-4 10-10 13M14 21l4-10 4 10m-7-3h6"),
    DOWNLOAD("M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"),
    UPLOAD("M12 16V4m-5 5 5-5 5 5M4 16v5h16v-5"),
    MAIL("M2 5h20v14H2zM3 6l9 7 9-7"),
    HAND(
        "M8 12V5a2 2 0 0 1 4 0v6-8a2 2 0 0 1 4 0v9-5a2 2 0 0 1 4 0v8c0 4-2 7-6 7h-2c-2 0-4-1-5-3l-4-6a2 2 0 0 1 3-2l2 2z",
    ),
}

@Composable
fun Glyph(
    g: G,
    modifier: Modifier = Modifier.size(21.dp),
    color: Color = Colors.current.purple,
    label: String? = null,
) {
    val path = remember(g) { PathParser().parsePathString(g.path).toPath() }
    Canvas(if (label != null) modifier.semantics { contentDescription = label } else modifier) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            drawPath(path, color, style = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun TileIcon(g: G, green: Boolean = false, size: Int = 40) {
    val p = Colors.current
    Box(
        Modifier.size(size.dp)
            .background(if (green) p.mist else p.lilac, RoundedCornerShape((size * .30f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(g, Modifier.size((size * .49f).dp), if (green) p.green else p.purple)
    }
}

@Composable
fun Logo(size: Int = 42) {
    Canvas(Modifier.size(size.dp)) {
        scale(this.size.width / 48, this.size.height / 48, Offset.Zero) {
            drawRoundRect(
                Brush.linearGradient(
                    listOf(c(0xff8b73e9), c(0xff5942bb)),
                    Offset.Zero,
                    Offset(48f, 48f),
                ),
                Offset.Zero,
                Size(48f, 48f),
                CornerRadius(14f),
            )
            drawRoundRect(c(0xffbaaef4), Offset(10f, 15f), Size(21f, 24f), CornerRadius(4f))
            drawRoundRect(Color.White, Offset(17f, 9f), Size(21f, 24f), CornerRadius(4f))
            val bolt =
                Path().apply {
                    moveTo(29f, 12f)
                    lineTo(21f, 23f)
                    lineTo(27f, 23f)
                    lineTo(25f, 30f)
                    lineTo(34f, 18f)
                    lineTo(28f, 18f)
                    close()
                }
            drawPath(bolt, c(0xff6651c6))
            drawCircle(c(0xff8ff0c9), 3.1f, Offset(11f, 37f))
        }
    }
}

@Composable
fun IconAction(g: G, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.semantics { contentDescription = label }) {
        Glyph(g, color = Colors.current.muted)
    }
}

@Composable
fun Badge(s: String, green: Boolean = false) {
    val p = Colors.current
    Box(
        Modifier.background(if (green) p.mist else p.lilac, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Txt(s, 11, if (green) p.green else p.purple, true)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = Colors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.paper)
            .border(1.dp, p.stroke, RoundedCornerShape(20.dp)),
        content = content,
    )
}

@Composable
fun Title(s: String, body: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt(s, 29, bold = true)
        if (body.isNotEmpty()) Txt(body, 14, Colors.current.muted)
    }
}

@Composable
fun Section(s: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Txt(s, 18, bold = true, modifier = Modifier.weight(1f))
        if (action != null)
            TextButton(onClick) {
                Txt(action, 13, Colors.current.purple, true)
                Spacer(Modifier.width(3.dp))
                Glyph(G.CHEV, Modifier.size(12.dp))
            }
    }
}

@Composable
fun Primary(
    s: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    icon: G? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(15.dp),
    ) {
        Txt(s, 16, LocalContentColor.current, true)
        if (icon != null) {
            Spacer(Modifier.width(10.dp))
            Glyph(icon, Modifier.size(18.dp), LocalContentColor.current)
        }
    }
}

@Composable
fun LineItem(
    g: G,
    title: String,
    detail: String = "",
    onClick: (() -> Unit)? = null,
    green: Boolean = false,
    tail: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TileIcon(g, green, 36)
        Column(Modifier.weight(1f)) {
            Txt(title, 15, bold = true)
            if (detail.isNotEmpty()) Txt(detail, 12, Colors.current.muted)
        }
        if (tail != null) tail()
        else if (onClick != null) Glyph(G.CHEV, Modifier.size(15.dp), Colors.current.muted)
    }
}

@Composable
fun RuleLine() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Colors.current.stroke)
}

@Composable
fun Empty(g: G, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TileIcon(g, size = 48)
        Spacer(Modifier.height(10.dp))
        Txt(title, 16, bold = true, align = TextAlign.Center)
        Txt(body, 13, Colors.current.muted, align = TextAlign.Center)
    }
}

@Composable
fun SearchBox(s: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        s,
        onChange,
        Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Glyph(G.SEARCH, color = Colors.current.muted) },
        trailingIcon = { if (s.isNotEmpty()) IconAction(G.CLOSE, "مسح البحث") { onChange("") } },
        placeholder = { Txt(hint, 14, Colors.current.muted) },
        shape = RoundedCornerShape(16.dp),
        textStyle = TextStyle(fontFamily = Plex, fontSize = 15.sp, color = Colors.current.text),
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Colors.current.paper,
                unfocusedBorderColor = Colors.current.stroke,
            ),
    )
}

@Composable
fun Confirm(
    title: String,
    body: String,
    action: String,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Txt(title, 21, bold = true) },
        text = { Txt(body, 15, Colors.current.muted) },
        confirmButton = {
            TextButton(onConfirm) {
                Txt(action, 15, if (danger) Colors.current.red else Colors.current.purple, true)
            }
        },
        dismissButton = { TextButton(onDismiss) { Txt("إلغاء", 15, Colors.current.muted) } },
        containerColor = Colors.current.paper,
    )
}

@Composable
fun Pick(
    title: String,
    items: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Txt(title, 21, bold = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { i, s ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                onPick(i)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected == i, null)
                        Spacer(Modifier.width(12.dp))
                        Txt(s)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Txt("إغلاق", color = Colors.current.purple) } },
        containerColor = Colors.current.paper,
    )
}

/** Hand-drawn vector illustration: paper stack, binder, mint lightning seal. Scales without bitmaps. */
@Composable
fun PaperArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = minOf(size.width / 180, size.height / 160)
        withTransform(
            {
                translate((size.width - 180 * k) / 2, (size.height - 160 * k) / 2)
                scale(k, k, Offset.Zero)
            },
        ) {
            val violet = c(0xff6654b4)
            val pale = c(0xffc4b9f4)
            val navy = c(0xff352c62)
            drawCircle(Color.White.copy(.065f), 71f, Offset(85f, 82f))
            drawCircle(Color.White.copy(.14f), 69f, Offset(85f, 82f), style = Stroke(.8f))
            drawCircle(Color.White.copy(.11f), 80f, Offset(85f, 82f), style = Stroke(.8f))
            rotate(-18f, Offset(76f, 90f)) {
                drawRoundRect(violet, Offset(39f, 39f), Size(79f, 104f), CornerRadius(10f))
                drawRoundRect(
                    pale.copy(.45f),
                    Offset(39f, 39f),
                    Size(79f, 104f),
                    CornerRadius(10f),
                    style = Stroke(1.5f),
                )
            }
            rotate(8f, Offset(95f, 78f)) {
                drawRoundRect(navy.copy(.5f), Offset(58f, 35f), Size(84f, 108f), CornerRadius(11f))
                drawRoundRect(
                    Brush.linearGradient(
                        listOf(Color.White, c(0xffded7ff)),
                        Offset(48f, 27f),
                        Offset(130f, 139f),
                    ),
                    Offset(53f, 28f),
                    Size(84f, 108f),
                    CornerRadius(10f),
                )
                drawRoundRect(pale, Offset(75f, 22f), Size(38f, 15f), CornerRadius(6f))
                drawRoundRect(violet, Offset(86f, 28f), Size(16f, 3f), CornerRadius(2f))
                drawRoundRect(violet, Offset(68f, 53f), Size(37f, 5f), CornerRadius(2.5f))
                listOf(49f, 41f, 48f, 29f).forEachIndexed { i, w ->
                    drawRoundRect(pale, Offset(68f, 71f + i * 10), Size(w, 3.5f), CornerRadius(2f))
                }
            }
            rotate(-10f, Offset(133f, 119f)) {
                drawRoundRect(navy.copy(.3f), Offset(115f, 102f), Size(40f, 39f), CornerRadius(12f))
                drawRoundRect(c(0xff99edcb), Offset(112f, 98f), Size(40f, 39f), CornerRadius(12f))
                val p =
                    Path().apply {
                        moveTo(135f, 103f)
                        lineTo(122f, 120f)
                        lineTo(131f, 120f)
                        lineTo(129f, 131f)
                        lineTo(143f, 113f)
                        lineTo(134f, 113f)
                        close()
                    }
                drawPath(p, navy)
            }
            listOf(Offset(28f, 49f), Offset(150f, 47f), Offset(44f, 136f)).forEach { pt ->
                drawLine(pale, pt - Offset(3f, 0f), pt + Offset(3f, 0f), 1.3f)
                drawLine(pale, pt - Offset(0f, 3f), pt + Offset(0f, 3f), 1.3f)
            }
        }
    }
}

val heroBrush
    get() = Brush.linearGradient(listOf(c(0xff353057), c(0xff514184), c(0xff68539d)))

@Composable
fun Plot(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 16.dp.toPx()
        var x = 0f
        while (x < size.width) {
            var y = 0f
            while (y < size.height) {
                drawCircle(Color.White.copy(.12f), .8.dp.toPx(), Offset(x, y))
                y += step
            }
            x += step
        }
    }
}
