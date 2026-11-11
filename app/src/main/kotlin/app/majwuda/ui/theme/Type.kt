package app.majwuda.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.majwuda.R

/** Plex Sans Arabic, shipped as app resources — renders Arabic and Latin beautifully. */
val PlexArabic: FontFamily = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

val MajwudaTypography: Typography = Typography(
    displayLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 56.sp),
    displayMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 46.sp),
    displaySmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)
