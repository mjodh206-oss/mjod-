import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:rattib/core/constants/categories.dart';
import 'package:rattib/models/app_settings.dart';
import 'package:rattib/models/expense_item.dart';
import 'package:rattib/models/salary_profile.dart';
import 'package:rattib/models/savings_goal.dart';
import 'package:rattib/services/plan_engine.dart';
import 'package:rattib/services/storage_service.dart';

void main() {
  test('StorageService round-trip and JSON backup/restore', () async {
    final tmpDir = Directory.systemTemp.createTempSync('rattib_storage_test_');
    final storage = StorageService();
    await storage.init(customPath: tmpDir.path);
    addTearDown(() async {
      await storage.close();
      if (tmpDir.existsSync()) tmpDir.deleteSync(recursive: true);
    });

    const profile = SalaryProfile(
      monthlySalary: 2000000,
      currencyCode: 'SYP',
      salaryDay: 5,
    );
    final plan = PlanEngine.generatePlan(profile);
    final expense = ExpenseItem(
      id: 'e1',
      amount: 45000,
      categoryId: 'food',
      bucket: BudgetBucket.necessities,
      note: 'بقالة',
      createdAt: DateTime(2026, 11, 12),
    );
    final goal = SavingsGoal(
      id: 'g1',
      title: 'سفر',
      iconKey: 'travel',
      targetAmount: 1000000,
      savedAmount: 250000,
      durationMonths: 10,
      createdAt: DateTime(2026, 11, 1),
    );
    const settings = AppSettings(
      hasSeenOnboarding: true,
      hasCompletedSetup: true,
      isDarkMode: true,
      completedChallengeDays: [1, 2, 3],
    );

    await storage.saveProfile(profile);
    await storage.savePlan(plan);
    await storage.saveExpenses([expense]);
    await storage.saveGoals([goal]);
    await storage.saveSettings(settings);

    final backupJson = storage.exportBackupJson();
    await storage.clearAll();
    expect(storage.loadProfile(), isNull);

    await storage.importBackupJson(backupJson);
    expect(storage.loadProfile()?.monthlySalary, 2000000);
    expect(storage.loadExpenses().length, 1);
    expect(storage.loadGoals().first.title, 'سفر');
    expect(storage.loadSettings().completedChallengeDays, [1, 2, 3]);
  });
}
