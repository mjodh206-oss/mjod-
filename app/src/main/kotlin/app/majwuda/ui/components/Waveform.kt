package app.majwuda.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.majwuda.ui.theme.MajwudaPalette
import kotlin.math.sin

/** Thin ribbon of bars that ripple while the mic is open. */
@Composable
fun Waveform(
    level: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "phase",
    )

    Canvas(modifier = modifier.fillMaxWidth().height(34.dp)) {
        val bars = 48
        val step = size.width / bars
        val midY = size.height / 2f
        for (i in 0 until bars) {
            val envelope = sin((i.toFloat() / bars) * Math.PI).toFloat().coerceIn(0.15f, 1f)
            val wobble = 0.55f + 0.45f * sin(phase * 2f + i * 0.55f)
            val amplitude = if (active) {
                (0.12f + level * 0.78f * wobble) * envelope
            } else {
                0.06f * envelope
            }
            val barHeight = (size.height * amplitude).coerceAtLeast(2f)
            val x = i * step + step / 2f
            val color = lerpColor(MajwudaPalette.Teal, MajwudaPalette.Violet, i.toFloat() / bars)
            drawLine(
                color = color.copy(alpha = if (active) 0.95f else 0.4f),
                start = Offset(x, midY - barHeight / 2f),
                end = Offset(x, midY + barHeight / 2f),
                strokeWidth = step * 0.52f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

private fun lerpColor(from: androidx.compose.ui.graphics.Color, to: androidx.compose.ui.graphics.Color, fraction: Float): androidx.compose.ui.graphics.Color {
    val t = fraction.coerceIn(0f, 1f)
    return androidx.compose.ui.graphics.Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha + (to.alpha - from.alpha) * t,
    )
}
