import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rattib/app.dart';
import 'package:rattib/core/constants/categories.dart';
import 'package:rattib/core/constants/currencies.dart';
import 'package:rattib/models/app_settings.dart';
import 'package:rattib/models/budget_plan.dart';
import 'package:rattib/models/debt_item.dart';
import 'package:rattib/models/salary_profile.dart';
import 'package:rattib/models/savings_goal.dart';
import 'package:rattib/screens/expenses/expenses_screen.dart';
import 'package:rattib/screens/home/main_shell.dart';
import 'package:rattib/screens/more/about_screen.dart';
import 'package:rattib/screens/more/more_screen.dart';
import 'package:rattib/screens/plan/plan_screen.dart';
import 'package:rattib/screens/setup/setup_wizard_screen.dart';
import 'package:rattib/screens/splash/splash_screen.dart';
import 'package:rattib/services/ai_advisor_service.dart';
import 'package:rattib/services/notification_service.dart';
import 'package:rattib/services/pdf_export_service.dart';
import 'package:rattib/services/plan_engine.dart';
import 'package:rattib/services/security_service.dart';
import 'package:rattib/services/sound_service.dart';
import 'package:rattib/services/storage_service.dart';
import 'package:rattib/services/widget_sync_service.dart';
import 'package:rattib/state/app_controller.dart';

class _TestSoundService extends SoundService {
  @override
  void updatePreferences({
    required bool soundEnabled,
    required bool musicEnabled,
  }) {}

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
  @override
  Future<void> dispose() async {}
}

class _TestNotificationService extends NotificationService {
  @override
  Future<void> init() async {}
  @override
  Future<bool> requestPermission() async => true;
  @override
  Future<void> syncAllSchedules({
    required AppSettings settings,
    required SalaryProfile? profile,
    required List<DebtItem> debts,
    required double estimatedWeeklySaving,
  }) async {}
  @override
  Future<void> showBudgetWarning({
    required String bucketTitleAr,
    required bool isExceeded,
    required String formattedDetail,
  }) async {}
}

class _TestAiAdvisorService extends AiAdvisorService {
  @override
  Future<AiAdviceResult> analyze({
    required SalaryProfile profile,
    required BudgetPlan plan,
    required AppCurrency currency,
    required Map<BudgetBucket, double> spentByBucket,
    required double totalSpentCycle,
    required double dailySafeSpend,
    required int daysLeft,
    required List<SavingsGoal> goals,
    required List<DebtItem> debts,
    String? userQuestion,
    bool allowCloud = true,
  }) {
    return super.analyze(
      profile: profile,
      plan: plan,
      currency: currency,
      spentByBucket: spentByBucket,
      totalSpentCycle: totalSpentCycle,
      dailySafeSpend: dailySafeSpend,
      daysLeft: daysLeft,
      goals: goals,
      debts: debts,
      userQuestion: userQuestion,
      allowCloud: false,
    );
  }
}

void main() {
  late Directory tmpDir;
  late StorageService storage;
  late AppController controller;

  setUp(() async {
    tmpDir = Directory.systemTemp.createTempSync('bnb_widget_test_');
    storage = StorageService();
    await storage.init(customPath: tmpDir.path);
    controller = AppController(
      storage: storage,
      sound: _TestSoundService(),
      notifications: _TestNotificationService(),
      security: SecurityService(),
      widgetSync: WidgetSyncService(),
      pdfExport: PdfExportService(),
      aiAdvisor: _TestAiAdvisorService(),
    );
    await controller.initialize();
  });

  tearDown(() async {
    await storage.close();
    if (tmpDir.existsSync()) tmpDir.deleteSync(recursive: true);
  });

  testWidgets('SplashScreen displays BNB branding and Amjad Habib credit', (
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

    await tester.pump(const Duration(milliseconds: 1900));
    expect(find.text('BNB'), findsOneWidget);
    expect(find.text('راتبك مرتب.. وحياتك أسهل'), findsOneWidget);
    expect(find.text('تنفيذ وتطوير أمجد حبيب'), findsOneWidget);
  });

  testWidgets('AboutScreen shows BNB, developer attribution and AI info', (
    tester,
  ) async {
    await tester.pumpWidget(
      RattibApp(
        controller: controller,
        homeOverride: const AboutScreen(),
      ),
    );
    await tester.pump();
    expect(find.text('BNB'), findsOneWidget);
    expect(find.text('تنفيذ وتطوير: أمجد حبيب'), findsOneWidget);
    expect(find.text('سريع وآمن + ذكاء اصطناعي مجاني'), findsOneWidget);
  });

  testWidgets('SetupWizardScreen provides fast 2-step flow and instant plan', (
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
        homeOverride: SetupWizardScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle(
      const Duration(milliseconds: 100),
      EnginePhase.sendSemanticsUpdate,
      const Duration(seconds: 10),
    );

    expect(find.text('كم راتبك الشهري؟'), findsOneWidget);
    expect(find.text('راتب شهري'), findsOneWidget);
    expect(find.text('راتب أسبوعي'), findsOneWidget);
    await tester.tap(find.text('راتب أسبوعي'));
    await tester.pumpAndSettle();
    expect(find.text('كم قبضتك الأسبوعية؟'), findsOneWidget);
    expect(find.text('احسب خطتي فوراً بدون أسئلة إضافية'), findsOneWidget);
  });

  testWidgets(
    'MainShell shows money amounts, AI advisor, and 4 tabs without overflow',
    timeout: const Timeout(Duration(seconds: 45)),
    (tester) async {
      tester.view.physicalSize = const Size(1080, 2340);
      tester.view.devicePixelRatio = 2.5;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.runAsync(() async {
        await controller.saveWizardProfile(
          const SalaryProfile(
            monthlySalary: 6000,
            currencyCode: 'SYP',
            isRenting: false,
            salaryDay: 1,
          ),
        );
      });

      await tester.pumpWidget(
        RattibApp(
          controller: controller,
          homeOverride: MainShell(controller: controller),
        ),
      );
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 15),
      );

      // Music button must stay permanently removed from the main screen
      expect(find.byIcon(Icons.music_note_rounded), findsNothing);
      expect(find.byIcon(Icons.music_off_rounded), findsNothing);

      expect(find.text('راتبك الشهري'), findsOneWidget);
      expect(find.text('مسموح تصرف باليوم'), findsOneWidget);
      expect(find.text('تقسيم راتبك بالمصاري'), findsOneWidget);

      final initialSavings = controller.plan!.savingsPct;
      await tester.runAsync(() async {
        await controller.applyPresetPlan(PlanPreset.strongSaving);
      });
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 15),
      );
      expect(controller.plan!.savingsPct, greaterThan(initialSavings));

      final planVerticalScroll = find.descendant(
        of: find.byType(PlanScreen),
        matching: find.byWidgetPredicate(
          (w) => w is Scrollable && w.axisDirection == AxisDirection.down,
        ),
      );

      final chartToggle = find.text('الرسم الدائري وتعديل المبالغ يدوياً');
      await tester.scrollUntilVisible(
        chartToggle,
        250,
        scrollable: planVerticalScroll,
      );
      await tester.ensureVisible(chartToggle);
      await tester.drag(planVerticalScroll, const Offset(0, -160));
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 10),
      );
      await tester.tap(chartToggle);
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 10),
      );
      expect(find.text('إجمالي راتبك'), findsOneWidget);

      final aiBtn = find.text('حلّل راتبي وأعطني خطة مجاناً');
      await tester.scrollUntilVisible(
        aiBtn,
        250,
        scrollable: planVerticalScroll,
      );
      await tester.ensureVisible(aiBtn);
      await tester.drag(planVerticalScroll, const Offset(0, -160));
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 10),
      );
      expect(find.text('المستشار الذكي بالذكاء الاصطناعي'), findsOneWidget);

      await tester.tap(aiBtn);
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 10),
      );
      expect(controller.latestAiResult, isNotNull);

      await tester.scrollUntilVisible(
        find.text('كم بتوفّر بعد سنة؟'),
        250,
        scrollable: planVerticalScroll,
      );
      expect(find.text('كم بتوفّر بعد سنة؟'), findsOneWidget);

      await tester.tap(find.text('مصاريفي'));
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 15),
      );
      await tester.scrollUntilVisible(
        find.text('مقارنة بين الخطة والواقع'),
        250,
        scrollable: find.descendant(
          of: find.byType(ExpensesScreen),
          matching: find.byWidgetPredicate(
            (w) => w is Scrollable && w.axisDirection == AxisDirection.down,
          ),
        ),
      );
      expect(find.text('مقارنة بين الخطة والواقع'), findsOneWidget);

      await tester.tap(find.text('أهدافي'));
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 15),
      );
      expect(find.text('أهدافك تصنع مستقبلك'), findsOneWidget);

      await tester.tap(find.text('المزيد'));
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 15),
      );
      expect(find.text('النصائح وخطة سداد الديون'), findsOneWidget);

      await tester.scrollUntilVisible(
        find.text('تصدير تقرير شهري PDF'),
        250,
        scrollable: find.descendant(
          of: find.byType(MoreScreen),
          matching: find.byType(Scrollable),
        ),
      );
      expect(find.text('تصدير تقرير شهري PDF'), findsOneWidget);
    },
  );

  testWidgets(
    'Weekly salary shows weekly plan, tracks remaining today, and repeats expense',
    timeout: const Timeout(Duration(seconds: 30)),
    (tester) async {
      tester.view.physicalSize = const Size(1080, 2340);
      tester.view.devicePixelRatio = 2.5;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.runAsync(() async {
        await controller.saveWizardProfile(
          const SalaryProfile(
            monthlySalary: 1400,
            currencyCode: 'SYP',
            payFrequency: PayFrequency.weekly,
            weeklyPayWeekday: DateTime.thursday,
          ),
        );
        await controller.addExpense(
          amount: 50,
          categoryId: 'food_grocery',
          note: 'خبز وفطور',
        );
      });

      await tester.pumpWidget(
        RattibApp(
          controller: controller,
          homeOverride: MainShell(controller: controller),
        ),
      );
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 15),
      );

      expect(find.byIcon(Icons.music_note_rounded), findsNothing);
      expect(find.byIcon(Icons.music_off_rounded), findsNothing);
      expect(find.text('قبضتك الأسبوعية (كل الخميس)'), findsOneWidget);
      expect(find.text('باقي لك مصروف لليوم'), findsOneWidget);
      expect(find.text('تقسيم قبضة الأسبوع بالمصاري'), findsOneWidget);

      await tester.tap(find.text('مصاريفي'));
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 15),
      );
      expect(find.text('مصروفك هالأسبوع حتى الآن'), findsOneWidget);
      expect(find.text('تكرار بلمسة واحدة (بدون كتابة)'), findsOneWidget);
    },
  );
}
