import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/constants/categories.dart';
import '../../core/constants/financial_tips_data.dart';
import '../../core/utils/arabic_formatters.dart';
import '../../models/debt_item.dart';
import '../../services/plan_engine.dart';
import '../../state/app_controller.dart';

class AdviceScreen extends StatefulWidget {
  const AdviceScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<AdviceScreen> createState() => _AdviceScreenState();
}

class _AdviceScreenState extends State<AdviceScreen> {
  double _extraDebtMonthly = 0;

  Future<void> _openAddDebtDialog() async {
    final nameCtrl = TextEditingController();
    final balanceCtrl = TextEditingController();
    final minCtrl = TextEditingController();
    final interestCtrl = TextEditingController(text: '0');
    int dueDay = 10;

    final c = widget.controller;
    final currency = c.currency;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDlgState) => AlertDialog(
          title: const Text('إضافة دين أو قسط'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(
                    labelText: 'اسم الدين (مثال: قرض أو جمعية أو محل)',
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: balanceCtrl,
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  decoration: InputDecoration(
                    labelText: 'المبلغ المتبقي (${currency.symbol})',
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: minCtrl,
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  decoration: InputDecoration(
                    labelText: 'القسط الشهري (${currency.symbol})',
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: interestCtrl,
                  keyboardType:
                      const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(
                    labelText: 'نسبة الفائدة السنوية % (0 إذا بدون فائدة)',
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text('يوم السداد الشهري:'),
                    DropdownButton<int>(
                      value: dueDay,
                      items: List.generate(28, (i) => i + 1)
                          .map((d) => DropdownMenuItem(value: d, child: Text('يوم $d')))
                          .toList(),
                      onChanged: (v) {
                        if (v != null) setDlgState(() => dueDay = v);
                      },
                    ),
                  ],
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('إلغاء'),
            ),
            FilledButton(
              onPressed: () async {
                final name = nameCtrl.text.trim();
                final bal = double.tryParse(balanceCtrl.text) ?? 0;
                final minPay = double.tryParse(minCtrl.text) ?? 0;
                final rate = double.tryParse(interestCtrl.text) ?? 0;
                if (name.isEmpty || bal <= 0 || minPay <= 0) return;

                Navigator.of(ctx).pop();
                await c.addDebt(
                  DebtItem(
                    id: 'debt_${DateTime.now().microsecondsSinceEpoch}',
                    name: name,
                    remainingBalance: bal,
                    minimumMonthly: minPay,
                    interestRatePercent: rate,
                    dueDay: dueDay,
                  ),
                );
              },
              child: const Text('حفظ'),
            ),
          ],
        ),
      ),
    );
  }

  Color _severityColor(AdviceSeverity s) {
    switch (s) {
      case AdviceSeverity.critical:
        return const Color(0xFFEF4444);
      case AdviceSeverity.warning:
        return const Color(0xFFF59E0B);
      case AdviceSeverity.positive:
        return const Color(0xFF10B981);
      case AdviceSeverity.info:
        return const Color(0xFF0EA5E9);
    }
  }

  IconData _severityIcon(AdviceSeverity s) {
    switch (s) {
      case AdviceSeverity.critical:
        return Icons.warning_rounded;
      case AdviceSeverity.warning:
        return Icons.info_rounded;
      case AdviceSeverity.positive:
        return Icons.verified_rounded;
      case AdviceSeverity.info:
        return Icons.lightbulb_rounded;
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final theme = Theme.of(context);
    final currency = c.currency;
    final smartAdvice = c.smartAdvice;
    final completedDays = c.settings.completedChallengeDays;
    final strategy = c.settings.debtStrategy;

    final simResult = PlanEngine.simulateDebtPayoff(
      debts: c.debts,
      extraMonthlyPayment: _extraDebtMonthly,
      strategy: strategy,
    );

    final maxExtra = (c.plan?.amountFor(BudgetBucket.wants) ?? 0)
        .clamp(0.0, double.infinity);

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
      children: [
        // Tip of the day card
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(22),
            gradient: const LinearGradient(
              colors: [Color(0xFF06402E), Color(0xFF0D6E4F)],
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(
                    Icons.wb_sunny_rounded,
                    color: Color(0xFFF5C443),
                    size: 24,
                  ),
                  const SizedBox(width: 8),
                  const Text(
                    'نصيحة اليوم',
                    style: TextStyle(
                      fontFamily: 'Cairo',
                      color: Color(0xFFF5C443),
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const Spacer(),
                  Text(
                    ArabicFormatters.formatShortDate(DateTime.now()),
                    style: const TextStyle(
                      fontFamily: 'Cairo',
                      color: Color(0xFFD7F3E6),
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Text(
                FinancialTipsData.tipOfToday(),
                style: const TextStyle(
                  fontFamily: 'Cairo',
                  color: Colors.white,
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                  height: 1.55,
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 16),

        // Personalized advice section
        Text('نصائح مخصصة لوضعك', style: theme.textTheme.titleLarge),
        const SizedBox(height: 8),
        if (smartAdvice.isEmpty)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Text(
                'أكمل إعداد خطتك لتحصل على نصائح ذكية مخصصة لوضعك المالي.',
                style: theme.textTheme.bodyMedium,
              ),
            ),
          )
        else
          ...smartAdvice.map((item) {
            final color = _severityColor(item.severity);
            return Card(
              margin: const EdgeInsets.only(bottom: 10),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(_severityIcon(item.severity), color: color),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            item.titleAr,
                            style: theme.textTheme.titleMedium?.copyWith(
                              color: color,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      item.bodyAr,
                      style: theme.textTheme.bodyLarge?.copyWith(height: 1.5),
                    ),
                    if (item.actionStepsAr.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      ...item.actionStepsAr.map(
                        (s) => Padding(
                          padding: const EdgeInsets.only(bottom: 4),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '• ',
                                style: TextStyle(
                                  color: color,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              Expanded(
                                child: Text(
                                  s,
                                  style: theme.textTheme.bodyMedium,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            );
          }),

        const SizedBox(height: 16),

        // Debt Payoff Strategy Card (Snowball vs Avalanche)
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.credit_score_rounded,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'طريقة سداد الديون',
                        style: theme.textTheme.titleLarge,
                      ),
                    ),
                    TextButton.icon(
                      onPressed: _openAddDebtDialog,
                      icon: const Icon(Icons.add_rounded),
                      label: const Text('إضافة دين'),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                SegmentedButton<DebtStrategy>(
                  segments: const [
                    ButtonSegment(
                      value: DebtStrategy.snowball,
                      label: Text('كرة الثلج (الأصغر أولاً)'),
                    ),
                    ButtonSegment(
                      value: DebtStrategy.avalanche,
                      label: Text('الانهيار الجليدي (الأعلى فائدة)'),
                    ),
                  ],
                  selected: {strategy},
                  onSelectionChanged: (s) => c.setDebtStrategy(s.first),
                ),
                const SizedBox(height: 10),
                Text(
                  strategy == DebtStrategy.snowball
                      ? 'كرة الثلج: تسدد أصغر دين أولاً لتنتهي منه بسرعة وتشعر بحماس الإنجاز ثم تنقل قسطه للدين التالي.'
                      : 'الانهيار الجليدي: تسدد الدين صاحب أعلى نسبة فائدة أولاً لتوفر أكبر مبلغ ممكن على المدى الطويل.',
                  style: theme.textTheme.bodyMedium,
                ),
                if (c.debts.isNotEmpty) ...[
                  if (maxExtra > 0) ...[
                    const SizedBox(height: 10),
                    Text(
                      'دفعة شهرية إضافية لتسريع السداد: ${ArabicFormatters.formatMoney(_extraDebtMonthly, currency)}',
                      style: theme.textTheme.bodyLarge,
                    ),
                    Slider(
                      value: _extraDebtMonthly.clamp(0.0, maxExtra),
                      min: 0,
                      max: maxExtra,
                      divisions: 10,
                      onChanged: (v) => setState(() => _extraDebtMonthly = v),
                    ),
                  ],
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primary.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Text(
                      'تتحرر من جميع ديونك خلال ${simResult.totalMonths} شهر'
                      '${simResult.totalInterestPaid > 0 ? ' (إجمالي الفائدة المتوقعة: ${ArabicFormatters.formatMoney(simResult.totalInterestPaid, currency)})' : ''}',
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: theme.colorScheme.primary,
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  ...simResult.steps.map((step) {
                    return ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: CircleAvatar(
                        backgroundColor: theme.colorScheme.secondary
                            .withValues(alpha: 0.2),
                        child: Text(
                          '${step.orderIndex}',
                          style: const TextStyle(fontWeight: FontWeight.w800),
                        ),
                      ),
                      title: Text(step.debt.name),
                      subtitle: Text(
                        'المتبقي: ${ArabicFormatters.formatMoney(step.debt.remainingBalance, currency)} • القسط: ${ArabicFormatters.formatCompactMoney(step.debt.minimumMonthly, currency)} • ينتهي بشهر ${step.payoffMonth}',
                      ),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline_rounded),
                        onPressed: () => c.deleteDebt(step.debt.id),
                      ),
                    );
                  }),
                ] else ...[
                  const SizedBox(height: 10),
                  Text(
                    'أضف ديونك أو أقساطك هنا ليحسب التطبيق ترتيب السداد الأمثل.',
                    style: theme.textTheme.bodyMedium,
                  ),
                ],
              ],
            ),
          ),
        ),

        const SizedBox(height: 16),

        // 30-Day Expense Reduction Challenge
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.emoji_events_rounded,
                      color: theme.colorScheme.secondary,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'خطة تحدي الـ 30 يوم لتقليل المصاريف',
                        style: theme.textTheme.titleLarge,
                      ),
                    ),
                    Text(
                      '${completedDays.length}/30',
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: theme.colorScheme.primary,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                LinearProgressIndicator(
                  value: completedDays.length / 30.0,
                  minHeight: 8,
                  borderRadius: BorderRadius.circular(10),
                ),
                const SizedBox(height: 12),
                ...FinancialTipsData.thirtyDayChallenge.map((ch) {
                  final done = completedDays.contains(ch.day);
                  return CheckboxListTile(
                    value: done,
                    onChanged: (_) => c.toggleChallengeDay(ch.day),
                    controlAffinity: ListTileControlAffinity.leading,
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      'اليوم ${ch.day}: ${ch.titleAr}',
                      style: theme.textTheme.titleMedium?.copyWith(
                        decoration: done ? TextDecoration.lineThrough : null,
                        color: done ? Colors.grey : null,
                      ),
                    ),
                    subtitle: Text(
                      ch.actionAr,
                      style: theme.textTheme.bodyMedium,
                    ),
                  );
                }),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
