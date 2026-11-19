import 'package:flutter/material.dart';

import '../../core/constants/categories.dart';
import '../../core/utils/arabic_formatters.dart';
import '../../models/expense_item.dart';
import '../../state/app_controller.dart';
import '../../widgets/category_progress_bar.dart';
import '../../widgets/quick_expense_sheet.dart';
import '../../widgets/spending_bar_chart.dart';

class ExpensesScreen extends StatefulWidget {
  const ExpensesScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<ExpensesScreen> createState() => _ExpensesScreenState();
}

class _ExpensesScreenState extends State<ExpensesScreen> {
  bool _isMonthlyReport = false;
  BudgetBucket? _selectedBucketFilter;

  List<SpendingBarPoint> _buildWeeklyPoints() {
    final now = DateTime.now();
    const dayNames = [
      'إث',
      'ثل',
      'أر',
      'خم',
      'جم',
      'سب',
      'أح',
    ];
    final points = <SpendingBarPoint>[];
    for (int i = 6; i >= 0; i--) {
      final d = DateTime(now.year, now.month, now.day).subtract(
        Duration(days: i),
      );
      final total = widget.controller.expenses
          .where(
            (e) =>
                e.createdAt.year == d.year &&
                e.createdAt.month == d.month &&
                e.createdAt.day == d.day,
          )
          .fold<double>(0.0, (s, e) => s + e.amount);
      final label = i == 0 ? 'اليوم' : dayNames[d.weekday - 1];
      points.add(SpendingBarPoint(label: label, amount: total));
    }
    return points;
  }

  List<SpendingBarPoint> _buildMonthlyPoints() {
    final p = widget.controller.profile;
    final start = p != null
        ? ArabicFormatters.currentCycleStartForProfile(p)
        : ArabicFormatters.currentSalaryCycleStart(1);
    final cycleExpenses = widget.controller.currentCycleExpenses;
    final buckets = [0.0, 0.0, 0.0, 0.0];
    for (final e in cycleExpenses) {
      final days = e.createdAt.difference(start).inDays;
      final weekIdx = (days ~/ 7).clamp(0, 3);
      buckets[weekIdx] += e.amount;
    }
    return [
      SpendingBarPoint(label: 'أسبوع 1', amount: buckets[0]),
      SpendingBarPoint(label: 'أسبوع 2', amount: buckets[1]),
      SpendingBarPoint(label: 'أسبوع 3', amount: buckets[2]),
      SpendingBarPoint(label: 'أسبوع 4', amount: buckets[3]),
    ];
  }

  List<ExpenseItem> _quickRepeatSuggestions(List<ExpenseItem> all) {
    final seen = <String>{};
    final out = <ExpenseItem>[];
    for (final e in all) {
      final title = e.note.isEmpty
          ? AppCategories.categoryById(e.categoryId).nameAr
          : e.note;
      final key = '${e.categoryId}|$title|${e.amount.round()}';
      if (seen.add(key)) {
        out.add(e);
        if (out.length >= 4) break;
      }
    }
    return out;
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final plan = c.plan;
    final currency = c.currency;
    final theme = Theme.of(context);
    final spentByBucket = c.spentByBucketCurrentCycle;
    final isWeekly = c.profile?.isWeekly ?? false;

    final filteredExpenses = _selectedBucketFilter == null
        ? c.expenses
        : c.expenses.where((e) => e.bucket == _selectedBucketFilter).toList();
    final repeatSuggestions = _quickRepeatSuggestions(c.expenses);

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 116),
      children: [
        // Quick summary bar
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        isWeekly
                            ? 'مصروفك هالأسبوع حتى الآن'
                            : 'مصروفك هالشهر حتى الآن',
                        style: theme.textTheme.bodyMedium,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        ArabicFormatters.formatMoney(
                          c.totalSpentCurrentCycle,
                          currency,
                        ),
                        style: theme.textTheme.headlineMedium?.copyWith(
                          color: theme.colorScheme.primary,
                        ),
                      ),
                      if (c.spentToday > 0) ...[
                        const SizedBox(height: 2),
                        Text(
                          'صرفت اليوم: ${ArabicFormatters.formatMoney(c.spentToday, currency)}',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                FilledButton.icon(
                  style: FilledButton.styleFrom(
                    minimumSize: const Size(0, 46),
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                  ),
                  onPressed: () => showQuickExpenseSheet(context, c),
                  icon: const Icon(Icons.add_rounded),
                  label: const Text('أضف مصروف'),
                ),
              ],
            ),
          ),
        ),

        if (repeatSuggestions.isNotEmpty) ...[
          const SizedBox(height: 10),
          Card(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      Icon(
                        Icons.bolt_rounded,
                        size: 18,
                        color: theme.colorScheme.secondary,
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          'تكرار بلمسة واحدة (بدون كتابة)',
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontSize: 13.5,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 6,
                    children: repeatSuggestions.map((e) {
                      final cat = AppCategories.categoryById(e.categoryId);
                      final title = e.note.isEmpty ? cat.nameAr : e.note;
                      return ActionChip(
                        avatar: Icon(cat.icon, size: 16, color: cat.color),
                        label: Text(
                          '$title · ${ArabicFormatters.formatCompactMoney(e.amount, currency)}',
                          style: const TextStyle(
                            fontFamily: 'Cairo',
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        onPressed: () async {
                          await c.repeatExpense(e);
                          if (!context.mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(
                                'تم تسجيل «$title» (${ArabicFormatters.formatMoney(e.amount, currency)})',
                              ),
                              duration: const Duration(seconds: 2),
                            ),
                          );
                        },
                      );
                    }).toList(),
                  ),
                ],
              ),
            ),
          ),
        ],

        const SizedBox(height: 12),

        // Progress bars per budget bucket (Green -> Orange -> Red)
        if (plan != null) ...[
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('صرفك من كل ظرف', style: theme.textTheme.titleLarge),
                  const SizedBox(height: 12),
                  ...AppCategories.buckets
                      .where((b) => (c.exactPlanMoney[b.bucket] ?? 0.0) > 0)
                      .map((b) {
                    final planned = c.exactPlanMoney[b.bucket] ?? 0.0;
                    final spent = spentByBucket[b.bucket] ?? 0.0;
                    final ratio = planned > 0 ? (spent / planned) : 0.0;
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Row(
                            children: [
                              Icon(b.icon, size: 18, color: b.color),
                              const SizedBox(width: 6),
                              Expanded(
                                child: Text(
                                  b.titleAr,
                                  style: theme.textTheme.titleMedium,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const SizedBox(width: 8),
                              Flexible(
                                child: Text(
                                  '${ArabicFormatters.formatCompactMoney(spent, currency)} / ${ArabicFormatters.formatCompactMoney(planned, currency)}',
                                  textAlign: TextAlign.end,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: theme.textTheme.bodyMedium?.copyWith(
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          CategoryProgressBar(ratio: ratio, height: 10),
                        ],
                      ),
                    );
                  }),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
        ],

        // Weekly & Monthly report chart
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  _isMonthlyReport
                      ? 'التقرير الدوري (أسابيع الدورة)'
                      : 'التقرير الأسبوعي (آخر 7 أيام)',
                  style: theme.textTheme.titleMedium,
                ),
                const SizedBox(height: 10),
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: SegmentedButton<bool>(
                    segments: const [
                      ButtonSegment(value: false, label: Text('آخر 7 أيام')),
                      ButtonSegment(value: true, label: Text('بالأسابيع')),
                    ],
                    selected: {_isMonthlyReport},
                    onSelectionChanged: (s) =>
                        setState(() => _isMonthlyReport = s.first),
                  ),
                ),
                const SizedBox(height: 14),
                SpendingTrendBarChart(
                  points: _isMonthlyReport
                      ? _buildMonthlyPoints()
                      : _buildWeeklyPoints(),
                  currency: currency,
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 12),

        // Plan vs Reality Chart
        if (plan != null) ...[
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    'مقارنة بين الخطة والواقع',
                    style: theme.textTheme.titleLarge,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'قارن ما خططت لصرفه في كل بند مع مصروفك الفعلي',
                    style: theme.textTheme.bodyMedium,
                  ),
                  const SizedBox(height: 14),
                  PlanVsActualBarChart(
                    plan: plan,
                    spentByBucket: spentByBucket,
                    currency: currency,
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 14),
        ],

        // Filter chips + Expense Log
        Text('سجل المصاريف', style: theme.textTheme.titleLarge),
        const SizedBox(height: 8),
        SizedBox(
          height: 42,
          child: ListView(
            scrollDirection: Axis.horizontal,
            children: [
              ChoiceChip(
                label: const Text('الكل'),
                selected: _selectedBucketFilter == null,
                onSelected: (_) => setState(() => _selectedBucketFilter = null),
              ),
              const SizedBox(width: 8),
              ...AppCategories.buckets.map((b) {
                return Padding(
                  padding: const EdgeInsets.only(left: 8),
                  child: ChoiceChip(
                    label: Text(b.titleAr),
                    selected: _selectedBucketFilter == b.bucket,
                    onSelected: (_) => setState(() => _selectedBucketFilter = b.bucket),
                  ),
                );
              }),
            ],
          ),
        ),
        const SizedBox(height: 10),

        if (filteredExpenses.isEmpty)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                children: [
                  Icon(
                    Icons.receipt_long_rounded,
                    size: 48,
                    color: theme.colorScheme.primary.withValues(alpha: 0.45),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    'لا توجد مصاريف مسجلة هنا بعد',
                    style: theme.textTheme.titleMedium,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'اضغط زر + لإضافة مصروف خلال 3 ثوانٍ',
                    style: theme.textTheme.bodyMedium,
                  ),
                ],
              ),
            ),
          )
        else
          ...filteredExpenses.map((e) {
            final cat = AppCategories.categoryById(e.categoryId);
            return Dismissible(
              key: ValueKey(e.id),
              direction: DismissDirection.endToStart,
              background: Container(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.symmetric(horizontal: 20),
                alignment: AlignmentDirectional.centerEnd,
                decoration: BoxDecoration(
                  color: const Color(0xFFEF4444),
                  borderRadius: BorderRadius.circular(18),
                ),
                child: const Icon(Icons.delete_rounded, color: Colors.white),
              ),
              onDismissed: (_) => c.deleteExpense(e.id),
              child: Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  leading: CircleAvatar(
                    backgroundColor: cat.color.withValues(alpha: 0.14),
                    child: Icon(cat.icon, color: cat.color),
                  ),
                  title: Text(
                    e.note.isEmpty ? cat.nameAr : e.note,
                    style: theme.textTheme.titleMedium,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  subtitle: Text(
                    '${cat.nameAr} • ${ArabicFormatters.friendlyDayLabelAr(e.createdAt)}',
                    style: theme.textTheme.bodyMedium?.copyWith(fontSize: 12),
                  ),
                  trailing: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 185),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Flexible(
                          child: Text(
                            ArabicFormatters.formatMoney(e.amount, currency),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.titleMedium?.copyWith(
                              color: const Color(0xFFEF4444),
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                        IconButton(
                          tooltip: 'كرّر اليوم',
                          visualDensity: VisualDensity.compact,
                          icon: Icon(
                            Icons.replay_rounded,
                            size: 19,
                            color: theme.colorScheme.primary,
                          ),
                          onPressed: () => c.repeatExpense(e),
                        ),
                        IconButton(
                          tooltip: 'حذف',
                          visualDensity: VisualDensity.compact,
                          icon: const Icon(
                            Icons.delete_outline_rounded,
                            size: 19,
                          ),
                          onPressed: () => c.deleteExpense(e.id),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          }),
      ],
    );
  }
}
