import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/constants/categories.dart';
import '../core/utils/arabic_formatters.dart';
import '../state/app_controller.dart';

Future<void> showQuickExpenseSheet(
  BuildContext context,
  AppController controller, {
  String? initialCategoryId,
}) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: Colors.transparent,
    builder: (ctx) => _QuickExpenseSheet(
      controller: controller,
      initialCategoryId: initialCategoryId,
    ),
  );
}

class _QuickExpenseSheet extends StatefulWidget {
  const _QuickExpenseSheet({
    required this.controller,
    this.initialCategoryId,
  });

  final AppController controller;
  final String? initialCategoryId;

  @override
  State<_QuickExpenseSheet> createState() => _QuickExpenseSheetState();
}

class _QuickExpenseSheetState extends State<_QuickExpenseSheet> {
  final TextEditingController _amountCtrl = TextEditingController();
  final TextEditingController _noteCtrl = TextEditingController();
  late String _selectedCategoryId;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _selectedCategoryId =
        widget.initialCategoryId ?? AppCategories.expenseCategories.first.id;
  }

  @override
  void dispose() {
    _amountCtrl.dispose();
    _noteCtrl.dispose();
    super.dispose();
  }

  List<double> _smartQuickChips(double salary) {
    if (salary >= 5000000) return [100000, 250000, 500000, 1000000];
    if (salary >= 500000) return [10000, 25000, 50000, 100000];
    if (salary >= 50000) return [500, 1000, 2500, 5000];
    if (salary >= 10000) return [100, 250, 500, 1000];
    if (salary >= 3000) return [25, 50, 100, 250];
    return [5, 10, 25, 50];
  }

  Future<void> _save() async {
    final normalized = _amountCtrl.text
        .replaceAll(',', '')
        .replaceAll(' ', '')
        .trim();
    final amount = double.tryParse(normalized);
    if (amount == null || amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('أدخل مبلغاً صحيحاً أكبر من صفر')),
      );
      return;
    }

    setState(() => _saving = true);
    final res = await widget.controller.addExpense(
      amount: amount,
      categoryId: _selectedCategoryId,
      note: _noteCtrl.text,
    );

    if (!mounted) return;
    Navigator.of(context).pop();

    final messenger = ScaffoldMessenger.of(context);
    if (res.budgetAlertMessage != null) {
      messenger.showSnackBar(
        SnackBar(
          backgroundColor: res.isOverBudget
              ? const Color(0xFFDC2626)
              : const Color(0xFFD97706),
          content: Text(
            res.budgetAlertMessage!,
            style: const TextStyle(
              fontFamily: 'Cairo',
              fontWeight: FontWeight.w700,
              color: Colors.white,
            ),
          ),
          duration: const Duration(seconds: 4),
        ),
      );
    } else {
      final currency = widget.controller.currency;
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            'تم خصم ${ArabicFormatters.formatMoney(amount, currency)} ✓',
            style: const TextStyle(
              fontFamily: 'Cairo',
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final currency = c.currency;
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    final theme = Theme.of(context);

    final salary = c.profile?.totalMonthlyIncome ?? currency.sampleSalary;
    final quickChips = _smartQuickChips(salary);

    final selectedCat = AppCategories.categoryById(_selectedCategoryId);
    final bucketMeta = AppCategories.bucketMeta(selectedCat.bucket);
    final plannedBucket = c.exactPlanMoney[selectedCat.bucket] ?? 0.0;
    final spentBucket = c.spentByBucketCurrentCycle[selectedCat.bucket] ?? 0.0;
    final leftBucket = plannedBucket - spentBucket;

    return Container(
      padding: EdgeInsets.fromLTRB(20, 14, 20, 20 + bottomInset),
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
            const SizedBox(height: 14),
            Row(
              children: [
                Icon(
                  Icons.bolt_rounded,
                  color: theme.colorScheme.secondary,
                  size: 26,
                ),
                const SizedBox(width: 8),
                Text(
                  'سجّل مصروف سريع',
                  style: theme.textTheme.titleLarge,
                ),
                const Spacer(),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
            ),
            const SizedBox(height: 10),
            TextField(
              controller: _amountCtrl,
              autofocus: true,
              keyboardType: TextInputType.numberWithOptions(
                decimal: currency.allowDecimals,
              ),
              inputFormatters: [
                FilteringTextInputFormatter.allow(
                  currency.allowDecimals
                      ? RegExp(r'[0-9.]')
                      : RegExp(r'[0-9]'),
                ),
              ],
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontFamily: 'Cairo',
                fontSize: 30,
                fontWeight: FontWeight.w800,
              ),
              decoration: InputDecoration(
                hintText: '0',
                suffixText: currency.symbol,
                suffixStyle: TextStyle(
                  fontFamily: 'Cairo',
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  color: theme.colorScheme.secondary,
                ),
              ),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 6,
              alignment: WrapAlignment.center,
              children: quickChips.map((val) {
                return ActionChip(
                  label: Text(
                    ArabicFormatters.formatMoney(val, currency),
                    style: const TextStyle(
                      fontFamily: 'Cairo',
                      fontWeight: FontWeight.w700,
                      fontSize: 12,
                    ),
                  ),
                  onPressed: () {
                    _amountCtrl.text = val.toInt().toString();
                    _amountCtrl.selection = TextSelection.fromPosition(
                      TextPosition(offset: _amountCtrl.text.length),
                    );
                  },
                );
              }).toList(),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Text('اختر الفئة', style: theme.textTheme.titleMedium),
                const Spacer(),
                if (plannedBucket > 0)
                  Flexible(
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: (leftBucket >= 0
                                ? bucketMeta.color
                                : const Color(0xFFDC2626))
                            .withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(
                        leftBucket >= 0
                            ? 'باقي بـ«${bucketMeta.titleAr}»: ${ArabicFormatters.formatMoney(leftBucket, currency)}'
                            : 'تجاوزت بـ ${ArabicFormatters.formatMoney(leftBucket.abs(), currency)}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontFamily: 'Cairo',
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: leftBucket >= 0
                              ? bucketMeta.color
                              : const Color(0xFFDC2626),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            SizedBox(
              height: 92,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: AppCategories.expenseCategories.length,
                separatorBuilder: (_, __) => const SizedBox(width: 10),
                itemBuilder: (context, index) {
                  final cat = AppCategories.expenseCategories[index];
                  final selected = cat.id == _selectedCategoryId;
                  return InkWell(
                    onTap: () => setState(() => _selectedCategoryId = cat.id),
                    borderRadius: BorderRadius.circular(18),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 200),
                      width: 92,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 10,
                      ),
                      decoration: BoxDecoration(
                        color: selected
                            ? cat.color.withValues(alpha: 0.16)
                            : theme.scaffoldBackgroundColor,
                        borderRadius: BorderRadius.circular(18),
                        border: Border.all(
                          color: selected
                              ? cat.color
                              : Colors.grey.withValues(alpha: 0.22),
                          width: selected ? 2 : 1,
                        ),
                      ),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(cat.icon, color: cat.color, size: 26),
                          const SizedBox(height: 6),
                          Text(
                            cat.nameAr,
                            textAlign: TextAlign.center,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontFamily: 'Cairo',
                              fontSize: 11.5,
                              fontWeight:
                                  selected ? FontWeight.w800 : FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _noteCtrl,
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _save(),
              decoration: const InputDecoration(
                hintText: 'ملاحظة قصيرة (اختياري)... مثل: قهوة أو بقالة',
                prefixIcon: Icon(Icons.edit_note_rounded),
              ),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: _saving ? null : _save,
              icon: const Icon(Icons.check_circle_rounded),
              label: Text(_saving ? 'جارٍ الحفظ...' : 'حفظ المصروف الآن'),
            ),
          ],
        ),
      ),
    );
  }
}
