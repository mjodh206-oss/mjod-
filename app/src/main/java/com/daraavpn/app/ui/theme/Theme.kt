package com.daraavpn.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val DaraaColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = NavyNight,
    secondary = EmeraldConnected,
    onSecondary = NavyNight,
    background = NavyNight,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavyGlass,
    onSurfaceVariant = TextSecondary,
    error = DangerRed
)

@Composable
fun DaraaVPNTheme(
    isRtl: Boolean = true,
    content: @Composable () -> Unit
) {
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = DaraaColorScheme,
            content = content
        )
    }
}
