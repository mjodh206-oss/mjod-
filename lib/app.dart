import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'core/theme/app_theme.dart';
import 'screens/splash/splash_screen.dart';
import 'state/app_controller.dart';

class RattibApp extends StatelessWidget {
  const RattibApp({
    super.key,
    required this.controller,
    this.homeOverride,
  });

  final AppController controller;
  final Widget? homeOverride;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final s = controller.settings;
        return MaterialApp(
          title: 'رَتِّبْ',
          debugShowCheckedModeBanner: false,
          locale: const Locale('ar'),
          supportedLocales: const [Locale('ar')],
          localizationsDelegates: const [
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          themeMode: s.isDarkMode ? ThemeMode.dark : ThemeMode.light,
          theme: AppThemes.buildTheme(
            paletteKey: s.themePaletteKey,
            brightness: Brightness.light,
          ),
          darkTheme: AppThemes.buildTheme(
            paletteKey: s.themePaletteKey,
            brightness: Brightness.dark,
          ),
          builder: (context, child) {
            return Directionality(
              textDirection: TextDirection.rtl,
              child: child ?? const SizedBox.shrink(),
            );
          },
          home: homeOverride ?? SplashScreen(controller: controller),
        );
      },
    );
  }
}
