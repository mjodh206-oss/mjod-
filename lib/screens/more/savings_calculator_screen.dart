import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/utils/arabic_formatters.dart';
import '../../state/app_controller.dart';

class SavingsCalculatorScreen extends StatefulWidget {
  const SavingsCalculatorScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<SavingsCalculatorScreen> createState() =>
      _SavingsCalculatorScreenState();
}

class _SavingsCalculatorScreenState extends State<SavingsCalculatorScreen> {
  final TextEditingController _targetCtrl = TextEditingController();
  final TextEditingController _monthlyCtrl = TextEditingController();
  final TextEditingController _monthsCtrl = TextEditingController(text: '12');
  bool _solveForTime = true; // true = given monthly -> months; false = given months -> monthly

  @override
  void dispose() {
    _targetCtrl.dispose();
    _monthlyCtrl.dispose();
    _monthsCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final currency = c.currency;
    final theme = Theme.of(context);
    final isWeekly = c.profile?.isWeekly ?? false;
    final salary = c.profile?.totalMonthlyIncome ?? 0;
    final unitAdj = isWeekly ? 'أسبوعياً' : 'شهرياً';
    final unitSingular = isWeekly ? 'أسبوع' : 'شهر';

    final target = double.tryParse(_targetCtrl.text.replaceAll(',', '')) ?? 0;
    final monthly = double.tryParse(_monthlyCtrl.text.replaceAll(',', '')) ?? 0;
    final months = int.tryParse(_monthsCtrl.text) ?? 12;

    String resultTitle = 'أدخل المبلغ لتحصل على الحساب الفوري';
    String resultSubtitle = '';
    double salarySharePct = 0;

    if (target > 0) {
      if (_solveForTime && monthly > 0) {
        final neededCycles = (target / monthly).ceil();
        if (isWeekly) {
          final monthsApprox = (neededCycles / 4.33).ceil();
          resultTitle = 'تحتاج إلى $neededCycles $unitSingular (~$monthsApprox شهر)';
        } else {
          final years = neededCycles ~/ 12;
          final remMonths = neededCycles % 12;
          final durationTxt = years > 0
              ? '$years سنة${remMonths > 0 ? ' و $remMonths شهر' : ''}'
              : '$neededCycles شهر';
          resultTitle = 'تحتاج إلى $durationTxt';
        }
        resultSubtitle =
            'عند ادخار ${ArabicFormatters.formatMoney(monthly, currency)} $unitAdj ستصل إلى ${ArabicFormatters.formatMoney(target, currency)}.';
        if (salary > 0) salarySharePct = (monthly / salary) * 100;
      } else if (!_solveForTime && months > 0) {
        final neededPerCycle = target / math.max(1, months);
        resultTitle =
            'تحتاج ادخار ${ArabicFormatters.formatMoney(neededPerCycle, currency)} $unitAdj';
        resultSubtitle =
            'للوصول إلى ${ArabicFormatters.formatMoney(target, currency)} خلال $months $unitSingular.';
        if (salary > 0) salarySharePct = (neededPerCycle / salary) * 100;
      }
    }

    return Scaffold(
      appBar: AppBar(title: const Text('حاسبة الادخار السريعة')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(
            'كم أحتاج لأدخر مبلغاً معيناً؟',
            style: theme.textTheme.headlineMedium,
          ),
          const SizedBox(height: 6),
          Text(
            'احسب المدة اللازمة أو المبلغ $unitAdj المطلوب لأي هدف.',
            style: theme.textTheme.bodyMedium,
          ),
          const SizedBox(height: 16),
          SegmentedButton<bool>(
            segments: [
              ButtonSegment(value: true, label: Text('احسب كم $unitSingular أحتاج')),
              ButtonSegment(value: false, label: Text('احسب كم أدخر $unitAdj')),
            ],
            selected: {_solveForTime},
            onSelectionChanged: (s) => setState(() => _solveForTime = s.first),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _targetCtrl,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              labelText: 'المبلغ المطلوب ادخاره (${currency.symbol})',
              prefixIcon: const Icon(Icons.flag_rounded),
            ),
          ),
          const SizedBox(height: 12),
          if (_solveForTime)
            TextField(
              controller: _monthlyCtrl,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                labelText: 'كم تستطيع أن تدخر $unitAdj؟ (${currency.symbol})',
                prefixIcon: const Icon(Icons.savings_rounded),
              ),
            )
          else
            TextField(
              controller: _monthsCtrl,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                labelText: 'خلال كم $unitSingular تريد الوصول؟',
                prefixIcon: const Icon(Icons.calendar_month_rounded),
              ),
            ),
          const SizedBox(height: 22),
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(22),
              gradient: const LinearGradient(
                colors: [Color(0xFF063C2B), Color(0xFF0C684A)],
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  resultTitle,
                  style: const TextStyle(
                    fontFamily: 'Cairo',
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFFF5C443),
                  ),
                ),
                if (resultSubtitle.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Text(
                    resultSubtitle,
                    style: const TextStyle(
                      fontFamily: 'Cairo',
                      fontSize: 14.5,
                      color: Colors.white,
                      height: 1.5,
                    ),
                  ),
                ],
                if (salarySharePct > 0) ...[
                  const SizedBox(height: 10),
                  Chip(
                    backgroundColor: Colors.black.withValues(alpha: 0.25),
                    label: Text(
                      'يمثل ${salarySharePct.toStringAsFixed(1)}% من راتبك الشهري',
                      style: const TextStyle(
                        fontFamily: 'Cairo',
                        color: Color(0xFFD7F3E6),
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
