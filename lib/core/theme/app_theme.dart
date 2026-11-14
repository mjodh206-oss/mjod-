import 'package:flutter/material.dart';

class AppColorPalette {
  const AppColorPalette({
    required this.key,
    required this.nameAr,
    required this.primary,
    required this.primaryDark,
    required this.accent,
    required this.lightBg,
    required this.darkBg,
    required this.darkCard,
  });

  final String key;
  final String nameAr;
  final Color primary;
  final Color primaryDark;
  final Color accent;
  final Color lightBg;
  final Color darkBg;
  final Color darkCard;
}

class AppThemes {
  AppThemes._();

  static const Color gold = Color(0xFFD4AF37);
  static const Color goldBright = Color(0xFFF5C443);
  static const Color emeraldDeep = Color(0xFF042B1F);
  static const Color emeraldMid = Color(0xFF0A5C40);
  static const Color success = Color(0xFF10B981);
  static const Color warning = Color(0xFFF59E0B);
  static const Color danger = Color(0xFFEF4444);

  static const List<AppColorPalette> palettes = [
    AppColorPalette(
      key: 'emerald_gold',
      nameAr: 'زمردي وذهبي',
      primary: Color(0xFF0B6E4F),
      primaryDark: Color(0xFF053D2C),
      accent: Color(0xFFD4AF37),
      lightBg: Color(0xFFF6FAF8),
      darkBg: Color(0xFF071611),
      darkCard: Color(0xFF0F241C),
    ),
    AppColorPalette(
      key: 'royal_amber',
      nameAr: 'ذهبي ملكي',
      primary: Color(0xFF9E7216),
      primaryDark: Color(0xFF543C08),
      accent: Color(0xFF10B981),
      lightBg: Color(0xFFFCF9F2),
      darkBg: Color(0xFF14110A),
      darkCard: Color(0xFF211C10),
    ),
    AppColorPalette(
      key: 'midnight_teal',
      nameAr: 'تركواز هادئ',
      primary: Color(0xFF0E7490),
      primaryDark: Color(0xFF083F4F),
      accent: Color(0xFFF59E0B),
      lightBg: Color(0xFFF4FAFC),
      darkBg: Color(0xFF07151A),
      darkCard: Color(0xFF0F232B),
    ),
    AppColorPalette(
      key: 'soft_sage',
      nameAr: 'أخضر نعناعي',
      primary: Color(0xFF15803D),
      primaryDark: Color(0xFF0B4A23),
      accent: Color(0xFFEAB308),
      lightBg: Color(0xFFF5FBF6),
      darkBg: Color(0xFF08140C),
      darkCard: Color(0xFF112217),
    ),
  ];

  static AppColorPalette paletteByKey(String key) {
    return palettes.firstWhere(
      (p) => p.key == key,
      orElse: () => palettes.first,
    );
  }

  static ThemeData buildTheme({
    required String paletteKey,
    required Brightness brightness,
  }) {
    final palette = paletteByKey(paletteKey);
    final isDark = brightness == Brightness.dark;

    final colorScheme = ColorScheme.fromSeed(
      seedColor: palette.primary,
      brightness: brightness,
      primary: isDark ? const Color(0xFF1EC88E) : palette.primary,
      onPrimary: isDark ? const Color(0xFF042218) : Colors.white,
      secondary: palette.accent,
      onSecondary: const Color(0xFF1E1704),
      surface: isDark ? palette.darkCard : Colors.white,
    );

    final bgColor = isDark ? palette.darkBg : palette.lightBg;
    final cardColor = isDark ? palette.darkCard : Colors.white;
    final textPrimary = isDark ? const Color(0xFFF1F7F4) : const Color(0xFF0E221A);
    final textSecondary =
        isDark ? const Color(0xFFA7C2B7) : const Color(0xFF4B665A);

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      fontFamily: 'Cairo',
      colorScheme: colorScheme,
      scaffoldBackgroundColor: bgColor,
      cardColor: cardColor,
      appBarTheme: AppBarTheme(
        backgroundColor: bgColor,
        foregroundColor: textPrimary,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: true,
        titleTextStyle: TextStyle(
          fontFamily: 'Cairo',
          fontSize: 20,
          fontWeight: FontWeight.w700,
          color: textPrimary,
        ),
      ),
      cardTheme: CardThemeData(
        color: cardColor,
        elevation: isDark ? 0 : 1.5,
        shadowColor: palette.primaryDark.withValues(alpha: 0.08),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(
            color: isDark
                ? Colors.white.withValues(alpha: 0.08)
                : palette.primary.withValues(alpha: 0.08),
          ),
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: cardColor,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      ),
      textTheme: TextTheme(
        headlineLarge: TextStyle(
          fontFamily: 'Cairo',
          fontSize: 28,
          fontWeight: FontWeight.w800,
          color: textPrimary,
        ),
        headlineMedium: TextStyle(
          fontFamily: 'Cairo',
          fontSize: 22,
          fontWeight: FontWeight.w700,
          color: textPrimary,
        ),
        titleLarge: TextStyle(
          fontFamily: 'Cairo',
          fontSize: 18,
          fontWeight: FontWeight.w700,
          color: textPrimary,
        ),
        titleMedium: TextStyle(
          fontFamily: 'Cairo',
          fontSize: 16,
          fontWeight: FontWeight.w600,
          color: textPrimary,
        ),
        bodyLarge: TextStyle(
          fontFamily: 'Cairo',
          fontSize: 15,
          fontWeight: FontWeight.w500,
          color: textPrimary,
        ),
        bodyMedium: TextStyle(
          fontFamily: 'Cairo',
          fontSize: 13.5,
          fontWeight: FontWeight.w500,
          color: textSecondary,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: isDark ? const Color(0xFF1EC88E) : palette.primary,
          foregroundColor: isDark ? const Color(0xFF042218) : Colors.white,
          minimumSize: const Size(double.infinity, 54),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          textStyle: const TextStyle(
            fontFamily: 'Cairo',
            fontSize: 16,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(0, 50),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          textStyle: const TextStyle(
            fontFamily: 'Cairo',
            fontSize: 15,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: isDark
            ? Colors.white.withValues(alpha: 0.06)
            : palette.primary.withValues(alpha: 0.05),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(
            color: isDark
                ? Colors.white.withValues(alpha: 0.12)
                : palette.primary.withValues(alpha: 0.15),
          ),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: palette.accent, width: 1.8),
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      ),
      sliderTheme: SliderThemeData(
        activeTrackColor: palette.accent,
        thumbColor: palette.accent,
        inactiveTrackColor: palette.accent.withValues(alpha: 0.2),
        overlayColor: palette.accent.withValues(alpha: 0.15),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
    );
  }
}
