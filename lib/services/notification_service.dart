import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/data/latest_all.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;

import '../core/constants/currencies.dart';
import '../core/utils/arabic_formatters.dart';
import '../models/app_settings.dart';
import '../models/debt_item.dart';
import '../models/salary_profile.dart';

class NotificationService {
  final FlutterLocalNotificationsPlugin _plugin =
      FlutterLocalNotificationsPlugin();

  bool _initialized = false;

  static const String _channelId = 'rattib_reminders';
  static const String _channelName = 'تنبيهات رَتِّبْ المالية';
  static const String _channelDesc =
      'تذكير يوم الراتب، المصاريف اليومية، الأقساط وتنبيهات الميزانية';

  Future<void> init() async {
    if (_initialized) return;
    try {
      tzdata.initializeTimeZones();
      tz.setLocalLocation(tz.UTC);

      const androidSettings = AndroidInitializationSettings(
        '@drawable/ic_stat_rattib',
      );
      const initSettings = InitializationSettings(android: androidSettings);
      await _plugin.initialize(initSettings);

      final androidPlugin = _plugin.resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin>();
      await androidPlugin?.createNotificationChannel(
        const AndroidNotificationChannel(
          _channelId,
          _channelName,
          description: _channelDesc,
          importance: Importance.high,
        ),
      );
      _initialized = true;
    } catch (e) {
      debugPrint('NotificationService init skipped: $e');
    }
  }

  Future<bool> requestPermission() async {
    if (!_initialized) return false;
    try {
      final androidPlugin = _plugin.resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin>();
      final granted =
          await androidPlugin?.requestNotificationsPermission() ?? true;
      return granted;
    } catch (_) {
      return false;
    }
  }

  NotificationDetails get _details => const NotificationDetails(
        android: AndroidNotificationDetails(
          _channelId,
          _channelName,
          channelDescription: _channelDesc,
          importance: Importance.high,
          priority: Priority.high,
          icon: '@drawable/ic_stat_rattib',
        ),
      );

  Future<void> syncAllSchedules({
    required AppSettings settings,
    required SalaryProfile? profile,
    required List<DebtItem> debts,
    required double estimatedWeeklySaving,
  }) async {
    if (!_initialized) return;
    try {
      await _plugin.cancelAll();
      if (profile == null) return;

      final currency = AppCurrencies.byCode(profile.currencyCode);

      if (settings.salaryDayReminder) {
        final nextSalary = _nextMonthlyDateTime(profile.salaryDay, 9, 0);
        await _scheduleSafe(
          id: 101,
          title: 'نزل الراتب! خلينا نوزعه 💰',
          body:
              'افتح رَتِّبْ الآن وراجع خطتك الشهرية لـ ${ArabicFormatters.formatMoney(profile.totalMonthlyIncome, currency)}.',
          localTarget: nextSalary,
          match: DateTimeComponents.dayOfMonthAndTime,
        );
      }

      if (settings.eveningReminder) {
        final nextEvening = _nextDailyDateTime(
          settings.eveningHour,
          settings.eveningMinute,
        );
        await _scheduleSafe(
          id: 102,
          title: 'تذكير مسائي من رَتِّبْ 📝',
          body: 'سجّل مصاريف اليوم في 3 ثوانٍ لتبقى ميزانيتك متوازنة.',
          localTarget: nextEvening,
          match: DateTimeComponents.time,
        );
      }

      if (settings.weeklyMotivation) {
        final nextWeek = _nextWeeklyDateTime(DateTime.friday, 18, 0);
        final savedTxt = estimatedWeeklySaving > 0
            ? ArabicFormatters.formatMoney(estimatedWeeklySaving, currency)
            : 'خطوة ممتازة نحو أهدافك';
        await _scheduleSafe(
          id: 103,
          title: 'تقرير التحفيز الأسبوعي 🌟',
          body: 'وفرت هذا الأسبوع $savedTxt، أحسنت! واصل التزامك.',
          localTarget: nextWeek,
          match: DateTimeComponents.dayOfWeekAndTime,
        );
      }

      if (settings.billDueReminder) {
        final nextBill = _nextMonthlyDateTime(profile.billDueDay, 10, 0);
        await _scheduleSafe(
          id: 200,
          title: 'تذكير بموعد الفواتير والأقساط 🧾',
          body: 'تحقق من التزاماتك الثابتة اليوم لتتجنب أي غرامة أو نسيان.',
          localTarget: nextBill,
          match: DateTimeComponents.dayOfMonthAndTime,
        );

        for (var i = 0; i < debts.length && i < 10; i++) {
          final debt = debts[i];
          if (debt.remainingBalance <= 0) continue;
          final nextDebtDue = _nextMonthlyDateTime(debt.dueDay, 10, 30);
          await _scheduleSafe(
            id: 210 + i,
            title: 'موعد قسط: ${debt.name}',
            body:
                'القسط الشهري ${ArabicFormatters.formatMoney(debt.minimumMonthly, currency)} — سدادك المنتظم يقرّبك من الحرية المالية.',
            localTarget: nextDebtDue,
            match: DateTimeComponents.dayOfMonthAndTime,
          );
        }
      }
    } catch (e) {
      debugPrint('Notification sync skipped: $e');
    }
  }

  Future<void> showBudgetWarning({
    required String bucketTitleAr,
    required bool isExceeded,
    required String formattedDetail,
  }) async {
    if (!_initialized) return;
    try {
      await _plugin.show(
        300 + bucketTitleAr.hashCode.abs() % 50,
        isExceeded
            ? '⚠️ تجاوزت ميزانية $bucketTitleAr'
            : '🔔 اقتربت من نفاد ميزانية $bucketTitleAr',
        formattedDetail,
        _details,
      );
    } catch (_) {}
  }

  Future<void> showTestNotification() async {
    if (!_initialized) return;
    try {
      await _plugin.show(
        999,
        'نزل الراتب! خلينا نوزعه 💰',
        'الإشعارات في رَتِّبْ تعمل بنجاح — فلوسك بتستاهل خطة!',
        _details,
      );
    } catch (_) {}
  }

  Future<void> _scheduleSafe({
    required int id,
    required String title,
    required String body,
    required DateTime localTarget,
    DateTimeComponents? match,
  }) async {
    final now = DateTime.now();
    var delay = localTarget.difference(now);
    if (delay.isNegative || delay.inSeconds < 5) {
      delay = const Duration(minutes: 1);
    }
    final tzDate = tz.TZDateTime.now(tz.UTC).add(delay);

    try {
      await _plugin.zonedSchedule(
        id,
        title,
        body,
        tzDate,
        _details,
        androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
        matchDateTimeComponents: match,
      );
    } catch (_) {
      try {
        await _plugin.zonedSchedule(
          id,
          title,
          body,
          tzDate,
          _details,
          androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
          matchDateTimeComponents: match,
        );
      } catch (_) {}
    }
  }

  DateTime _nextDailyDateTime(int hour, int minute) {
    final now = DateTime.now();
    var target = DateTime(now.year, now.month, now.day, hour, minute);
    if (!target.isAfter(now)) {
      target = target.add(const Duration(days: 1));
    }
    return target;
  }

  DateTime _nextMonthlyDateTime(int dayOfMonth, int hour, int minute) {
    final now = DateTime.now();
    final daysInThisMonth = DateTime(now.year, now.month + 1, 0).day;
    final clamped = dayOfMonth.clamp(1, daysInThisMonth);
    var target = DateTime(now.year, now.month, clamped, hour, minute);
    if (!target.isAfter(now)) {
      final daysInNextMonth = DateTime(now.year, now.month + 2, 0).day;
      final clampedNext = dayOfMonth.clamp(1, daysInNextMonth);
      target = DateTime(now.year, now.month + 1, clampedNext, hour, minute);
    }
    return target;
  }

  DateTime _nextWeeklyDateTime(int weekday, int hour, int minute) {
    final now = DateTime.now();
    var target = DateTime(now.year, now.month, now.day, hour, minute);
    while (target.weekday != weekday || !target.isAfter(now)) {
      target = target.add(const Duration(days: 1));
    }
    return target;
  }
}
