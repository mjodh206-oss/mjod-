package app.majwuda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.majwuda.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = MajwudaPalette.Teal,
    onPrimary = Color(0xFF052B27),
    primaryContainer = Color(0xFF123B36),
    onPrimaryContainer = MajwudaPalette.Teal,
    secondary = MajwudaPalette.VioletSoft,
    onSecondary = Color(0xFF241145),
    secondaryContainer = Color(0xFF2E2054),
    onSecondaryContainer = MajwudaPalette.VioletSoft,
    tertiary = MajwudaPalette.Amber,
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF443208),
    onTertiaryContainer = MajwudaPalette.Amber,
    error = MajwudaPalette.Red,
    onError = Color(0xFF3A0A0A),
    background = MajwudaPalette.SpaceDeep,
    onBackground = MajwudaPalette.TextPrimary,
    surface = MajwudaPalette.Surface,
    onSurface = MajwudaPalette.TextPrimary,
    surfaceVariant = MajwudaPalette.SurfaceHigh,
    onSurfaceVariant = MajwudaPalette.TextSecondary,
    outline = MajwudaPalette.Outline,
    outlineVariant = MajwudaPalette.OutlineSoft,
    inverseSurface = MajwudaPalette.TextPrimary,
    inverseOnSurface = MajwudaPalette.SpaceDeep,
    inversePrimary = MajwudaPalette.TealDeep,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF115E59),
    secondary = Color(0xFF6D28D9),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF5B21B6),
    tertiary = Color(0xFF92400E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

@Composable
fun MajwudaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = MajwudaTypography,
        content = content,
    )
}
