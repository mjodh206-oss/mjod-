import 'package:flutter/material.dart';

import '../../core/constants/categories.dart';
import '../../core/constants/currencies.dart';
import '../../core/utils/arabic_formatters.dart';
import '../../models/budget_plan.dart';
import '../../models/expense_item.dart';
import '../../widgets/category_progress_bar.dart';

Future<void> showBucketDetailSheet({
  required BuildContext context,
  required BucketMeta meta,
  required BudgetPlan plan,
  required double spent,
  required AppCurrency currency,
  required List<ExpenseItem> bucketExpenses,
}) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: Colors.transparent,
    builder: (ctx) => _BucketDetailSheet(
      meta: meta,
      plan: plan,
      spent: spent,
      currency: currency,
      bucketExpenses: bucketExpenses,
    ),
  );
}

class _BucketDetailSheet extends StatelessWidget {
  const _BucketDetailSheet({
    required this.meta,
    required this.plan,
    required this.spent,
    required this.currency,
    required this.bucketExpenses,
  });

  final BucketMeta meta;
  final BudgetPlan plan;
  final double spent;
  final AppCurrency currency;
  final List<ExpenseItem> bucketExpenses;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final planned = plan.amountFor(meta.bucket);
    final pct = plan.percentageFor(meta.bucket);
    final remaining = planned - spent;
    final ratio = planned > 0 ? (spent / planned) : 0.0;

    return Container(
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 24),
      decoration: BoxDecoration(
        color: theme.cardColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 44,
                height: 5,
                decoration: BoxDecoration(
                  color: Colors.grey.withValues(alpha: 0.35),
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: meta.color.withValues(alpha: 0.16),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Icon(meta.icon, color: meta.color, size: 28),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${meta.titleAr} (${pct.toStringAsFixed(1)}%)',
                        style: theme.textTheme.titleLarge,
                      ),
                      Text(meta.subtitleAr, style: theme.textTheme.bodyMedium),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
            ),
            const SizedBox(height: 18),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: theme.scaffoldBackgroundColor,
                borderRadius: BorderRadius.circular(18),
              ),
              child: Column(
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: _statCol(
                          'المخصص شهرياً',
                          ArabicFormatters.formatMoney(planned, currency),
                          meta.color,
                        ),
                      ),
                      Expanded(
                        child: _statCol(
                          'المصروف فعلياً',
                          ArabicFormatters.formatMoney(spent, currency),
                          theme.textTheme.bodyLarge?.color ?? Colors.black,
                        ),
                      ),
                      Expanded(
                        child: _statCol(
                          remaining >= 0 ? 'المتبقي' : 'التجاوز',
                          ArabicFormatters.formatMoney(remaining.abs(), currency),
                          remaining >= 0
                              ? const Color(0xFF10B981)
                              : const Color(0xFFEF4444),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  CategoryProgressBar(ratio: ratio, height: 11),
                ],
              ),
            ),
            const SizedBox(height: 14),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: meta.color.withValues(alpha: 0.09),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: meta.color.withValues(alpha: 0.25)),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.lightbulb_rounded, color: meta.color, size: 22),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      meta.ruleDescriptionAr,
                      style: theme.textTheme.bodyLarge?.copyWith(height: 1.5),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'عمليات هذا البند (${bucketExpenses.length})',
              style: theme.textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            if (bucketExpenses.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  'لم تسجل أي مصاريف في هذا البند بعد خلال هذه الدورة.',
                  style: theme.textTheme.bodyMedium,
                  textAlign: TextAlign.center,
                ),
              )
            else
              ...bucketExpenses.take(6).map((e) {
                final cat = AppCategories.categoryById(e.categoryId);
                return ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: CircleAvatar(
                    backgroundColor: cat.color.withValues(alpha: 0.14),
                    child: Icon(cat.icon, color: cat.color, size: 20),
                  ),
                  title: Text(
                    e.note.isEmpty ? cat.nameAr : e.note,
                    style: theme.textTheme.bodyLarge,
                  ),
                  subtitle: Text(
                    ArabicFormatters.formatShortDate(e.createdAt),
                    style: theme.textTheme.bodyMedium,
                  ),
                  trailing: Text(
                    ArabicFormatters.formatMoney(e.amount, currency),
                    style: theme.textTheme.titleMedium,
                  ),
                );
              }),
          ],
        ),
      ),
    );
  }

  Widget _statCol(String label, String value, Color color) {
    return Column(
      children: [
        Text(
          label,
          style: const TextStyle(fontFamily: 'Cairo', fontSize: 11.5),
        ),
        const SizedBox(height: 3),
        Text(
          value,
          style: TextStyle(
            fontFamily: 'Cairo',
            fontSize: 13.5,
            fontWeight: FontWeight.w800,
            color: color,
          ),
        ),
      ],
    );
  }
}
