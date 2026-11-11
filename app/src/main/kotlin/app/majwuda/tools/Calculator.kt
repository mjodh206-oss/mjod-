package app.majwuda.tools

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import java.util.Locale

/**
 * Self-contained arithmetic evaluator used by the calculator tool: +, -, *, /, %, ^, parentheses,
 * unary minus, scientific functions and the constants pi and e. No eval, no Android.
 * The expression grammar follows standard conventions (^ binds harder than * /, % is modulo).
 */
class EvaluationException(message: String) : Exception(message)

object Calculator {

    sealed interface Token
    private data class Number(val value: Double) : Token
    private data class Op(val symbol: Char) : Token
    private data class LeftParen(val c: Char = '(') : Token
    private data class RightParen(val c: Char = ')') : Token
    private data class Func(val name: String) : Token
    private data class Const(val value: Double) : Token

    fun evaluate(expression: String): Double {
        val tokens = tokenize(expression)
        val rpn = toRpn(tokens)
        return evalRpn(rpn)
    }

    fun evaluateOrThrow(expression: String): Double = evaluate(expression)

    // ------------------------------------------------------------------------------ tokenizer

    private val FUNCTIONS = setOf(
        "sqrt", "cbrt", "abs", "sin", "cos", "tan", "asin", "acos", "atan", "ln", "log", "exp", "min", "max", "pow", "mod",
    )

    private fun tokenize(expression: String): List<Token> {
        // Accept Arabic-Indic digits and Arabic operators/symbols as input too.
        val normalized = expression
            .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3').replace('٤', '4')
            .replace('٥', '5').replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
            .replace('×', '*').replace('÷', '/').replace('−', '-').replace('٫', '.').replace('،', ',')
            .replace("π", "pi ")
        val tokens = ArrayList<Token>()
        var i = 0
        val n = normalized.length
        var prev: Token? = null
        while (i < n) {
            val ch = normalized[i]
            when {
                ch.isWhitespace() -> i++
                ch.isDigit() || ch == '.' -> {
                    val start = i
                    var seenDot = false
                    while (i < n && (normalized[i].isDigit() || normalized[i] == '.')) {
                        if (normalized[i] == '.') {
                            if (seenDot) break
                            seenDot = true
                        }
                        i++
                    }
                    // scientific notation
                    if (i < n && (normalized[i] == 'e' || normalized[i] == 'E')) {
                        var j = i + 1
                        if (j < n && (normalized[j] == '+' || normalized[j] == '-')) j++
                        if (j < n && normalized[j].isDigit()) {
                            while (j < n && normalized[j].isDigit()) j++
                            tokens += Number(normalized.substring(start, j).toDouble()); prev = tokens.last(); i = j
                            continue
                        }
                    }
                    tokens += Number(normalized.substring(start, i).toDouble()); prev = tokens.last()
                }
                ch.isLetter() -> {
                    val start = i
                    while (i < n && normalized[i].isLetter()) i++
                    val word = normalized.substring(start, i).lowercase(Locale.ROOT)
                    when (word) {
                        "pi" -> { tokens += Const(Math.PI); prev = tokens.last() }
                        "e" -> { tokens += Const(Math.E); prev = tokens.last() }
                        else -> {
                            if (word in FUNCTIONS) {
                                tokens += Func(word); prev = tokens.last()
                            } else {
                                throw EvaluationException("دالة غير معروفة: $word")
                            }
                        }
                    }
                }
                ch == '+' || ch == '-' -> {
                    val unary = prev == null || prev is Op || prev is LeftParen || prev is Func
                    if (unary) {
                        if (ch == '-') {
                            tokens += Op('u') // unary minus
                        } // unary plus is a no-op
                    } else {
                        tokens += Op(ch)
                    }
                    prev = tokens.lastOrNull()
                    i++
                }
                ch == '*' || ch == '/' || ch == '%' || ch == '^' -> {
                    tokens += Op(ch); prev = tokens.last(); i++
                }
                ch == '(' -> {
                    // implicit multiplication: 2(3+4)
                    if (prev is Number || prev is RightParen || prev is Const) tokens += Op('*')
                    tokens += LeftParen(); prev = tokens.last(); i++
                }
                ch == ')' -> {
                    tokens += RightParen(); prev = tokens.last(); i++
                }
                ch == ',' -> {
                    // function argument separator: represent as postfix marker
                    tokens += Op(','); prev = tokens.last(); i++
                }
                else -> throw EvaluationException("رمز غير متوقع: $ch")
            }
        }
        return tokens
    }

    // ----------------------------------------------------------------------------------- shunting yard

    private val PRECEDENCE = mapOf('+' to 2, '-' to 2, '*' to 3, '/' to 3, '%' to 3, '^' to 5, 'u' to 4, ',' to 1)

    private fun toRpn(tokens: List<Token>): List<Token> {
        val output = ArrayList<Token>()
        val ops = ArrayDeque<Token>()
        for (token in tokens) {
            when (token) {
                is Number, is Const -> output += token
                is Func -> ops.addLast(token)
                is LeftParen -> ops.addLast(token)
                is Op -> {
                    val symbol = token.symbol
                    while (ops.isNotEmpty()) {
                        val top = ops.last()
                        if (symbol == 'u' && top is Op && top.symbol == '^') break
                        val topPrec = when (top) {
                            is Op -> PRECEDENCE[top.symbol] ?: 0
                            is Func -> 6
                            else -> 0
                        }
                        val newPrec = PRECEDENCE[symbol] ?: 0
                        val rightAssoc = symbol == '^' || symbol == 'u'
                        if ((leftAssocBeats(topPrec, newPrec, rightAssoc))) {
                            output += ops.removeLast()
                        } else break
                    }
                    ops.addLast(token)
                }
                is RightParen -> {
                    while (ops.isNotEmpty() && ops.last() !is LeftParen) {
                        output += ops.removeLast()
                    }
                    if (ops.isEmpty()) throw EvaluationException("أقواس غير متوازنة")
                    ops.removeLast() // discard left paren
                    if (ops.isNotEmpty() && ops.last() is Func) output += ops.removeLast()
                }
            }
        }
        while (ops.isNotEmpty()) {
            val top = ops.removeLast()
            if (top is LeftParen) throw EvaluationException("أقواس غير متوازنة")
            output += top
        }
        return output
    }

    private fun leftAssocBeats(topPrec: Int, newPrec: Int, rightAssoc: Boolean): Boolean =
        if (rightAssoc) topPrec > newPrec else topPrec >= newPrec

    // ------------------------------------------------------------------------------------------ eval

    private fun evalRpn(rpn: List<Token>): Double {
        val stack = ArrayDeque<Double>()
        for (token in rpn) {
            when (token) {
                is Number -> stack.addLast(token.value)
                is Const -> stack.addLast(token.value)
                is LeftParen, is RightParen -> throw EvaluationException("أقواس غير متوازنة")
                is Op -> {
                    when (token.symbol) {
                        'u' -> {
                            val a = stack.removeLastOrNull() ?: throw EvaluationException("تعبير غير صالح")
                            stack.addLast(-a)
                        }
                        ',' -> Unit // argument separator handled by function arity logic below
                        else -> {
                            val b = stack.removeLastOrNull() ?: throw EvaluationException("تعبير غير مكتمل")
                            val a = stack.removeLastOrNull() ?: throw EvaluationException("تعبير غير مكتمل")
                            stack.addLast(binaryOp(token.symbol, a, b))
                        }
                    }
                }
                is Func -> {
                    val arity = if (token.name in setOf("min", "max", "pow", "mod")) 2 else 1
                    val args = ArrayList<Double>(arity)
                    repeat(arity) {
                        args.add(0, stack.removeLastOrNull() ?: throw EvaluationException("وسائط ناقصة للدالة ${token.name}"))
                    }
                    stack.addLast(applyFunction(token.name, args))
                }
            }
        }
        if (stack.size != 1) throw EvaluationException("تعبير غير صالح")
        return stack.last()
    }

    private fun binaryOp(symbol: Char, a: Double, b: Double): Double = when (symbol) {
        '+' -> a + b
        '-' -> a - b
        '*' -> a * b
        '/' -> {
            if (b == 0.0) throw EvaluationException("القسمة على صفر غير معرفة")
            a / b
        }
        '%' -> {
            if (b == 0.0) throw EvaluationException("القسمة على صفر غير معرفة")
            a % b
        }
        '^' -> a.pow(b)
        else -> throw EvaluationException("عملية غير معروفة: $symbol")
    }

    private fun applyFunction(name: String, args: List<Double>): Double = when (name) {
        "sqrt" -> {
            if (args[0] < 0) throw EvaluationException("الجذر التربيعي لعدد سالب غير معرف")
            sqrt(args[0])
        }
        "cbrt" -> cbrt(args[0])
        "abs" -> abs(args[0])
        "sin" -> sin(args[0])
        "cos" -> cos(args[0])
        "tan" -> tan(args[0])
        "asin" -> kotlin.math.asin(args[0])
        "acos" -> kotlin.math.acos(args[0])
        "atan" -> atan(args[0])
        "ln" -> {
            if (args[0] <= 0) throw EvaluationException("اللوغاريتم لعدد غير موجب غير معرف")
            ln(args[0])
        }
        "log" -> {
            if (args[0] <= 0) throw EvaluationException("اللوغاريتم لعدد غير موجب غير معرف")
            log10(args[0])
        }
        "exp" -> exp(args[0])
        "min" -> minOf(args[0], args[1])
        "max" -> maxOf(args[0], args[1])
        "pow" -> args[0].pow(args[1])
        "mod" -> args[0] % args[1]
        else -> throw EvaluationException("دالة غير معروفة: $name")
    }

    /** Pretty-print a result: integer-ish values stay integers, others use up to 6 decimals. */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "غير معرف"
        val rounded = Math.rint(value)
        if (abs(value - rounded) < 1e-9 && abs(value) < 1e15) return rounded.toLong().toString()
        return String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
    }
}

/** Percentages: "كم 20% من 1500؟", "كم النسبة المئوية لـ45 من 180؟" */
object PercentSupport {
    fun percentOf(percent: Double, total: Double) = total * percent / 100.0
    fun whatPercent(part: Double, total: Double): Double {
        if (total == 0.0) throw IllegalArgumentException("المجموع لا يمكن أن يكون صفرًا")
        return part * 100.0 / total
    }
    fun percentChange(from: Double, to: Double): Double {
        if (from == 0.0) throw IllegalArgumentException("القيمة الأولى لا يمكن أن تكون صفرًا")
        return (to - from) * 100.0 / from
    }
}

/** Temperature and length/weight/volume unit conversions. Pure Kotlin. */
object UnitConverter {
    private val units: Map<String, Pair<String, (Double) -> Double>> = mapOf(
        // length (base = meter)
        "مم" to ("length" to { it / 1000.0 }), "ملم" to ("length" to { it / 1000.0 }), "مليمتر" to ("length" to { it / 1000.0 }),
        "cm" to ("length" to { it / 100.0 }), "سم" to ("length" to { it / 100.0 }), "سنتيمتر" to ("length" to { it / 100.0 }),
        "m" to ("length" to { it }), "متر" to ("length" to { it }), "метр" to ("length" to { it }),
        "km" to ("length" to { it * 1000.0 }), "كم" to ("length" to { it * 1000.0 }), "كيلومتر" to ("length" to { it * 1000.0 }),
        "inch" to ("length" to { it * 0.0254 }), "بوصه" to ("length" to { it * 0.0254 }), "انش" to ("length" to { it * 0.0254 }),
        "ft" to ("length" to { it * 0.3048 }), "قدم" to ("length" to { it * 0.3048 }),
        "mile" to ("length" to { it * 1609.344 }), "ميل" to ("length" to { it * 1609.344 }),
        "yd" to ("length" to { it * 0.9144 }), "يارد" to ("length" to { it * 0.9144 }),
        // mass (base = kg)
        "غ" to ("mass" to { it / 1000.0 }), "غرام" to ("mass" to { it / 1000.0 }), "جرام" to ("mass" to { it / 1000.0 }), "g" to ("mass" to { it / 1000.0 }),
        "ملغ" to ("mass" to { it / 1_000_000.0 }), "mg" to ("mass" to { it / 1_000_000.0 }),
        "كجم" to ("mass" to { it }), "كيلو" to ("mass" to { it }), "kg" to ("mass" to { it }),
        "طن" to ("mass" to { it * 1000.0 }), "tonne" to ("mass" to { it * 1000.0 }), "t" to ("mass" to { it * 1000.0 }),
        "lb" to ("mass" to { it * 0.45359237 }), "باوند" to ("mass" to { it * 0.45359237 }), "pound" to ("mass" to { it * 0.45359237 }),
        "oz" to ("mass" to { it * 0.02834952 }), "اونصة" to ("mass" to { it * 0.02834952 }),
        // volume (base = liter)
        "ml" to ("volume" to { it / 1000.0 }), "مل" to ("volume" to { it / 1000.0 }),
        "لتر" to ("volume" to { it }), "l" to ("volume" to { it }), "ل" to ("volume" to { it }),
        "gallon" to ("volume" to { it * 3.78541 }), "غالون" to ("volume" to { it * 3.78541 }),
        "cup" to ("volume" to { it * 0.24 }), "كوب" to ("volume" to { it * 0.24 }),
        // speed (base = m/s)
        "km/h" to ("speed" to { it / 3.6 }), "كم/س" to ("speed" to { it / 3.6 }), "kmh" to ("speed" to { it / 3.6 }),
        "m/s" to ("speed" to { it }), "م/ث" to ("speed" to { it }),
        "mph" to ("speed" to { it * 0.44704 }), "ميل/س" to ("speed" to { it * 0.44704 }),
        "knot" to ("speed" to { it * 0.51444 }), "عقده" to ("speed" to { it * 0.51444 }),
        // data (base = byte)
        "بايت" to ("data" to { it }), "byte" to ("data" to { it }), "B" to ("data" to { it }),
        "kb" to ("data" to { it * 1024.0 }), "mb" to ("data" to { it * 1024.0 * 1024.0 }),
        "gb" to ("data" to { it * 1024.0 * 1024.0 * 1024.0 }), "tb" to ("data" to { it * 1024.0 * 1024.0 * 1024.0 * 1024.0 }),
        "كيلوبايت" to ("data" to { it * 1024.0 }), "ميغابايت" to ("data" to { it * 1024.0 * 1024.0 }),
        "غيغابايت" to ("data" to { it * 1024.0 * 1024.0 * 1024.0 }),
        // time (base = second)
        "ثانية" to ("time" to { it }), "ث" to ("time" to { it }), "second" to ("time" to { it }), "sec" to ("time" to { it }),
        "دقيقة" to ("time" to { it * 60.0 }), "د" to ("time" to { it * 60.0 }), "minute" to ("time" to { it * 60.0 }), "min" to ("time" to { it * 60.0 }),
        "ساعة" to ("time" to { it * 3600.0 }), "ساعه" to ("time" to { it * 3600.0 }), "hour" to ("time" to { it * 3600.0 }),
        "يوم" to ("time" to { it * 86400.0 }), "day" to ("time" to { it * 86400.0 }),
        "اسبوع" to ("time" to { it * 604800.0 }), "week" to ("time" to { it * 604800.0 }),
    )

    fun convert(value: Double, fromUnit: String, toUnit: String): Double {
        val from = units[fromUnit.trim().lowercase(Locale.ROOT)] ?: throw EvaluationException("وحدة غير معروفة: $fromUnit")
        val to = units[toUnit.trim().lowercase(Locale.ROOT)] ?: throw EvaluationException("وحدة غير معروفة: $toUnit")
        if (from.first != to.first) throw EvaluationException("لا يمكن التحويل بين $fromUnit و $toUnit (وحدتان من نوعين مختلفين)")
        val base = from.second(value)
        // Recompute the reverse factor by evaluating 0 and 1 through the "to" function.
        val f0 = units[toUnit.trim().lowercase(Locale.ROOT)]!!.second(0.0)
        val f1 = units[toUnit.trim().lowercase(Locale.ROOT)]!!.second(1.0)
        val factor = f1 - f0
        return (base - f0) / factor
    }

    fun convertTemperature(value: Double, from: String, to: String): Double {
        val norm = { s: String ->
            when (s.trim().lowercase(Locale.ROOT)) {
                "c", "°c", "مئويه", "مئوية", "سيليزيوس", "celsius" -> "c"
                "f", "°f", "فهرنهايت", "fahrenheit" -> "f"
                "k", "°k", "كلفن", "kelvin" -> "k"
                else -> throw EvaluationException("وحدة حرارة غير معروفة: $s")
            }
        }
        val f = norm(from)
        val t = norm(to)
        val celsius = when (f) {
            "c" -> value
            "f" -> (value - 32.0) * 5.0 / 9.0
            else -> value - 273.15
        }
        return when (t) {
            "c" -> celsius
            "f" -> celsius * 9.0 / 5.0 + 32.0
            else -> celsius + 273.15
        }
    }
}
