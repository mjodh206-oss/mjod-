import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../core/constants/categories.dart';
import '../core/constants/currencies.dart';
import '../core/utils/arabic_formatters.dart';
import '../models/app_settings.dart';
import '../models/budget_plan.dart';
import '../models/debt_item.dart';
import '../models/expense_item.dart';
import '../models/salary_profile.dart';
import '../models/savings_goal.dart';
import '../services/notification_service.dart';
import '../services/pdf_export_service.dart';
import '../services/plan_engine.dart';
import '../services/security_service.dart';
import '../services/sound_service.dart';
import '../services/storage_service.dart';
import '../services/widget_sync_service.dart';

class ExpenseAddResult {
  const ExpenseAddResult({
    this.budgetAlertMessage,
    this.isOverBudget = false,
    this.newlyUnlockedBadgeId,
  });

  final String? budgetAlertMessage;
  final bool isOverBudget;
  final String? newlyUnlockedBadgeId;
}

class AppController extends ChangeNotifier {
  AppController({
    required this.storage,
    required this.sound,
    required this.notifications,
    required this.security,
    required this.widgetSync,
    required this.pdfExport,
  });

  final StorageService storage;
  final SoundService sound;
  final NotificationService notifications;
  final SecurityService security;
  final WidgetSyncService widgetSync;
  final PdfExportService pdfExport;

  AppSettings _settings = const AppSettings();
  SalaryProfile? _profile;
  BudgetPlan? _plan;
  List<ExpenseItem> _expenses = [];
  List<SavingsGoal> _goals = [];
  List<DebtItem> _debts = [];
  bool _isLocked = false;
  String? _pendingBadgeBanner;

  AppSettings get settings => _settings;
  SalaryProfile? get profile => _profile;
  BudgetPlan? get plan => _plan;
  List<ExpenseItem> get expenses => List.unmodifiable(_expenses);
  List<SavingsGoal> get goals => List.unmodifiable(_goals);
  List<DebtItem> get debts => List.unmodifiable(_debts);
  bool get isLocked => _isLocked;
  String? get pendingBadgeBanner => _pendingBadgeBanner;

  AppCurrency get currency =>
      AppCurrencies.byCode(_profile?.currencyCode ?? 'SYP');

  void clearBadgeBanner() {
    _pendingBadgeBanner = null;
    notifyListeners();
  }

  Future<void> initialize() async {
    _settings = storage.loadSettings();
    _profile = storage.loadProfile();
    _plan = storage.loadPlan();
    _expenses = storage.loadExpenses();
    _goals = storage.loadGoals();
    _debts = storage.loadDebts();

    if (_profile != null && _plan == null) {
      _plan = PlanEngine.generatePlan(_profile!);
      await storage.savePlan(_plan!);
    }

    _isLocked = _settings.isLockEnabled && _settings.hasCompletedSetup;

    sound.updatePreferences(
      soundEnabled: _settings.soundEnabled,
      musicEnabled: _settings.musicEnabled,
    );

    await _syncExternalIntegrations();
    notifyListeners();
  }

  void unlockApp() {
    _isLocked = false;
    notifyListeners();
  }

  Future<void> markOnboardingComplete() async {
    _settings = _settings.copyWith(hasSeenOnboarding: true);
    await storage.saveSettings(_settings);
    notifyListeners();
  }

  Future<void> saveWizardProfile(SalaryProfile newProfile) async {
    _profile = newProfile;
    _plan = PlanEngine.generatePlan(newProfile);
    _settings = _settings.copyWith(hasCompletedSetup: true, hasSeenOnboarding: true);

    // Seed initial debt entry if user entered monthly debt and has no debts yet
    if (newProfile.totalDebtAndInstallments > 0 && _debts.isEmpty) {
      _debts = [
        DebtItem(
          id: 'initial_debt_${DateTime.now().millisecondsSinceEpoch}',
          name: 'أقساط والتزامات شهرية',
          remainingBalance: newProfile.totalDebtAndInstallments * 6,
          minimumMonthly: newProfile.totalDebtAndInstallments,
          interestRatePercent: 0,
          dueDay: newProfile.billDueDay,
        ),
      ];
      await storage.saveDebts(_debts);
    }

    await storage.saveProfile(newProfile);
    await storage.savePlan(_plan!);
    await storage.saveSettings(_settings);
    await _evaluateBadges();
    await sound.playSuccess();
    await notifications.requestPermission();
    await _syncExternalIntegrations();
    notifyListeners();
  }

  Future<void> updatePlanSlider(BudgetBucket bucket, double newPct) async {
    final p = _profile;
    final currentPlan = _plan;
    if (p == null || currentPlan == null) return;

    _plan = PlanEngine.rebalanceFromSlider(
      profile: p,
      current: currentPlan,
      changedBucket: bucket,
      newPercentage: newPct,
    );
    _recomputeHealthWithRealSpending();
    await storage.savePlan(_plan!);
    await _evaluateBadges();
    await _syncWidgetOnly();
    notifyListeners();
  }

  Future<void> resetRecommendedPlan() async {
    final p = _profile;
    if (p == null) return;
    _plan = PlanEngine.generatePlan(p);
    _recomputeHealthWithRealSpending();
    await storage.savePlan(_plan!);
    await _syncWidgetOnly();
    notifyListeners();
  }

  List<ExpenseItem> get currentCycleExpenses {
    final p = _profile;
    if (p == null) return _expenses;
    final start = ArabicFormatters.currentSalaryCycleStart(p.salaryDay);
    return _expenses
        .where((e) => !e.createdAt.isBefore(start))
        .toList();
  }

  Map<BudgetBucket, double> get spentByBucketCurrentCycle {
    final map = <BudgetBucket, double>{
      for (final b in BudgetBucket.values) b: 0.0,
    };
    for (final e in currentCycleExpenses) {
      map[e.bucket] = (map[e.bucket] ?? 0.0) + e.amount;
    }
    return map;
  }

  double get totalSpentCurrentCycle {
    return currentCycleExpenses.fold<double>(0.0, (sum, e) => sum + e.amount);
  }

  double get remainingCurrentCycle {
    final income = _profile?.totalMonthlyIncome ?? 0;
    return income - totalSpentCurrentCycle;
  }

  Map<String, double> get spentByCategoryCurrentCycle {
    final map = <String, double>{};
    for (final e in currentCycleExpenses) {
      map[e.categoryId] = (map[e.categoryId] ?? 0.0) + e.amount;
    }
    return map;
  }

  Future<ExpenseAddResult> addExpense({
    required double amount,
    required String categoryId,
    required String note,
    DateTime? date,
  }) async {
    final cat = AppCategories.categoryById(categoryId);
    final item = ExpenseItem(
      id: 'exp_${DateTime.now().microsecondsSinceEpoch}',
      amount: amount,
      categoryId: categoryId,
      bucket: cat.bucket,
      note: note.trim(),
      createdAt: date ?? DateTime.now(),
    );

    _expenses = [item, ..._expenses]
      ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
    await storage.saveExpenses(_expenses);
    await sound.playExpenseAdd();

    _recomputeHealthWithRealSpending();
    if (_plan != null) {
      await storage.savePlan(_plan!);
    }

    String? alertMsg;
    bool isOver = false;
    if (_plan != null) {
      final bucketMeta = AppCategories.metaFor(cat.bucket);
      final planned = _plan!.amountFor(cat.bucket);
      final spent = spentByBucketCurrentCycle[cat.bucket] ?? 0.0;
      if (planned > 0 && spent > planned) {
        isOver = true;
        alertMsg =
            'تجاوزت ميزانية ${bucketMeta.titleAr} (${ArabicFormatters.formatMoney(spent, currency)} / ${ArabicFormatters.formatMoney(planned, currency)})';
        await notifications.showBudgetWarning(
          bucketTitleAr: bucketMeta.titleAr,
          isExceeded: true,
          formattedDetail: alertMsg,
        );
      } else if (planned > 0 && spent >= planned * 0.85) {
        alertMsg =
            'تنبيه: استهلكت ${((spent / planned) * 100).round()}% من ميزانية ${bucketMeta.titleAr}';
        await notifications.showBudgetWarning(
          bucketTitleAr: bucketMeta.titleAr,
          isExceeded: false,
          formattedDetail: alertMsg,
        );
      }
    }

    final unlocked = await _evaluateBadges();
    await _syncWidgetOnly();
    notifyListeners();

    return ExpenseAddResult(
      budgetAlertMessage: alertMsg,
      isOverBudget: isOver,
      newlyUnlockedBadgeId: unlocked,
    );
  }

  Future<void> deleteExpense(String id) async {
    _expenses = _expenses.where((e) => e.id != id).toList();
    await storage.saveExpenses(_expenses);
    _recomputeHealthWithRealSpending();
    if (_plan != null) {
      await storage.savePlan(_plan!);
    }
    await _syncWidgetOnly();
    notifyListeners();
  }

  Future<void> addGoal({
    required String title,
    required String iconKey,
    required double targetAmount,
    required int durationMonths,
    double initialSaved = 0,
  }) async {
    final goal = SavingsGoal(
      id: 'goal_${DateTime.now().microsecondsSinceEpoch}',
      title: title.trim(),
      iconKey: iconKey,
      targetAmount: targetAmount,
      savedAmount: initialSaved,
      durationMonths: durationMonths,
      createdAt: DateTime.now(),
    );
    _goals = [goal, ..._goals];
    await storage.saveGoals(_goals);
    await sound.playExpenseAdd();
    await _evaluateBadges();
    notifyListeners();
  }

  Future<bool> contributeToGoal(String goalId, double amount) async {
    bool completedNow = false;
    _goals = _goals.map((g) {
      if (g.id != goalId) return g;
      final updatedSaved = g.savedAmount + amount;
      final becomesCompleted = updatedSaved >= g.targetAmount && !g.celebrated;
      if (becomesCompleted) completedNow = true;
      return g.copyWith(
        savedAmount: updatedSaved,
        celebrated: g.celebrated || becomesCompleted,
      );
    }).toList();

    await storage.saveGoals(_goals);
    if (completedNow) {
      await sound.playSuccess();
    } else {
      await sound.playExpenseAdd();
    }
    await _evaluateBadges();
    notifyListeners();
    return completedNow;
  }

  Future<void> deleteGoal(String id) async {
    _goals = _goals.where((g) => g.id != id).toList();
    await storage.saveGoals(_goals);
    notifyListeners();
  }

  Future<void> addDebt(DebtItem debt) async {
    _debts = [..._debts, debt];
    await storage.saveDebts(_debts);
    await _evaluateBadges();
    await _syncNotificationsOnly();
    notifyListeners();
  }

  Future<void> updateDebtBalance(String id, double newBalance) async {
    _debts = _debts
        .map((d) => d.id == id ? d.copyWith(remainingBalance: newBalance) : d)
        .toList();
    await storage.saveDebts(_debts);
    await sound.playExpenseAdd();
    notifyListeners();
  }

  Future<void> deleteDebt(String id) async {
    _debts = _debts.where((d) => d.id != id).toList();
    await storage.saveDebts(_debts);
    await _syncNotificationsOnly();
    notifyListeners();
  }

  Future<void> setDebtStrategy(DebtStrategy strategy) async {
    _settings = _settings.copyWith(debtStrategy: strategy);
    await storage.saveSettings(_settings);
    notifyListeners();
  }

  Future<void> toggleChallengeDay(int day) async {
    final days = List<int>.from(_settings.completedChallengeDays);
    if (days.contains(day)) {
      days.remove(day);
    } else {
      days.add(day);
      days.sort();
      if (days.length % 7 == 0 || days.length == 30) {
        await sound.playSuccess();
      } else {
        await sound.playExpenseAdd();
      }
    }
    _settings = _settings.copyWith(completedChallengeDays: days);
    await storage.saveSettings(_settings);
    await _evaluateBadges();
    notifyListeners();
  }

  Future<void> updateSettings(AppSettings updated) async {
    _settings = updated;
    await storage.saveSettings(updated);
    sound.updatePreferences(
      soundEnabled: updated.soundEnabled,
      musicEnabled: updated.musicEnabled,
    );
    await _syncNotificationsOnly();
    notifyListeners();
  }

  Future<void> toggleSound() async {
    await updateSettings(
      _settings.copyWith(soundEnabled: !_settings.soundEnabled),
    );
  }

  Future<void> toggleMusic() async {
    await updateSettings(
      _settings.copyWith(musicEnabled: !_settings.musicEnabled),
    );
  }

  Future<void> toggleDarkMode() async {
    await updateSettings(_settings.copyWith(isDarkMode: !_settings.isDarkMode));
  }

  List<SmartAdviceItem> get smartAdvice {
    final p = _profile;
    final pl = _plan;
    if (p == null || pl == null) return [];
    return PlanEngine.generateSmartAdvice(
      profile: p,
      plan: pl,
      spentByBucket: spentByBucketCurrentCycle,
      currency: currency,
    );
  }

  Future<File> exportAndSharePdf() async {
    final p = _profile!;
    final pl = _plan!;
    final file = await pdfExport.generateMonthlyReport(
      profile: p,
      plan: pl,
      cycleExpenses: currentCycleExpenses,
      spentByBucket: spentByBucketCurrentCycle,
      goals: _goals,
    );
    await pdfExport.shareFile(
      file,
      text: 'تقرير ميزانية تطبيق رَتِّبْ (${ArabicFormatters.formatDateAr(DateTime.now())})',
    );
    return file;
  }

  Future<File> exportAndShareBackup() async {
    final jsonStr = storage.exportBackupJson();
    final dir = await getTemporaryDirectory();
    final file = File(
      '${dir.path}/rattib_backup_${DateTime.now().millisecondsSinceEpoch}.json',
    );
    await file.writeAsString(jsonStr, encoding: utf8);
    await pdfExport.shareFile(
      file,
      text: 'نسخة احتياطية لبيانات تطبيق رَتِّبْ',
    );
    return file;
  }

  Future<void> importBackupFromString(String rawJson) async {
    await storage.importBackupJson(rawJson);
    await initialize();
    await sound.playSuccess();
  }

  Future<void> resetAllData() async {
    await storage.clearAll();
    _settings = const AppSettings(hasSeenOnboarding: true);
    _profile = null;
    _plan = null;
    _expenses = [];
    _goals = [];
    _debts = [];
    _isLocked = false;
    await storage.saveSettings(_settings);
    await _syncExternalIntegrations();
    notifyListeners();
  }

  void _recomputeHealthWithRealSpending() {
    final p = _profile;
    final pl = _plan;
    if (p == null || pl == null) return;
    final spent = spentByBucketCurrentCycle;
    int overCount = 0;
    for (final b in BudgetBucket.values) {
      final planned = pl.amountFor(b);
      if (planned > 0 && (spent[b] ?? 0) > planned) {
        overCount++;
      }
    }
    final score = PlanEngine.calculateHealthScore(
      p,
      pl,
      overBudgetCount: overCount.toDouble(),
    );
    _plan = pl.copyWith(
      healthScore: score,
      healthLabelAr: PlanEngine.healthLabelForScore(score),
    );
  }

  Future<String?> _evaluateBadges() async {
    final earned = Set<String>.from(_settings.unlockedBadgeIds);
    String? newlyEarned;

    void unlock(String id) {
      if (!earned.contains(id)) {
        earned.add(id);
        newlyEarned ??= id;
      }
    }

    if (_plan != null) unlock('first_plan');

    final totalSavedInGoals =
        _goals.fold<double>(0.0, (s, g) => s + g.savedAmount);
    final totalSaved = (_profile?.currentSavings ?? 0.0) + totalSavedInGoals;
    if (totalSaved >= 100000 || _goals.any((g) => g.savedAmount > 0)) {
      unlock('first_saving_100k');
    }

    final distinctExpenseDays = _expenses
        .map((e) => '${e.createdAt.year}-${e.createdAt.month}-${e.createdAt.day}')
        .toSet()
        .length;
    if (_settings.completedChallengeDays.length >= 7 ||
        distinctExpenseDays >= 7) {
      unlock('seven_days_streak');
    }

    if (_settings.completedChallengeDays.length >= 30) {
      unlock('challenge_30_master');
    }

    if (_goals.any((g) => g.isCompleted)) {
      unlock('goal_achiever');
    }

    if (_debts.isNotEmpty) {
      unlock('debt_warrior');
    }

    if ((_plan?.healthScore ?? 0) >= 80) {
      unlock('healthy_salary');
    }

    if (earned.length != _settings.unlockedBadgeIds.length) {
      _settings = _settings.copyWith(unlockedBadgeIds: earned.toList());
      await storage.saveSettings(_settings);
      _pendingBadgeBanner = newlyEarned;
    }
    return newlyEarned;
  }

  Future<void> _syncExternalIntegrations() async {
    await _syncWidgetOnly();
    await _syncNotificationsOnly();
  }

  Future<void> _syncWidgetOnly() async {
    final p = _profile;
    if (p == null) {
      await widgetSync.updateWidget(
        remainingText: 'ابدأ خطتك الآن',
        daysLeftText: 'تطبيق رَتِّبْ',
        progressPercent: 100,
        subtitleText: 'فلوسك بتستاهل خطة',
      );
      return;
    }

    final income = p.totalMonthlyIncome;
    final rem = remainingCurrentCycle;
    final daysLeft = ArabicFormatters.daysUntilSalary(p.salaryDay);
    final pct = income > 0 ? ((rem / income) * 100).round().clamp(0, 100) : 0;

    await widgetSync.updateWidget(
      remainingText: ArabicFormatters.formatMoney(rem, currency),
      daysLeftText: 'متبقي $daysLeft يوم للراتب',
      progressPercent: pct,
      subtitleText: _plan?.healthLabelAr ?? 'فلوسك بتستاهل خطة',
    );
  }

  Future<void> _syncNotificationsOnly() async {
    final weeklySaving = _plan != null
        ? (_plan!.amountFor(BudgetBucket.savings) +
                _plan!.amountFor(BudgetBucket.emergency)) /
            4.0
        : 0.0;
    await notifications.syncAllSchedules(
      settings: _settings,
      profile: _profile,
      debts: _debts,
      estimatedWeeklySaving: weeklySaving,
    );
  }
}
