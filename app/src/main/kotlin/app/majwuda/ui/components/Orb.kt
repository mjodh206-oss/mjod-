package app.majwuda.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.majwuda.assistant.EngineState
import app.majwuda.ui.theme.MajwudaPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * The مجودة orb: a breathing gradient sphere that reacts to the assistant's state.
 * LISTENING pulses with the mic level; TOOL spins a comet arc; THINKING breathes slowly.
 */
@Composable
fun Orb(
    state: EngineState,
    level: Float,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing)),
        label = "breathe",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "spin",
    )
    val glowPulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing)),
        label = "glow",
    )
    val animatedLevel by animateFloatAsState(
        targetValue = if (state == EngineState.LISTENING) level else 0f,
        animationSpec = tween(90),
        label = "level",
    )

    val scale = when (state) {
        EngineState.LISTENING -> 1f + animatedLevel * 0.28f
        EngineState.THINKING -> breathe
        EngineState.TOOL -> 1.02f
        EngineState.IDLE -> breathe * 0.96f
    }

    Box(modifier = modifier.size(size * scale)) {
        Canvas(modifier = Modifier.size(size * scale)) {
            val s = this.size.minDimension
            val center = Offset(s / 2f, s / 2f)
            val radius = s / 2f

            // Outer halo
            val haloAlpha = if (state == EngineState.THINKING) glowPulse else 0.32f + animatedLevel * 0.4f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MajwudaPalette.Teal.copy(alpha = haloAlpha),
                        MajwudaPalette.Violet.copy(alpha = haloAlpha * 0.55f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius * 1.7f,
                ),
                radius = radius * 1.7f,
                center = center,
            )

            // Body
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color(0xFFB8FFF2),
                    0.28f to MajwudaPalette.Teal,
                    0.62f to MajwudaPalette.Violet,
                    1.0f to Color(0xFF241145),
                    center = Offset(s * 0.36f, s * 0.36f),
                    radius = radius * 1.35f,
                ),
                radius = radius,
                center = center,
            )

            // Specular highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
                    center = Offset(s * 0.38f, s * 0.34f),
                    radius = radius * 0.5f,
                ),
                radius = radius * 0.5f,
                center = Offset(s * 0.38f, s * 0.34f),
            )

            // Orbiting comet while a tool runs
            if (state == EngineState.TOOL) {
                val angle = Math.toRadians(spin.toDouble())
                val orbitRadius = radius * 1.18f
                val px = center.x + cos(angle).toFloat() * orbitRadius
                val py = center.y + sin(angle).toFloat() * orbitRadius
                drawCircle(
                    color = MajwudaPalette.Amber.copy(alpha = 0.9f),
                    radius = s * 0.045f,
                    center = Offset(px, py),
                )
            }
        }
    }
}
