import 'dart:convert';

import 'package:hive_flutter/hive_flutter.dart';

import '../models/app_settings.dart';
import '../models/budget_plan.dart';
import '../models/debt_item.dart';
import '../models/expense_item.dart';
import '../models/salary_profile.dart';
import '../models/savings_goal.dart';

class StorageService {
  static const String _mainBoxName = 'rattib_main_box';
  static const String _kProfile = 'profile';
  static const String _kPlan = 'plan';
  static const String _kExpenses = 'expenses';
  static const String _kGoals = 'goals';
  static const String _kDebts = 'debts';
  static const String _kSettings = 'settings';

  Box<dynamic>? _box;

  Future<void> init({String? customPath}) async {
    if (customPath != null) {
      Hive.init(customPath);
    } else {
      await Hive.initFlutter();
    }
    _box = await Hive.openBox<dynamic>(_mainBoxName);
  }

  Box<dynamic> get _safeBox {
    final b = _box;
    if (b == null) {
      throw StateError('StorageService.init() must be called first.');
    }
    return b;
  }

  AppSettings loadSettings() {
    final raw = _safeBox.get(_kSettings);
    if (raw is String && raw.isNotEmpty) {
      return AppSettings.fromJson(
        jsonDecode(raw) as Map<String, dynamic>,
      );
    }
    return const AppSettings();
  }

  Future<void> saveSettings(AppSettings settings) async {
    await _safeBox.put(_kSettings, jsonEncode(settings.toJson()));
  }

  SalaryProfile? loadProfile() {
    final raw = _safeBox.get(_kProfile);
    if (raw is String && raw.isNotEmpty) {
      return SalaryProfile.fromJson(
        jsonDecode(raw) as Map<String, dynamic>,
      );
    }
    return null;
  }

  Future<void> saveProfile(SalaryProfile profile) async {
    await _safeBox.put(_kProfile, jsonEncode(profile.toJson()));
  }

  BudgetPlan? loadPlan() {
    final raw = _safeBox.get(_kPlan);
    if (raw is String && raw.isNotEmpty) {
      return BudgetPlan.fromJson(
        jsonDecode(raw) as Map<String, dynamic>,
      );
    }
    return null;
  }

  Future<void> savePlan(BudgetPlan plan) async {
    await _safeBox.put(_kPlan, jsonEncode(plan.toJson()));
  }

  List<ExpenseItem> loadExpenses() {
    final raw = _safeBox.get(_kExpenses);
    if (raw is String && raw.isNotEmpty) {
      final list = jsonDecode(raw) as List<dynamic>;
      return list
          .map((e) => ExpenseItem.fromJson(e as Map<String, dynamic>))
          .toList()
        ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
    }
    return [];
  }

  Future<void> saveExpenses(List<ExpenseItem> expenses) async {
    await _safeBox.put(
      _kExpenses,
      jsonEncode(expenses.map((e) => e.toJson()).toList()),
    );
  }

  List<SavingsGoal> loadGoals() {
    final raw = _safeBox.get(_kGoals);
    if (raw is String && raw.isNotEmpty) {
      final list = jsonDecode(raw) as List<dynamic>;
      return list
          .map((e) => SavingsGoal.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }

  Future<void> saveGoals(List<SavingsGoal> goals) async {
    await _safeBox.put(
      _kGoals,
      jsonEncode(goals.map((e) => e.toJson()).toList()),
    );
  }

  List<DebtItem> loadDebts() {
    final raw = _safeBox.get(_kDebts);
    if (raw is String && raw.isNotEmpty) {
      final list = jsonDecode(raw) as List<dynamic>;
      return list
          .map((e) => DebtItem.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }

  Future<void> saveDebts(List<DebtItem> debts) async {
    await _safeBox.put(
      _kDebts,
      jsonEncode(debts.map((e) => e.toJson()).toList()),
    );
  }

  String exportBackupJson() {
    final payload = <String, dynamic>{
      'app': 'rattib',
      'version': 1,
      'exportedAt': DateTime.now().toIso8601String(),
      'settings': loadSettings().toJson(),
      'profile': loadProfile()?.toJson(),
      'plan': loadPlan()?.toJson(),
      'expenses': loadExpenses().map((e) => e.toJson()).toList(),
      'goals': loadGoals().map((e) => e.toJson()).toList(),
      'debts': loadDebts().map((e) => e.toJson()).toList(),
    };
    return const JsonEncoder.withIndent('  ').convert(payload);
  }

  Future<void> importBackupJson(String rawJson) async {
    final map = jsonDecode(rawJson) as Map<String, dynamic>;
    if (map['app'] != 'rattib') {
      throw const FormatException('ملف النسخة الاحتياطية غير صالح لتطبيق رَتِّبْ.');
    }
    if (map['settings'] is Map<String, dynamic>) {
      await saveSettings(
        AppSettings.fromJson(map['settings'] as Map<String, dynamic>),
      );
    }
    if (map['profile'] is Map<String, dynamic>) {
      await saveProfile(
        SalaryProfile.fromJson(map['profile'] as Map<String, dynamic>),
      );
    }
    if (map['plan'] is Map<String, dynamic>) {
      await savePlan(BudgetPlan.fromJson(map['plan'] as Map<String, dynamic>));
    }
    if (map['expenses'] is List) {
      await saveExpenses(
        (map['expenses'] as List)
            .map((e) => ExpenseItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
    }
    if (map['goals'] is List) {
      await saveGoals(
        (map['goals'] as List)
            .map((e) => SavingsGoal.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
    }
    if (map['debts'] is List) {
      await saveDebts(
        (map['debts'] as List)
            .map((e) => DebtItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
    }
  }

  Future<void> clearAll() async {
    await _safeBox.clear();
  }
}
