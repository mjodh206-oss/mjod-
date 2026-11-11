package app.majwuda.tools

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.majwuda.claude.Schema
import app.majwuda.claude.ToolDefinition
import app.majwuda.core.TimeSupport
import app.majwuda.core.double
import app.majwuda.core.int
import app.majwuda.core.str
import app.majwuda.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Weather, web research, currency, calculator, unit conversion, current date/time. */
object KnowledgeTools {

    fun register(ctx: ToolContext): List<ToolSpec> = listOf(
        getWeather(ctx),
        getCurrentDatetime(ctx),
        convertTime(ctx),
        calculator(ctx),
        convertUnits(ctx),
        convertCurrency(ctx),
        webSearch(ctx),
        fetchPage(ctx),
        getDeviceInfo(ctx),
    )

    private val ARABIC = Locale("ar")

    // ------------------------------------------------------------------ weather

    private fun getWeather(ctx: ToolContext) = ToolSpec(
        id = "get_weather",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "get_weather",
            description = "الطقس الحالي (وصيغة AR) لمدينة. إذا لم تُحدّد مدينة تُستخدم آخر موقع معروف للجهاز.",
            schema = Schema.object_(
                "city" to Schema.string("اسم المدينة (اختياري — بدونها يستخدم الموقع الحالي)"),
                required = emptyList(),
            ),
        ),
        handler = ToolHandler { _, args ->
            if (!ctx.settings.snapshot().webAccessEnabled) {
                return@ToolHandler ToolOutput.fail("الوصول إلى الإنترنت معطّل من الإعدادات (الخصوصية) — فعّله لأحضر الطقس.")
            }
            var city = args.str("city")?.trim().orEmpty()
            if (city.isEmpty()) {
                city = lastKnownLocationQuery(ctx) ?: return@ToolHandler ToolOutput.fail("لم أحدد موقعك — اذكر المدينة (مثلاً: الرياض) أو فعّل الموقع.")
            }
            val snap = ctx.weather.currentWeather(city)
                ?: return@ToolHandler ToolOutput.fail("تعذر جلب الطقس لـ «$city» — تأكد من الاتصال بالإنترنت واسم المدينة.")
            val card = ToolCard.Weather(
                place = snap.place,
                temperature = "%.0f°".format(snap.temperature),
                feelsLike = snap.feelsLike?.let { "%.0f°".format(it) },
                description = snap.description,
                humidity = snap.humidity?.let { "$it%" },
                wind = snap.windKmh?.let { "%.0f كم/س".format(it) },
                precipitation = snap.precipitationMm?.let { "%.1f مـم".format(it) },
            )
            val summary = buildString {
                append("الطقس في ${snap.place}: ${snap.description}، ${"%.0f".format(snap.temperature)} درجة")
                snap.feelsLike?.let { append(" (محسوسة ${"%.0f".format(it)})") }
                snap.humidity?.let { append("، الرطوبة $it%") }
                snap.windKmh?.let { append("، الرياح ${"%.0f".format(it)} كم/س") }
                snap.high?.let { h -> snap.low?.let { l -> append(". العظمى ${"%.0f".format(h)} والصغرى ${"%.0f".format(l)}") } }
            }
            ToolOutput.ok(summary, speak = "الطقس في ${snap.place}: ${snap.description}، ${"%.0f".format(snap.temperature)} درجة", card = card, toolResultForModel = summary)
        },
    )

    private fun lastKnownLocationQuery(ctx: ToolContext): String? {
        val fine = ContextCompat.checkSelfPermission(ctx.context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx.context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        return try {
            val lm = ctx.context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return null
            val providers = listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.PASSIVE_PROVIDER)
            val loc = providers.mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null } }.maxByOrNull { it.time } ?: return null
            "%.4f,%.4f".format(loc.latitude, loc.longitude)
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ date & time

    private fun getCurrentDatetime(ctx: ToolContext) = ToolSpec(
        id = "get_current_datetime",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "get_current_datetime",
            description = "التاريخ والوقت الحالي للجهاز، أو لمنطقة زمنية محددة (مثل Asia/Riyadh).",
            schema = Schema.object_(
                "timezone" to Schema.string("معرّف المنطقة الزمنية (IANA) مثل Asia/Riyadh أو Africa/Cairo، الافتراضي توقيت الجهاز"),
                required = emptyList(),
            ),
        ),
        handler = ToolHandler { _, args ->
            val tzName = args.str("timezone")?.trim().orEmpty()
            val zone = try {
                if (tzName.isEmpty()) ZoneId.systemDefault() else ZoneId.of(tzName)
            } catch (_: Exception) {
                return@ToolHandler ToolOutput.fail("منطقة زمنية غير معروفة: $tzName")
            }
            val now = ZonedDateTime.now(zone)
            val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy، الساعة h:mm و a", ARABIC)
            val msg = "الآن: ${now.format(fmt)} (${zone.id})"
            ToolOutput.ok(msg, speak = now.format(DateTimeFormatter.ofPattern("h:mm a", ARABIC)))
        },
    )

    private fun convertTime(ctx: ToolContext) = ToolSpec(
        id = "convert_time",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "convert_time",
            description = "حوّل وقتًا من منطقة زمنية إلى أخرى (مثلاً: كم الساعة 9 مساء بتوقيت الرياض بتوقيت لندن).",
            schema = Schema.object_(
                "time" to Schema.string("الوقت بصيغة طبيعية («9 مساء») أو HH:mm"),
                "from_timezone" to Schema.string("المنطقة المصدر (IANA) مثال Asia/Riyadh"),
                "to_timezone" to Schema.string("المنطقة الهدف (IANA) مثال Europe/London"),
                required = listOf("time", "from_timezone", "to_timezone"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val fromZone = try { ZoneId.of(args.str("from_timezone")?.trim().orEmpty()) } catch (_: Exception) {
                return@ToolHandler ToolOutput.fail("المنطقة الزمنية الأولى غير معروفة.")
            }
            val toZone = try { ZoneId.of(args.str("to_timezone")?.trim().orEmpty()) } catch (_: Exception) {
                return@ToolHandler ToolOutput.fail("المنطقة الزمنية الثانية غير معروفة.")
            }
            val clock = TimeSupport.parseClockTime(args.str("time").orEmpty())
                ?: return@ToolHandler ToolOutput.fail("الوقت «${args.str("time")}» غير مفهوم.")
            val today = LocalDateTime.now(fromZone).toLocalDate()
            val at = today.atTime(clock.first, clock.second).atZone(fromZone)
            val converted = at.withZoneSameInstant(toZone)
            val fmt = DateTimeFormatter.ofPattern("h:mm a (EEEE d MMMM)", ARABIC)
            ToolOutput.ok(
                "${at.format(fmt)} في ${fromZone.id} تساوي ${converted.format(fmt)} في ${toZone.id}.",
                speak = "الساعة ${converted.format(DateTimeFormatter.ofPattern("h:mm a", ARABIC))} في ${toZone.id}",
            )
        },
    )

    // ------------------------------------------------------------------ calculator & units

    private fun calculator(ctx: ToolContext) = ToolSpec(
        id = "calculator",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "calculator",
            description = "آلة حاسبة: أي تعبير حسابي مثل (12+8)*3، sqrt(144)، 5^3، 20% من 500 تعبر عنها كـ 500*20/100.",
            schema = Schema.object_(
                "expression" to Schema.string("التعبير الرياضي بالإنجليزية (sqrt, sin, cos, tan, ln, log, abs, ^ للأسس)"),
                required = listOf("expression"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val expression = args.str("expression").orEmpty()
            return@ToolHandler try {
                val value = Calculator.evaluate(expression)
                ToolOutput.ok("${Calculator.format(value)}", speak = "الناتج ${Calculator.format(value)}", toolResultForModel = "result: ${Calculator.format(value)}")
            } catch (e: EvaluationException) {
                ToolOutput.fail("تعذر حساب «$expression»: ${e.message}")
            }
        },
    )

    private fun convertUnits(ctx: ToolContext) = ToolSpec(
        id = "convert_units",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "convert_units",
            description = "تحويل وحدات: أطوال (متر/كم/ميل/قدم)، أوزان (غرام/كيلو/باوند)، سعات (لتر/مل/جالون)، حرارة (مئوية/فهرنهايت/كلفن)، سرعة، بيانات، وقت.",
            schema = Schema.object_(
                "value" to Schema.number("القيمة المراد تحويلها"),
                "from" to Schema.string("الوحدة المصدر (مثلاً: كم، باوند، لتر، °C)"),
                "to" to Schema.string("الوحدة الهدف"),
                required = listOf("value", "from", "to"),
            ),
        ),
        handler = ToolHandler { _, args ->
            val value = args.double("value") ?: return@ToolHandler ToolOutput.fail("القيمة غير واضحة.")
            val from = args.str("from").orEmpty()
            val to = args.str("to").orEmpty()
            val tempUnits = setOf("c", "f", "k", "°c", "°f", "°k", "مئويه", "مئوية", "فهرنهايت", "كلفن", "celsius", "fahrenheit", "kelvin")
            return@ToolHandler try {
                val converted = if (from.trim().lowercase(Locale.ROOT) in tempUnits || to.trim().lowercase(Locale.ROOT) in tempUnits) {
                    UnitConverter.convertTemperature(value, from, to)
                } else {
                    UnitConverter.convert(value, from, to)
                }
                ToolOutput.ok("${Calculator.format(value)} $from = ${Calculator.format(converted)} $to", speak = "الناتج ${Calculator.format(converted)} $to")
            } catch (e: Exception) {
                ToolOutput.fail(e.message ?: "تعذر التحويل")
            }
        },
    )

    private fun convertCurrency(ctx: ToolContext) = ToolSpec(
        id = "convert_currency",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "convert_currency",
            description = "تحويل عملات بأسعار الصرف المباشرة (رموز ISO: USD EUR SAR AED KWD QAR EGP...).",
            schema = Schema.object_(
                "amount" to Schema.number("المبلغ"),
                "from" to Schema.string("عملة المصدر (USD مثلاً)"),
                "to" to Schema.string("عملة الهدف (SAR مثلاً)"),
                required = listOf("amount", "from", "to"),
            ),
        ),
        handler = ToolHandler { _, args ->
            if (!ctx.settings.snapshot().webAccessEnabled) {
                return@ToolHandler ToolOutput.fail("الوصول إلى الإنترنت معطّل — فعّله لجلب أسعار الصرف.")
            }
            val amount = args.double("amount") ?: return@ToolHandler ToolOutput.fail("المبلغ غير واضح.")
            val from = args.str("from").orEmpty().trim().uppercase(Locale.ROOT)
            val to = args.str("to").orEmpty().trim().uppercase(Locale.ROOT)
            if (from.length != 3 || to.length != 3) return@ToolHandler ToolOutput.fail("رموز العملات يجب أن تكون بثلاثة أحرف (USD, SAR...).")
            val rates = ctx.currency.latestRates(from) ?: return@ToolHandler ToolOutput.fail("تعذر جلب أسعار الصرف الآن — تأكد من الاتصال بالإنترنت.")
            val target = rates[to] ?: return@ToolHandler ToolOutput.fail("عملة غير معروفة: $to")
            val converted = amount * target
            ToolOutput.ok("${Calculator.format(amount)} $from = ${"%.2f".format(converted)} $to", speak = "يساوي ${"%.2f".format(converted)} $to")
        },
    )

    // ------------------------------------------------------------------ web research

    private fun webSearch(ctx: ToolContext) = ToolSpec(
        id = "web_search",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "web_search",
            description = "ابحث في الويب (DuckDuckGo) وأعد أفضل النتائج (عنوان + رابط). استخدمه للمعلومات الحالية ثم fetch_page لقراءة الأهم.",
            schema = Schema.object_(
                "query" to Schema.string("عبارة البحث"),
                required = listOf("query"),
            ),
        ),
        handler = ToolHandler { _, args ->
            if (!ctx.settings.snapshot().webAccessEnabled) {
                return@ToolHandler ToolOutput.fail("الوصول إلى الويب معطّل من الإعدادات.")
            }
            val query = args.str("query")?.trim().orEmpty()
            if (query.isBlank()) return@ToolHandler ToolOutput.fail("عبارة البحث فارغة.")
            val links = withContext(Dispatchers.IO) { searchDuckDuckGo(query) }
            if (links.isEmpty()) {
                ToolOutput.ok("لم أجد نتائج لـ «$query».")
            } else {
                ToolOutput.ok(
                    "نتائج البحث عن «$query»:\n" + links.mapIndexed { i, l -> "${i + 1}. ${l.first} — ${l.second}" }.joinToString("\n"),
                    card = ToolCard.WebLinks(query, links.map { CardRow(it.first, it.second) }),
                    toolResultForModel = links.mapIndexed { i, l -> "${i + 1}. ${l.first} — ${l.second}" }.joinToString("\n"),
                )
            }
        },
    )

    private fun fetchPage(ctx: ToolContext) = ToolSpec(
        id = "fetch_page",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "fetch_page",
            description = "اجلب نص صفحة ويب (حتى ~5000 حرف من المحتوى الظاهر) لتلخيصها أو اقتباسها. لا يقرأ الصور ولا يتطلب تسجيل دخول.",
            schema = Schema.object_(
                "url" to Schema.string("رابط الصفحة (https)"),
                required = listOf("url"),
            ),
        ),
        handler = ToolHandler { _, args ->
            if (!ctx.settings.snapshot().webAccessEnabled) {
                return@ToolHandler ToolOutput.fail("الوصول إلى الويب معطّل من الإعدادات.")
            }
            var url = args.str("url")?.trim().orEmpty()
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            val page = withContext(Dispatchers.IO) { fetchPageText(url) }
                ?: return@ToolHandler ToolOutput.fail("تعذر جلب الصفحة (قد تكون محمية أو بلا اتصال).")
            ToolOutput.ok(
                "اقتباس من ${page.first}:\n${page.second}",
                toolResultForModel = "محتوى الصفحة (${page.first}):\n${page.second}",
            )
        },
    )

    private val webHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun searchDuckDuckGo(query: String): List<Pair<String, String>> {
        return try {
            val request = Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(query, "UTF-8"))
                .header("user-agent", "Mozilla/5.0 (Linux; Android 14) majwuda/1.0")
                .build()
            webHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                val results = ArrayList<Pair<String, String>>()
                val regex = Regex("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                for (match in regex.findAll(body)) {
                    val href = htmlDecode(match.groupValues[1])
                    val title = htmlDecode(stripTags(match.groupValues[2])).trim()
                    val real = unwrapDuckDuckGo(href)
                    if (title.isNotBlank() && real.startsWith("http")) results += title to real
                    if (results.size >= 6) break
                }
                results
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchPageText(url: String): Pair<String, String>? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("user-agent", "Mozilla/5.0 (Linux; Android 14) majwuda/1.0")
                .header("accept", "text/html,application/xhtml+xml")
                .build()
            webHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val contentType = response.header("content-type").orEmpty()
                if (!contentType.contains("text") && !contentType.contains("json") && !contentType.contains("html")) return null
                val bytes = response.body?.bytes() ?: return null
                val text = String(bytes, Charsets.UTF_8)
                val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)?.let(::htmlDecode)?.trim()
                    ?: url
                val stripped = stripTags(
                    text.replace(Regex("(?s)<script[^>]*>.*?</script>"), " ")
                        .replace(Regex("(?s)<style[^>]*>.*?</style>"), " ")
                        .replace(Regex("(?s)<!--.*?-->"), " ")
                        .replace(Regex("(?i)<br\\s*/?>"), "\n")
                        .replace(Regex("(?i)</p>"), "\n")
                        .replace(Regex("(?i)</(div|li|h[1-6]|tr)>"), "\n"),
                )
                val collapsed = stripped
                    .replace(Regex("[\\u00A0\\u2007\\u202F]"), " ")
                    .replace(Regex("[ \\t]+"), " ")
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()
                    .take(5000)
                title to collapsed
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun unwrapDuckDuckGo(href: String): String {
        // DDG wraps outbound links as //duckduckgo.com/l/?uddg=<encoded>&rut=...
        val marker = "uddg="
        val idx = href.indexOf(marker)
        if (idx >= 0) {
            val encoded = href.substring(idx + marker.length).substringBefore('&')
            return try {
                java.net.URLDecoder.decode(encoded, "UTF-8")
            } catch (_: Exception) {
                "https://$href"
            }
        }
        return if (href.startsWith("//")) "https:$href" else href
    }

    private fun stripTags(html: String): String = html.replace(Regex("<[^>]+>"), " ")

    private fun htmlDecode(text: String): String =
        text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace(Regex("&#(\\d+);")) { m -> runCatching { m.groupValues[1].toInt().toChar().toString() }.getOrDefault(m.value) }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> runCatching { m.groupValues[1].toInt(16).toChar().toString() }.getOrDefault(m.value) }

    // ------------------------------------------------------------------ device info

    private fun getDeviceInfo(ctx: ToolContext) = ToolSpec(
        id = "get_device_info",
        gates = emptySet(),
        risk = ToolRisk.SAFE,
        definition = ToolDefinition(
            name = "get_device_info",
            description = "معلومات عن الجهاز (النظام، الطراز) — مفيدة عند أخطاء أو طلبات الدعم.",
            schema = Schema.emptyObject(),
        ),
        handler = ToolHandler { _, _ ->
            ToolOutput.ok("${DeviceCapabilities.deviceName()}، ${DeviceCapabilities.osVersion()}")
        },
    )
}
