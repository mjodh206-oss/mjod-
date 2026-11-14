import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rattib/app.dart';
import 'package:rattib/models/salary_profile.dart';
import 'package:rattib/screens/home/main_shell.dart';
import 'package:rattib/screens/more/about_screen.dart';
import 'package:rattib/screens/more/more_screen.dart';
import 'package:rattib/screens/plan/plan_screen.dart';
import 'package:rattib/screens/splash/splash_screen.dart';
import 'package:rattib/services/notification_service.dart';
import 'package:rattib/services/pdf_export_service.dart';
import 'package:rattib/services/security_service.dart';
import 'package:rattib/services/sound_service.dart';
import 'package:rattib/services/storage_service.dart';
import 'package:rattib/services/widget_sync_service.dart';
import 'package:rattib/state/app_controller.dart';

class _TestSoundService extends SoundService {
  @override
  Future<void> playSplashIntro() async {}
  @override
  Future<void> playExpenseAdd() async {}
  @override
  Future<void> playSuccess() async {}
  @override
  Future<void> startAmbientMusic() async {}
  @override
  Future<void> stopAmbientMusic() async {}
}

class _TestNotificationService extends NotificationService {
  @override
  Future<void> init() async {}
  @override
  Future<bool> requestPermission() async => true;
  @override
  Future<void> showBudgetWarning({
    required String bucketTitleAr,
    required bool isExceeded,
    required String formattedDetail,
  }) async {}
}

void main() {
  late Directory tmpDir;
  late AppController controller;

  setUp(() async {
    tmpDir = Directory.systemTemp.createTempSync('rattib_widget_test_');
    final storage = StorageService();
    await storage.init(customPath: tmpDir.path);
    controller = AppController(
      storage: storage,
      sound: _TestSoundService(),
      notifications: _TestNotificationService(),
      security: SecurityService(),
      widgetSync: WidgetSyncService(),
      pdfExport: PdfExportService(),
    );
    await controller.initialize();
  });

  tearDown(() {
    if (tmpDir.existsSync()) tmpDir.deleteSync(recursive: true);
  });

  testWidgets('SplashScreen displays tagline and Amjad Habib credit', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1080, 2340);
    tester.view.devicePixelRatio = 2.5;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(
      RattibApp(
        controller: controller,
        homeOverride: SplashScreen(
          controller: controller,
          autoNavigate: false,
        ),
      ),
    );

    await tester.pump(const Duration(milliseconds: 900));
    expect(find.text('فلوسك بتستاهل خطة'), findsOneWidget);
    expect(find.text('تنفيذ وتطوير أمجد حبيب'), findsOneWidget);
  });

  testWidgets('AboutScreen shows developer attribution and offline promise', (
    tester,
  ) async {
    await tester.pumpWidget(
      RattibApp(
        controller: controller,
        homeOverride: const AboutScreen(),
      ),
    );
    await tester.pump();
    expect(find.text('تنفيذ وتطوير: أمجد حبيب'), findsOneWidget);
    expect(find.text('يعمل بدون إنترنت بالكامل'), findsOneWidget);
  });

  testWidgets('MainShell renders plan and switches tabs cleanly', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1080, 2340);
    tester.view.devicePixelRatio = 2.5;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await controller.saveWizardProfile(
      const SalaryProfile(
        monthlySalary: 1500000,
        currencyCode: 'SYP',
        isRenting: true,
        rentAmount: 400000,
        salaryDay: 1,
      ),
    );

    await tester.pumpWidget(
      RattibApp(
        controller: controller,
        homeOverride: MainShell(controller: controller),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('توزيع راتبك الشهري'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('كم ممكن توفر بالسنة؟'),
      250,
      scrollable: find.descendant(
        of: find.byType(PlanScreen),
        matching: find.byType(Scrollable),
      ),
    );
    expect(find.text('كم ممكن توفر بالسنة؟'), findsOneWidget);

    await tester.tap(find.text('المصاريف'));
    await tester.pumpAndSettle();
    expect(find.text('مقارنة بين الخطة والواقع'), findsOneWidget);

    await tester.tap(find.text('الأهداف'));
    await tester.pumpAndSettle();
    expect(find.text('أهدافك تصنع مستقبلك'), findsOneWidget);

    await tester.tap(find.text('النصائح'));
    await tester.pumpAndSettle();
    expect(find.text('نصيحة اليوم'), findsOneWidget);

    await tester.tap(find.text('المزيد'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('تصدير تقرير شهري PDF'),
      250,
      scrollable: find.descendant(
        of: find.byType(MoreScreen),
        matching: find.byType(Scrollable),
      ),
    );
    expect(find.text('تصدير تقرير شهري PDF'), findsOneWidget);
  });
}
