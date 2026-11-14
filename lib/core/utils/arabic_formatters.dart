import 'package:intl/intl.dart';
import '../constants/currencies.dart';

class ArabicFormatters {
  ArabicFormatters._();

  static final NumberFormat _intFormat = NumberFormat('#,##0', 'en_US');
  static final NumberFormat _decFormat = NumberFormat('#,##0.##', 'en_US');

  static String formatNumber(double value, {bool allowDecimals = false}) {
    if (!allowDecimals || value == value.roundToDouble()) {
      return _intFormat.format(value.round());
    }
    return _decFormat.format(value);
  }

  static String formatMoney(double value, AppCurrency currency) {
    final formatted = formatNumber(value, allowDecimals: currency.allowDecimals);
    return '$formatted ${currency.symbol}';
  }

  static String formatCompactMoney(double value, AppCurrency currency) {
    final absVal = value.abs();
    final sign = value < 0 ? '-' : '';
    if (absVal >= 1000000000) {
      return '$sign${(absVal / 1000000000).toStringAsFixed(1)}B ${currency.symbol}';
    }
    if (absVal >= 1000000) {
      return '$sign${(absVal / 1000000).toStringAsFixed(1)}M ${currency.symbol}';
    }
    if (absVal >= 10000) {
      return '$sign${(absVal / 1000).toStringAsFixed(1)}K ${currency.symbol}';
    }
    return formatMoney(value, currency);
  }

  static String formatDateAr(DateTime date) {
    const months = [
      'كانون الثاني',
      'شباط',
      'آذار',
      'نيسان',
      'أيار',
      'حزيران',
      'تموز',
      'آب',
      'أيلول',
      'تشرين الأول',
      'تشرين الثاني',
      'كانون الأول',
    ];
    return '${date.day} ${months[date.month - 1]} ${date.year}';
  }

  static String formatShortDate(DateTime date) {
    return '${date.day}/${date.month}';
  }

  static int daysUntilSalary(int salaryDay, [DateTime? now]) {
    final today = now ?? DateTime.now();
    final currentMonthDays = DateTime(today.year, today.month + 1, 0).day;
    final clampedThisMonth = salaryDay.clamp(1, currentMonthDays);
    var nextPay = DateTime(today.year, today.month, clampedThisMonth);
    final todayOnly = DateTime(today.year, today.month, today.day);
    if (!nextPay.isAfter(todayOnly)) {
      final nextMonthDays = DateTime(today.year, today.month + 2, 0).day;
      final clampedNextMonth = salaryDay.clamp(1, nextMonthDays);
      nextPay = DateTime(today.year, today.month + 1, clampedNextMonth);
    }
    return nextPay.difference(todayOnly).inDays;
  }

  static DateTime currentSalaryCycleStart(int salaryDay, [DateTime? now]) {
    final today = now ?? DateTime.now();
    final currentMonthDays = DateTime(today.year, today.month + 1, 0).day;
    final clampedThisMonth = salaryDay.clamp(1, currentMonthDays);
    final thisMonthPay = DateTime(today.year, today.month, clampedThisMonth);
    final todayOnly = DateTime(today.year, today.month, today.day);
    if (!todayOnly.isBefore(thisMonthPay)) {
      return thisMonthPay;
    }
    final prevMonthDays = DateTime(today.year, today.month, 0).day;
    final clampedPrev = salaryDay.clamp(1, prevMonthDays);
    return DateTime(today.year, today.month - 1, clampedPrev);
  }
}
