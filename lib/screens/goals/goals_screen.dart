import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/constants/categories.dart';
import '../../core/utils/arabic_formatters.dart';
import '../../models/savings_goal.dart';
import '../../state/app_controller.dart';
import '../../widgets/confetti_overlay.dart';

class GoalsScreen extends StatefulWidget {
  const GoalsScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<GoalsScreen> createState() => _GoalsScreenState();
}

class _GoalsScreenState extends State<GoalsScreen> {
  bool _showConfetti = false;

  static const _goalPresets = [
    (key: 'car', label: 'سيارة', icon: Icons.directions_car_rounded),
    (key: 'travel', label: 'سفر', icon: Icons.flight_takeoff_rounded),
    (key: 'marriage', label: 'زواج', icon: Icons.favorite_rounded),
    (key: 'home', label: 'منزل / أثاث', icon: Icons.home_rounded),
    (key: 'education', label: 'تعليم', icon: Icons.school_rounded),
    (key: 'business', label: 'مشروع', icon: Icons.storefront_rounded),
    (key: 'emergency', label: 'طوارئ', icon: Icons.shield_rounded),
    (key: 'target', label: 'هدف آخر', icon: Icons.flag_rounded),
  ];

  IconData _iconFor(String key) {
    return _goalPresets
        .firstWhere(
          (g) => g.key == key,
          orElse: () => _goalPresets.last,
        )
        .icon;
  }

  Future<void> _openCreateGoalDialog() async {
    final titleCtrl = TextEditingController();
    final amountCtrl = TextEditingController();
    final monthsCtrl = TextEditingController(text: '12');
    final initialCtrl = TextEditingController();
    String selectedKey = 'car';

    final c = widget.controller;
    final currency = c.currency;
    final monthlySavingsCapacity = c.plan?.amountFor(BudgetBucket.savings) ?? 0;

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setModalState) {
            final target =
                double.tryParse(amountCtrl.text.replaceAll(',', '')) ?? 0;
            final initial =
                double.tryParse(initialCtrl.text.replaceAll(',', '')) ?? 0;
            final months = int.tryParse(monthsCtrl.text) ?? 1;
            final neededMonthly = months > 0
                ? ((target - initial).clamp(0, double.infinity) / months)
                : 0.0;

            return Container(
              padding: EdgeInsets.fromLTRB(
                20,
                16,
                20,
                22 + MediaQuery.of(ctx).viewInsets.bottom,
              ),
              decoration: BoxDecoration(
                color: Theme.of(ctx).cardColor,
                borderRadius: const BorderRadius.vertical(
                  top: Radius.circular(28),
                ),
              ),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      'إنشاء هدف جديد',
                      style: Theme.of(ctx).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: _goalPresets.map((p) {
                        final selected = p.key == selectedKey;
                        return ChoiceChip(
                          avatar: Icon(p.icon, size: 18),
                          label: Text(p.label),
                          selected: selected,
                          onSelected: (_) {
                            setModalState(() {
                              selectedKey = p.key;
                              if (titleCtrl.text.isEmpty ||
                                  _goalPresets.any(
                                    (e) => e.label == titleCtrl.text,
                                  )) {
                                titleCtrl.text = p.label;
                              }
                            });
                          },
                        );
                      }).toList(),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: titleCtrl,
                      decoration: const InputDecoration(
                        labelText: 'اسم الهدف (مثال: شراء سيارة أو سفرة)',
                        prefixIcon: Icon(Icons.flag_rounded),
                      ),
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      controller: amountCtrl,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      onChanged: (_) => setModalState(() {}),
                      decoration: InputDecoration(
                        labelText: 'المبلغ المطلوب (${currency.symbol})',
                        prefixIcon: const Icon(Icons.payments_rounded),
                      ),
                    ),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: monthsCtrl,
                            keyboardType: TextInputType.number,
                            inputFormatters: [
                              FilteringTextInputFormatter.digitsOnly,
                            ],
                            onChanged: (_) => setModalState(() {}),
                            decoration: const InputDecoration(
                              labelText: 'المدة (بالأشهر)',
                              prefixIcon: Icon(Icons.calendar_month_rounded),
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: TextField(
                            controller: initialCtrl,
                            keyboardType: TextInputType.number,
                            inputFormatters: [
                              FilteringTextInputFormatter.digitsOnly,
                            ],
                            onChanged: (_) => setModalState(() {}),
                            decoration: const InputDecoration(
                              labelText: 'مبلغ ابتدائي (اختياري)',
                              prefixIcon: Icon(Icons.savings_rounded),
                            ),
                          ),
                        ),
                      ],
                    ),
                    if (target > 0) ...[
                      const SizedBox(height: 12),
                      Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: Theme.of(ctx)
                              .colorScheme
                              .primary
                              .withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'تحتاج ادخار ${ArabicFormatters.formatMoney(neededMonthly, currency)} شهرياً',
                              style: Theme.of(ctx)
                                  .textTheme
                                  .titleMedium
                                  ?.copyWith(
                                    color: Theme.of(ctx).colorScheme.primary,
                                  ),
                            ),
                            if (monthlySavingsCapacity > 0)
                              Text(
                                neededMonthly <= monthlySavingsCapacity
                                    ? 'ممتاز! هذا المبلغ يناسب ميزانية الادخار في خطتك.'
                                    : 'ملاحظة: أكبر من بند الادخار الحالي (${ArabicFormatters.formatMoney(monthlySavingsCapacity, currency)}) — يمكنك تعديل المدة أو النسب.',
                                style: Theme.of(ctx).textTheme.bodyMedium,
                              ),
                          ],
                        ),
                      ),
                    ],
                    const SizedBox(height: 16),
                    FilledButton.icon(
                      onPressed: () async {
                        final t = titleCtrl.text.trim();
                        if (t.isEmpty || target <= 0 || months <= 0) {
                          ScaffoldMessenger.of(ctx).showSnackBar(
                            const SnackBar(
                              content: Text('أكمل اسم الهدف والمبلغ والمدة'),
                            ),
                          );
                          return;
                        }
                        Navigator.of(ctx).pop();
                        await c.addGoal(
                          title: t,
                          iconKey: selectedKey,
                          targetAmount: target,
                          durationMonths: months,
                          initialSaved: initial,
                        );
                      },
                      icon: const Icon(Icons.check_rounded),
                      label: const Text('حفظ الهدف'),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  Future<void> _openDepositDialog(SavingsGoal goal) async {
    final c = widget.controller;
    final currency = c.currency;
    final defaultDeposit =
        goal.monthlyRequired > 0 ? goal.monthlyRequired.round() : 100;
    final ctrl = TextEditingController(text: defaultDeposit.toString());

    final added = await showDialog<double>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('إيداع في هدف: ${goal.title}'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: ctrl,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(
                labelText: 'المبلغ المضاف (${currency.symbol})',
              ),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              children: [
                ActionChip(
                  label: Text(
                    'القسط الشهري (${ArabicFormatters.formatCompactMoney(goal.monthlyRequired, currency)})',
                  ),
                  onPressed: () =>
                      ctrl.text = goal.monthlyRequired.round().toString(),
                ),
                ActionChip(
                  label: const Text('إكمال الهدف بالكامل'),
                  onPressed: () =>
                      ctrl.text = goal.remainingAmount.ceil().toString(),
                ),
              ],
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('إلغاء'),
          ),
          FilledButton(
            onPressed: () {
              final v = double.tryParse(ctrl.text) ?? 0;
              Navigator.of(ctx).pop(v);
            },
            child: const Text('إضافة للهدف'),
          ),
        ],
      ),
    );

    if (added == null || added <= 0) return;
    final completedNow = await c.contributeToGoal(goal.id, added);
    if (completedNow && mounted) {
      setState(() => _showConfetti = true);
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('🎉 مبارك! حققت هدفك'),
          content: Text(
            'أتممت هدف «${goal.title}» بالكامل! استمر بنفس الحماس نحو هدفك القادم.',
            style: const TextStyle(fontFamily: 'Cairo', height: 1.5),
          ),
          actions: [
            FilledButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('رائع!'),
            ),
          ],
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final currency = c.currency;
    final theme = Theme.of(context);
    final goals = c.goals;

    return Stack(
      children: [
        ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
          children: [
            Card(
              color: theme.colorScheme.primary.withValues(alpha: 0.08),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'أهدافك تصنع مستقبلك',
                            style: theme.textTheme.titleLarge,
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'حدد المبلغ والمدة وسيحسب رَتِّبْ الادخار الشهري تلقائياً',
                            style: theme.textTheme.bodyMedium,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    FilledButton.icon(
                      style: FilledButton.styleFrom(
                        minimumSize: const Size(0, 46),
                        padding: const EdgeInsets.symmetric(horizontal: 14),
                      ),
                      onPressed: _openCreateGoalDialog,
                      icon: const Icon(Icons.add_task_rounded),
                      label: const Text('هدف جديد'),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 14),
            if (goals.isEmpty)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(28),
                  child: Column(
                    children: [
                      Icon(
                        Icons.flag_circle_rounded,
                        size: 62,
                        color: theme.colorScheme.secondary,
                      ),
                      const SizedBox(height: 10),
                      Text(
                        'لا توجد أهداف مضافة بعد',
                        style: theme.textTheme.titleLarge,
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'أنشئ هدفاً لسيارة، سفر، زواج، أو صندوق أمان وشاهد تقدمك بالقصاصات الملونة 🎉',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodyMedium,
                      ),
                      const SizedBox(height: 16),
                      OutlinedButton.icon(
                        onPressed: _openCreateGoalDialog,
                        icon: const Icon(Icons.add_rounded),
                        label: const Text('أضف أول هدف الآن'),
                      ),
                    ],
                  ),
                ),
              )
            else
              ...goals.map((goal) {
                final pct = (goal.progress * 100).round();
                final barColor = goal.isCompleted
                    ? const Color(0xFFD4AF37)
                    : theme.colorScheme.primary;

                return Card(
                  margin: const EdgeInsets.only(bottom: 12),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Row(
                          children: [
                            CircleAvatar(
                              radius: 24,
                              backgroundColor:
                                  barColor.withValues(alpha: 0.16),
                              child: Icon(
                                _iconFor(goal.iconKey),
                                color: barColor,
                                size: 26,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Expanded(
                                        child: Text(
                                          goal.title,
                                          style: theme.textTheme.titleLarge,
                                        ),
                                      ),
                                      if (goal.isCompleted)
                                        const Chip(
                                          label: Text('مكتمل 🎉'),
                                          visualDensity: VisualDensity.compact,
                                        ),
                                    ],
                                  ),
                                  Text(
                                    'الهدف: ${ArabicFormatters.formatMoney(goal.targetAmount, currency)} • المدة: ${goal.durationMonths} شهر',
                                    style: theme.textTheme.bodyMedium,
                                  ),
                                ],
                              ),
                            ),
                            IconButton(
                              onPressed: () => c.deleteGoal(goal.id),
                              icon: const Icon(Icons.delete_outline_rounded),
                            ),
                          ],
                        ),
                        const SizedBox(height: 14),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(12),
                          child: Container(
                            height: 13,
                            color: barColor.withValues(alpha: 0.15),
                            child: TweenAnimationBuilder<double>(
                              tween: Tween<double>(
                                begin: 0,
                                end: goal.progress,
                              ),
                              duration: const Duration(milliseconds: 650),
                              curve: Curves.easeOutCubic,
                              builder: (context, val, _) {
                                return FractionallySizedBox(
                                  alignment: AlignmentDirectional.centerStart,
                                  widthFactor: val,
                                  child: Container(
                                    decoration: BoxDecoration(
                                      gradient: LinearGradient(
                                        colors: [
                                          theme.colorScheme.primary,
                                          const Color(0xFFF5C443),
                                        ],
                                      ),
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                  ),
                                );
                              },
                            ),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              'المدخر: ${ArabicFormatters.formatMoney(goal.savedAmount, currency)} ($pct%)',
                              style: theme.textTheme.bodyLarge?.copyWith(
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            Text(
                              goal.isCompleted
                                  ? 'تم الوصول!'
                                  : 'اللازم شهرياً: ${ArabicFormatters.formatCompactMoney(goal.monthlyRequired, currency)}',
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: theme.colorScheme.primary,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: OutlinedButton.icon(
                                onPressed: goal.isCompleted
                                    ? () => setState(() => _showConfetti = true)
                                    : () => _openDepositDialog(goal),
                                icon: Icon(
                                  goal.isCompleted
                                      ? Icons.celebration_rounded
                                      : Icons.add_circle_outline_rounded,
                                ),
                                label: Text(
                                  goal.isCompleted
                                      ? 'احتفال بالإنجاز 🎉'
                                      : 'إضافة مبلغ للهدف',
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                );
              }),
          ],
        ),
        ConfettiOverlay(
          visible: _showConfetti,
          onFinished: () {
            if (mounted) setState(() => _showConfetti = false);
          },
        ),
      ],
    );
  }
}
