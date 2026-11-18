import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/constants/categories.dart';
import '../../core/constants/currencies.dart';
import '../../core/utils/arabic_formatters.dart';
import '../../core/utils/page_transitions.dart';
import '../../services/plan_engine.dart';
import '../../state/app_controller.dart';
import '../../widgets/animated_counter.dart';
import '../../widgets/budget_donut_chart.dart';
import '../../widgets/category_progress_bar.dart';
import '../advice/advice_screen.dart';
import 'bucket_detail_sheet.dart';

class PlanScreen extends StatefulWidget {
  const PlanScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<PlanScreen> createState() => _PlanScreenState();
}

class _PlanScreenState extends State<PlanScreen> {
  PlanPreset _selectedPreset = PlanPreset.balanced;
  bool _showChartAndSliders = false;
  final TextEditingController _aiQuestionCtrl = TextEditingController();

  @override
  void dispose() {
    _aiQuestionCtrl.dispose();
    super.dispose();
  }

  void _openBucketDetails(BudgetBucket bucket) {
    final c = widget.controller;
    final plan = c.plan;
    if (plan == null) return;
    final meta = AppCategories.bucketMeta(bucket);
    final spent = c.spentByBucketCurrentCycle[bucket] ?? 0.0;
    final items =
        c.currentCycleExpenses.where((e) => e.bucket == bucket).toList();

    showBucketDetailSheet(
      context: context,
      meta: meta,
      plan: plan,
      spent: spent,
      currency: c.currency,
      bucketExpenses: items,
    );
  }

  Future<void> _selectPreset(PlanPreset preset) async {
    setState(() => _selectedPreset = preset);
    await widget.controller.applyPresetPlan(preset);
  }

  Future<void> _sendAiQuestion([String? presetQuestion]) async {
    final q = presetQuestion ?? _aiQuestionCtrl.text.trim();
    if (presetQuestion == null && q.isNotEmpty) {
      _aiQuestionCtrl.clear();
    }
    FocusScope.of(context).unfocus();
    await widget.controller.askAiAdvisor(
      question: q.isEmpty ? null : q,
    );
  }

  Future<void> _openQuickSalaryEditSheet() async {
    final c = widget.controller;
    final profile = c.profile;
    if (profile == null) return;

    final salaryCtrl = TextEditingController(
      text: profile.monthlySalary.round().toString(),
    );
    final rentCtrl = TextEditingController(
      text: profile.isRenting && profile.rentAmount > 0
          ? profile.rentAmount.round().toString()
          : '',
    );
    final debtCtrl = TextEditingController(
      text: profile.debtsMonthlyPayment > 0
          ? profile.debtsMonthlyPayment.round().toString()
          : '',
    );
    bool isRenting = profile.isRenting;
    String code = profile.currencyCode;

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        final theme = Theme.of(ctx);
        return StatefulBuilder(
          builder: (ctx, setSheetState) {
            final cur = AppCurrencies.byCode(code);
            return Container(
              padding: EdgeInsets.fromLTRB(
                20,
                16,
                20,
                20 + MediaQuery.of(ctx).viewInsets.bottom,
              ),
              decoration: BoxDecoration(
                color: theme.cardColor,
                borderRadius:
                    const BorderRadius.vertical(top: Radius.circular(28)),
              ),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Center(
                      child: Container(
                        width: 42,
                        height: 4,
                        decoration: BoxDecoration(
                          color: Colors.grey.withValues(alpha: 0.35),
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                    ),
                    const SizedBox(height: 14),
                    Text(
                      'تعديل الراتب والالتزامات بسرعة',
                      style: theme.textTheme.titleLarge,
                    ),
                    const SizedBox(height: 12),
                    InputDecorator(
                      decoration: const InputDecoration(
                        labelText: 'العملة',
                        prefixIcon: Icon(Icons.currency_exchange_rounded),
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<String>(
                          value: code,
                          isExpanded: true,
                          isDense: true,
                          items: AppCurrencies.all
                              .map(
                                (item) => DropdownMenuItem(
                                  value: item.code,
                                  child: Text('${item.nameAr} (${item.symbol})'),
                                ),
                              )
                              .toList(),
                          onChanged: (v) {
                            if (v != null) setSheetState(() => code = v);
                          },
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: salaryCtrl,
                      keyboardType: TextInputType.numberWithOptions(
                        decimal: cur.allowDecimals,
                      ),
                      inputFormatters: [
                        FilteringTextInputFormatter.allow(
                          cur.allowDecimals
                              ? RegExp(r'[0-9.]')
                              : RegExp(r'[0-9]'),
                        ),
                      ],
                      decoration: InputDecoration(
                        labelText: 'الراتب الشهري',
                        suffixText: cur.symbol,
                        prefixIcon: const Icon(Icons.payments_rounded),
                      ),
                    ),
                    const SizedBox(height: 10),
                    SwitchListTile.adaptive(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('هل تدفع إيجاراً شهرياً؟'),
                      value: isRenting,
                      onChanged: (v) => setSheetState(() => isRenting = v),
                    ),
                    if (isRenting) ...[
                      TextField(
                        controller: rentCtrl,
                        keyboardType: TextInputType.number,
                        inputFormatters: [
                          FilteringTextInputFormatter.digitsOnly,
                        ],
                        decoration: InputDecoration(
                          labelText: 'قيمة الإيجار الشهري',
                          suffixText: cur.symbol,
                          prefixIcon: const Icon(Icons.home_rounded),
                        ),
                      ),
                      const SizedBox(height: 10),
                    ],
                    TextField(
                      controller: debtCtrl,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      decoration: InputDecoration(
                        labelText: 'أقساط أو ديون شهرية (اتركه فارغاً إن لم يوجد)',
                        suffixText: cur.symbol,
                        prefixIcon: const Icon(Icons.receipt_long_rounded),
                      ),
                    ),
                    const SizedBox(height: 16),
                    FilledButton.icon(
                      onPressed: () async {
                        final sal = double.tryParse(salaryCtrl.text.trim()) ?? 0;
                        if (sal <= 0) return;
                        final rent = isRenting
                            ? (double.tryParse(rentCtrl.text.trim()) ?? 0)
                            : 0.0;
                        final debt = double.tryParse(debtCtrl.text.trim()) ?? 0;
                        await c.quickEditSalary(
                          salary: sal,
                          currencyCode: code,
                          isRenting: isRenting,
                          rentAmount: rent,
                          debtsAmount: debt,
                        );
                        if (ctx.mounted) Navigator.of(ctx).pop();
                      },
                      icon: const Icon(Icons.check_rounded),
                      label: const Text('تحديث خطتي الآن'),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );

    salaryCtrl.dispose();
    rentCtrl.dispose();
    debtCtrl.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final profile = c.profile;
    final plan = c.plan;
    final currency = c.currency;
    final theme = Theme.of(context);

    if (profile == null || plan == null) {
      return const Center(child: CircularProgressIndicator());
    }

    final spentByBucket = c.spentByBucketCurrentCycle;
    final exactSplits = c.exactPlanMoney;
    final daysLeft = c.daysUntilSalary;
    final dailySafe = c.dailySafeSpend;
    final weeklySafe = c.weeklySafeSpend;
    final steps = PlanEngine.buildSalaryActionPlan(
      profile: profile,
      plan: plan,
      spentByBucket: spentByBucket,
      currency: currency,
      daysLeft: daysLeft,
    );

    final annualWealth = (exactSplits[BudgetBucket.savings] ?? 0) * 12;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 120),
      children: [
        // 1. Hero Card: Clear Salary + Daily & Weekly Safe Spend
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                theme.colorScheme.primary,
                theme.colorScheme.primary.withValues(alpha: 0.84),
              ],
              begin: Alignment.topRight,
              end: Alignment.bottomLeft,
            ),
            borderRadius: BorderRadius.circular(24),
            boxShadow: [
              BoxShadow(
                color: theme.colorScheme.primary.withValues(alpha: 0.24),
                blurRadius: 16,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'راتبك الشهري',
                          style: TextStyle(
                            fontFamily: 'Cairo',
                            fontSize: 13,
                            color: Colors.white.withValues(alpha: 0.85),
                          ),
                        ),
                        AnimatedNumberText(
                          value: profile.totalMonthlyIncome,
                          formatter: (v) =>
                              ArabicFormatters.formatMoney(v, currency),
                          style: const TextStyle(
                            fontFamily: 'Cairo',
                            fontSize: 22,
                            fontWeight: FontWeight.w800,
                            color: Colors.white,
                          ),
                        ),
                      ],
                    ),
                  ),
                  FilledButton.icon(
                    onPressed: _openQuickSalaryEditSheet,
                    style: FilledButton.styleFrom(
                      minimumSize: const Size(0, 38),
                      backgroundColor: Colors.white.withValues(alpha: 0.18),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 6,
                      ),
                    ),
                    icon: const Icon(Icons.edit_rounded, size: 16),
                    label: const Text(
                      'تعديل',
                      style: TextStyle(
                        fontFamily: 'Cairo',
                        fontSize: 12.5,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.18),
                  borderRadius: BorderRadius.circular(18),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(
                          Icons.today_rounded,
                          color: Color(0xFFF5D77A),
                          size: 18,
                        ),
                        const SizedBox(width: 6),
                        Text(
                          'مسموح تصرف باليوم',
                          style: TextStyle(
                            fontFamily: 'Cairo',
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            color: Colors.white.withValues(alpha: 0.92),
                          ),
                        ),
                        const Spacer(),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.14),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Text(
                            'باقي $daysLeft يوم',
                            style: const TextStyle(
                              fontFamily: 'Cairo',
                              fontSize: 11,
                              fontWeight: FontWeight.w700,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      ArabicFormatters.formatMoney(dailySafe, currency),
                      style: const TextStyle(
                        fontFamily: 'Cairo',
                        fontSize: 28,
                        fontWeight: FontWeight.w800,
                        color: Color(0xFFF5D77A),
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'يعني بالأسبوع: ${ArabicFormatters.formatMoney(weeklySafe, currency)} (حتى يكفيك الراتب)',
                      style: TextStyle(
                        fontFamily: 'Cairo',
                        fontSize: 12,
                        color: Colors.white.withValues(alpha: 0.86),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 14),

        // 2. Unified Card: Ready Salary Presets + Clear Money Envelopes (NO %)
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'تقسيم راتبك بالمصاري',
                  style: theme.textTheme.titleLarge,
                ),
                const SizedBox(height: 2),
                Text(
                  'قديش تحط بكل ظرف أول ما ينزل راتبك (المجموع يساوي راتبك تماماً)',
                  style: theme.textTheme.bodyMedium,
                ),
                const SizedBox(height: 10),
                SizedBox(
                  height: 42,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    itemCount: PlanPreset.values.length,
                    separatorBuilder: (_, __) => const SizedBox(width: 8),
                    itemBuilder: (context, idx) {
                      final preset = PlanPreset.values[idx];
                      final isSelected =
                          !plan.isCustomized && preset == PlanPreset.balanced
                              ? true
                              : preset == _selectedPreset && plan.isCustomized;
                      return ChoiceChip(
                        label: Text(
                          preset.shortAr,
                          style: TextStyle(
                            fontFamily: 'Cairo',
                            fontWeight: FontWeight.w700,
                            fontSize: 12.5,
                            color: isSelected
                                ? theme.colorScheme.onPrimary
                                : null,
                          ),
                        ),
                        selected: isSelected,
                        selectedColor: theme.colorScheme.primary,
                        onSelected: (_) => _selectPreset(preset),
                      );
                    },
                  ),
                ),
                const SizedBox(height: 14),
                ...steps.map((step) {
                  final meta = AppCategories.bucketMeta(step.bucket);
                  final ratio = step.monthlyAmount > 0
                      ? (step.spentAmount / step.monthlyAmount)
                      : 0.0;
                  final remaining = step.monthlyAmount - step.spentAmount;
                  return InkWell(
                    onTap: () => _openBucketDetails(step.bucket),
                    borderRadius: BorderRadius.circular(18),
                    child: Container(
                      margin: const EdgeInsets.only(bottom: 10),
                      padding: const EdgeInsets.all(13),
                      decoration: BoxDecoration(
                        color: theme.scaffoldBackgroundColor,
                        borderRadius: BorderRadius.circular(18),
                        border: Border.all(
                          color: meta.color.withValues(alpha: 0.25),
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Row(
                            children: [
                              Container(
                                width: 30,
                                height: 30,
                                alignment: Alignment.center,
                                decoration: BoxDecoration(
                                  color: meta.color,
                                  shape: BoxShape.circle,
                                ),
                                child: Text(
                                  '${step.stepNumber}',
                                  style: const TextStyle(
                                    fontFamily: 'Cairo',
                                    color: Colors.white,
                                    fontWeight: FontWeight.w800,
                                    fontSize: 13.5,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  meta.titleAr,
                                  style: theme.textTheme.titleMedium?.copyWith(
                                    fontSize: 15,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 10,
                                  vertical: 4,
                                ),
                                decoration: BoxDecoration(
                                  color: meta.color.withValues(alpha: 0.14),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Text(
                                  ArabicFormatters.formatMoney(
                                    step.monthlyAmount,
                                    currency,
                                  ),
                                  style: TextStyle(
                                    fontFamily: 'Cairo',
                                    fontWeight: FontWeight.w800,
                                    fontSize: 15,
                                    color: meta.color,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          Text(
                            step.instructionAr,
                            style: theme.textTheme.bodyMedium?.copyWith(
                              fontSize: 12.5,
                            ),
                          ),
                          const SizedBox(height: 8),
                          CategoryProgressBar(ratio: ratio, height: 8),
                          const SizedBox(height: 5),
                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  remaining >= 0
                                      ? 'المتبقي معك: ${ArabicFormatters.formatMoney(remaining, currency)}'
                                      : 'تجاوزت بـ ${ArabicFormatters.formatMoney(remaining.abs(), currency)}',
                                  style: TextStyle(
                                    fontFamily: 'Cairo',
                                    fontSize: 11.5,
                                    fontWeight: FontWeight.w700,
                                    color: remaining >= 0
                                        ? meta.color
                                        : const Color(0xFFDC2626),
                                  ),
                                ),
                              ),
                              Text(
                                'التفاصيل ←',
                                style: TextStyle(
                                  fontFamily: 'Cairo',
                                  fontSize: 11,
                                  color: theme.textTheme.bodyMedium?.color,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  );
                }),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      const Text(
                        'إجمالي التوزيع',
                        style: TextStyle(
                          fontFamily: 'Cairo',
                          fontWeight: FontWeight.w700,
                          fontSize: 13,
                        ),
                      ),
                      const Spacer(),
                      Text(
                        ArabicFormatters.formatMoney(
                          profile.totalMonthlyIncome,
                          currency,
                        ),
                        style: TextStyle(
                          fontFamily: 'Cairo',
                          fontWeight: FontWeight.w800,
                          fontSize: 14,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 6),
                TextButton(
                  onPressed: () => setState(
                    () => _showChartAndSliders = !_showChartAndSliders,
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        _showChartAndSliders
                            ? Icons.expand_less_rounded
                            : Icons.pie_chart_outline_rounded,
                      ),
                      const SizedBox(width: 8),
                      Flexible(
                        child: Text(
                          _showChartAndSliders
                              ? 'إخفاء الرسم والتعديل'
                              : 'الرسم الدائري وتعديل المبالغ يدوياً',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
                if (_showChartAndSliders) ...[
                  const Divider(height: 20),
                  BudgetDonutChart(
                    plan: plan,
                    currency: currency,
                    onBucketTap: _openBucketDetails,
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          'تعديل المبالغ يدوياً',
                          style: theme.textTheme.titleMedium,
                        ),
                      ),
                      TextButton(
                        onPressed: c.resetRecommendedPlan,
                        child: const Text('إعادة التلقائي'),
                      ),
                    ],
                  ),
                  ...AppCategories.buckets.map((meta) {
                    final amount = exactSplits[meta.bucket] ?? 0.0;
                    final maxAmt = (profile.totalMonthlyIncome * 0.85)
                        .clamp(1.0, double.infinity);
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Row(
                          children: [
                            Icon(meta.icon, color: meta.color, size: 18),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                meta.titleAr,
                                style: theme.textTheme.bodyLarge?.copyWith(
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                            Text(
                              ArabicFormatters.formatMoney(amount, currency),
                              style: TextStyle(
                                fontFamily: 'Cairo',
                                fontWeight: FontWeight.w800,
                                color: meta.color,
                              ),
                            ),
                          ],
                        ),
                        Slider(
                          value: amount.clamp(0.0, maxAmt),
                          min: 0,
                          max: maxAmt,
                          activeColor: meta.color,
                          onChanged: (val) => c.updatePlanAmount(
                            meta.bucket,
                            val,
                          ),
                        ),
                      ],
                    );
                  }),
                ],
              ],
            ),
          ),
        ),

        const SizedBox(height: 14),

        // 3. Free AI Financial Advisor Card
        _buildAiAdvisorCard(context, c),

        const SizedBox(height: 14),

        // 4. Compact Annual Savings & Advice Shortcut Card
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: const Color(0xFF10B981).withValues(alpha: 0.14),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: const Icon(
                        Icons.savings_rounded,
                        color: Color(0xFF10B981),
                        size: 26,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'كم بتوفّر بعد سنة؟',
                            style: theme.textTheme.bodyMedium,
                          ),
                          Text(
                            ArabicFormatters.formatMoney(annualWealth, currency),
                            style: theme.textTheme.titleLarge?.copyWith(
                              color: const Color(0xFF10B981),
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          Text(
                            'حالة راتبك: ${plan.healthLabelAr}',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                OutlinedButton(
                  onPressed: () {
                    Navigator.of(context).push(
                      SmoothPageRoute(
                        builder: (_) => Scaffold(
                          appBar: AppBar(
                            title: const Text('النصائح وخطة الديون'),
                          ),
                          body: AdviceScreen(controller: c),
                        ),
                      ),
                    );
                  },
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.tips_and_updates_rounded),
                      SizedBox(width: 8),
                      Flexible(
                        child: Text(
                          'النصائح، تسكير الديون وتحدي 30 يوم',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildAiAdvisorCard(BuildContext context, AppController c) {
    final theme = Theme.of(context);
    final aiResult = c.latestAiResult;
    final loading = c.isAiLoading;

    const quickPrompts = [
      'كيف أوفر أكثر من راتبي؟',
      'هل مصاريفي وإيجاري طبيعية؟',
      'رتّب لي مصروفي للأسبوع الجاي',
    ];

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(9),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.secondary.withValues(alpha: 0.16),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(
                    Icons.auto_awesome_rounded,
                    color: theme.colorScheme.secondary,
                    size: 24,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'المستشار الذكي بالذكاء الاصطناعي',
                        style: theme.textTheme.titleMedium,
                      ),
                      Text(
                        'مجاني تماماً: يحلّل راتبك ويعطيك الجواب بالمصاري',
                        style: theme.textTheme.bodyMedium?.copyWith(fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: loading ? null : () => _sendAiQuestion(''),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  loading
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(
                            strokeWidth: 2.2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.psychology_rounded),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Text(
                      loading
                          ? 'جاري التحليل الذكي لراتبك...'
                          : 'حلّل راتبي وأعطني خطة مجاناً',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 6,
              children: quickPrompts.map((q) {
                return ActionChip(
                  label: Text(
                    q,
                    style: const TextStyle(
                      fontFamily: 'Cairo',
                      fontSize: 11.5,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  onPressed: loading ? null : () => _sendAiQuestion(q),
                );
              }).toList(),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _aiQuestionCtrl,
                    enabled: !loading,
                    textInputAction: TextInputAction.send,
                    onSubmitted: (_) => _sendAiQuestion(),
                    decoration: const InputDecoration(
                      hintText: 'اسأل أي سؤال (مثلاً: أشتري غرض بـ 500؟)',
                      isDense: true,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton.filled(
                  onPressed: loading ? null : () => _sendAiQuestion(),
                  icon: const Icon(Icons.send_rounded),
                  tooltip: 'إرسال للذكاء الاصطناعي',
                ),
              ],
            ),
            if (aiResult != null) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: theme.colorScheme.primary.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: theme.colorScheme.primary.withValues(alpha: 0.22),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      children: [
                        Icon(
                          aiResult.isFromCloudAi
                              ? Icons.cloud_done_rounded
                              : Icons.bolt_rounded,
                          size: 16,
                          color: theme.colorScheme.primary,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            aiResult.isFromCloudAi
                                ? 'تحليل ذكاء اصطناعي مجاني (أونلاين)'
                                : 'تحليل فوري ذكي (مباشر)',
                            style: TextStyle(
                              fontFamily: 'Cairo',
                              fontSize: 11.5,
                              fontWeight: FontWeight.w800,
                              color: theme.colorScheme.primary,
                            ),
                          ),
                        ),
                      ],
                    ),
                    if (aiResult.questionAsked != null) ...[
                      const SizedBox(height: 4),
                      Text(
                        'سؤالك: ${aiResult.questionAsked}',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          fontSize: 12,
                          fontStyle: FontStyle.italic,
                        ),
                      ),
                    ],
                    const SizedBox(height: 8),
                    Text(
                      aiResult.message,
                      style: theme.textTheme.bodyLarge?.copyWith(
                        height: 1.55,
                        fontSize: 13.5,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
