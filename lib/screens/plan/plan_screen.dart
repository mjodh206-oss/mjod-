import 'package:flutter/material.dart';

import '../../core/constants/categories.dart';
import '../../core/constants/financial_tips_data.dart';
import '../../core/utils/arabic_formatters.dart';
import '../../core/utils/page_transitions.dart';
import '../../services/plan_engine.dart';
import '../../state/app_controller.dart';
import '../../widgets/animated_counter.dart';
import '../../widgets/budget_donut_chart.dart';
import '../../widgets/category_progress_bar.dart';
import '../advice/advice_screen.dart';
import '../setup/setup_wizard_screen.dart';
import 'bucket_detail_sheet.dart';

class PlanScreen extends StatefulWidget {
  const PlanScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<PlanScreen> createState() => _PlanScreenState();
}

class _PlanScreenState extends State<PlanScreen> {
  bool _showSliders = false;
  PlanPreset _selectedPreset = PlanPreset.balanced;

  Color _healthColor(int score) {
    if (score >= 85) return const Color(0xFF10B981);
    if (score >= 70) return const Color(0xFF22C55E);
    if (score >= 50) return const Color(0xFFF59E0B);
    return const Color(0xFFEF4444);
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final profile = c.profile;
    final plan = c.plan;
    final theme = Theme.of(context);

    if (profile == null || plan == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                Icons.account_balance_wallet_rounded,
                size: 64,
                color: Color(0xFF0E9F6E),
              ),
              const SizedBox(height: 12),
              Text(
                'أدخل راتبك لنجهّز خطتك بثانية',
                style: theme.textTheme.headlineMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: () {
                  Navigator.of(context).push(
                    SmoothPageRoute(
                      builder: (_) => SetupWizardScreen(controller: c),
                    ),
                  );
                },
                icon: const Icon(Icons.auto_awesome_rounded),
                label: const Text('اكتب راتبك الآن'),
              ),
            ],
          ),
        ),
      );
    }

    final currency = c.currency;
    final spentByBucket = c.spentByBucketCurrentCycle;
    final remaining = c.remainingCurrentCycle;
    final daysLeft = c.daysUntilSalary;
    final dailyAllowed = c.dailySafeSpend;
    final weeklyAllowed = c.weeklySafeSpend;
    final actionSteps = PlanEngine.buildSalaryActionPlan(
      profile: profile,
      plan: plan,
      spentByBucket: spentByBucket,
      currency: currency,
      daysLeft: daysLeft,
    );
    final smartAdvice = PlanEngine.generateSmartAdvice(
      profile: profile,
      plan: plan,
      spentByBucket: spentByBucket,
      currency: currency,
    );

    final overBuckets = AppCategories.buckets.where((b) {
      final planned = plan.amountFor(b.bucket);
      final spent = spentByBucket[b.bucket] ?? 0;
      return planned > 0 && spent > planned;
    }).toList();

    final healthColor = _healthColor(plan.healthScore);

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
      children: [
        // 1. Hero Salary & Daily Allowance Card
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            gradient: const LinearGradient(
              begin: AlignmentDirectional.topStart,
              end: AlignmentDirectional.bottomEnd,
              colors: [
                Color(0xFF063C2B),
                Color(0xFF0C684A),
                Color(0xFF9C7A22),
              ],
            ),
            boxShadow: [
              BoxShadow(
                color: const Color(0xFF063C2B).withValues(alpha: 0.22),
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
                        const Text(
                          'راتبك الشهري',
                          style: TextStyle(
                            fontFamily: 'Cairo',
                            color: Color(0xFFD7F3E6),
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 2),
                        FittedBox(
                          fit: BoxFit.scaleDown,
                          alignment: AlignmentDirectional.centerStart,
                          child: Text(
                            ArabicFormatters.formatMoney(
                              profile.totalMonthlyIncome,
                              currency,
                            ),
                            style: const TextStyle(
                              fontFamily: 'Cairo',
                              color: Colors.white,
                              fontSize: 24,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.white,
                      side: const BorderSide(color: Color(0xFFF5C443)),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 8,
                      ),
                    ),
                    onPressed: () {
                      Navigator.of(context).push(
                        SmoothPageRoute(
                          builder: (_) => SetupWizardScreen(
                            controller: c,
                            isEditing: true,
                          ),
                        ),
                      );
                    },
                    icon: const Icon(Icons.edit_rounded, size: 17),
                    label: const Text('تعديل الراتب'),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              // Daily safe spend box — the #1 easiest metric for every user
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.25),
                  borderRadius: BorderRadius.circular(18),
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
                              const Text(
                                'مسموح تصرف باليوم',
                                style: TextStyle(
                                  fontFamily: 'Cairo',
                                  color: Color(0xFFF5C443),
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              FittedBox(
                                fit: BoxFit.scaleDown,
                                alignment: AlignmentDirectional.centerStart,
                                child: Text(
                                  ArabicFormatters.formatMoney(
                                    dailyAllowed,
                                    currency,
                                  ),
                                  style: const TextStyle(
                                    fontFamily: 'Cairo',
                                    color: Colors.white,
                                    fontSize: 22,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                'المتبقي ($daysLeft يوم للراتب)',
                                style: const TextStyle(
                                  fontFamily: 'Cairo',
                                  color: Color(0xFFD7F3E6),
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              FittedBox(
                                fit: BoxFit.scaleDown,
                                alignment: AlignmentDirectional.centerEnd,
                                child: Text(
                                  ArabicFormatters.formatMoney(
                                    remaining,
                                    currency,
                                  ),
                                  style: const TextStyle(
                                    fontFamily: 'Cairo',
                                    color: Colors.white,
                                    fontSize: 17,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'يعني حوالي ${ArabicFormatters.formatCompactMoney(weeklyAllowed, currency)} بالأسبوع للأكل والمصروف الشخصي حتى ما ينقطع راتبك قبل آخر الشهر.',
                      style: const TextStyle(
                        fontFamily: 'Cairo',
                        color: Color(0xFFE5F7EE),
                        fontSize: 12,
                        height: 1.45,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        if (overBuckets.isNotEmpty) ...[
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: const Color(0xFFEF4444).withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: const Color(0xFFEF4444).withValues(alpha: 0.5),
              ),
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.warning_amber_rounded,
                  color: Color(0xFFEF4444),
                  size: 28,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'انتبه: تجاوزت الحد المخصص في ${overBuckets.map((e) => e.titleAr).join('، ')}. خفف الصرف شوي هالأسبوع.',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: const Color(0xFFB91C1C),
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],

        const SizedBox(height: 14),

        // 2. Ready Plan Style Switcher (4 presets tailored to salary)
        Text(
          'اختر خطتك الجاهزة بناءً على راتبك',
          style: theme.textTheme.titleLarge,
        ),
        const SizedBox(height: 4),
        Text(
          'بدّل بين الخطط بضغطة وحدة وشوف الأرقام تتحدث فوراً',
          style: theme.textTheme.bodyMedium,
        ),
        const SizedBox(height: 10),
        SizedBox(
          height: 44,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: PlanPreset.values.length,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (context, idx) {
              final preset = PlanPreset.values[idx];
              final selected = _selectedPreset == preset;
              return ChoiceChip(
                label: Text(preset.shortAr),
                selected: selected,
                onSelected: (_) {
                  setState(() => _selectedPreset = preset);
                  c.applyPresetPlan(preset);
                },
              );
            },
          ),
        ),
        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            color: theme.colorScheme.primary.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Text(
            '${_selectedPreset.titleAr}: ${_selectedPreset.descriptionAr}',
            style: theme.textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.w600,
            ),
          ),
        ),

        const SizedBox(height: 16),

        // 3. Step-by-Step Salary Action Plan (the real plan!)
        Row(
          children: [
            Expanded(
              child: Text(
                'خطتك خطوة بخطوة أول ما ينزل الراتب',
                style: theme.textTheme.titleLarge,
              ),
            ),
            Flexible(
              child: Text(
                'اضغط للتفاصيل',
                style: theme.textTheme.bodyMedium?.copyWith(fontSize: 12),
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),

        ...actionSteps.map((step) {
          final meta = AppCategories.metaFor(step.bucket);
          final ratio = step.monthlyAmount > 0
              ? (step.spentAmount / step.monthlyAmount)
              : 0.0;
          final remainingInStep = step.monthlyAmount - step.spentAmount;

          return Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Card(
              child: InkWell(
                borderRadius: BorderRadius.circular(20),
                onTap: () => showBucketDetailSheet(
                  context: context,
                  meta: meta,
                  plan: plan,
                  spent: step.spentAmount,
                  currency: currency,
                  bucketExpenses: c.currentCycleExpenses
                      .where((e) => e.bucket == step.bucket)
                      .toList(),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Row(
                        children: [
                          CircleAvatar(
                            radius: 17,
                            backgroundColor: meta.color,
                            child: Text(
                              '${step.stepNumber}',
                              style: const TextStyle(
                                fontFamily: 'Cairo',
                                fontWeight: FontWeight.w800,
                                color: Colors.white,
                              ),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              step.titleAr,
                              style: theme.textTheme.titleMedium,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                ArabicFormatters.formatMoney(
                                  step.monthlyAmount,
                                  currency,
                                ),
                                style: theme.textTheme.titleMedium?.copyWith(
                                  color: meta.color,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              Text(
                                '${step.percentage.toStringAsFixed(0)}%',
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        step.instructionAr,
                        style: theme.textTheme.bodyMedium?.copyWith(height: 1.45),
                      ),
                      const SizedBox(height: 10),
                      CategoryProgressBar(ratio: ratio, height: 8),
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              'المصروف: ${ArabicFormatters.formatCompactMoney(step.spentAmount, currency)}',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: theme.textTheme.bodyMedium?.copyWith(
                                fontSize: 12,
                              ),
                            ),
                          ),
                          Expanded(
                            child: Text(
                              remainingInStep >= 0
                                  ? 'باقي: ${ArabicFormatters.formatCompactMoney(remainingInStep, currency)}'
                                  : 'زيادة: ${ArabicFormatters.formatCompactMoney(remainingInStep.abs(), currency)}',
                              textAlign: TextAlign.end,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontFamily: 'Cairo',
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: remainingInStep >= 0
                                    ? const Color(0xFF10B981)
                                    : const Color(0xFFEF4444),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        }),

        const SizedBox(height: 8),

        // 4. Annual Savings Card ("كم ممكن توفر بالسنة؟")
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(22),
            gradient: LinearGradient(
              colors: [
                const Color(0xFFD4AF37).withValues(alpha: 0.18),
                const Color(0xFF0E9F6E).withValues(alpha: 0.14),
              ],
            ),
            border: Border.all(
              color: const Color(0xFFD4AF37).withValues(alpha: 0.5),
            ),
          ),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFFD4AF37).withValues(alpha: 0.25),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Icon(
                  Icons.auto_graph_rounded,
                  color: Color(0xFFB8860B),
                  size: 30,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'كم ممكن توفر بالسنة؟',
                      style: theme.textTheme.titleMedium,
                    ),
                    const SizedBox(height: 2),
                    AnimatedNumberText(
                      value: plan.annualSavingsPotential,
                      formatter: (v) =>
                          ArabicFormatters.formatMoney(v, currency),
                      style: theme.textTheme.headlineMedium?.copyWith(
                        color: theme.colorScheme.primary,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    Text(
                      'إذا التزمت بتوفير ${ArabicFormatters.formatCompactMoney(plan.amountFor(BudgetBucket.savings) + plan.amountFor(BudgetBucket.emergency), currency)} كل شهر',
                      style: theme.textTheme.bodyMedium?.copyWith(fontSize: 12),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 12),

        // 5. Simple Visual Chart + Health Score + Optional Manual Sliders
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        'شكل توزيع راتبك',
                        style: theme.textTheme.titleLarge,
                      ),
                    ),
                    TextButton.icon(
                      onPressed: () =>
                          setState(() => _showSliders = !_showSliders),
                      icon: Icon(
                        _showSliders
                            ? Icons.expand_less_rounded
                            : Icons.tune_rounded,
                        size: 18,
                      ),
                      label: Text(_showSliders ? 'إخفاء' : 'تعديل يدوي'),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                BudgetDonutChart(
                  plan: plan,
                  currency: currency,
                  onBucketTap: (bucket) {
                    final meta = AppCategories.metaFor(bucket);
                    showBucketDetailSheet(
                      context: context,
                      meta: meta,
                      plan: plan,
                      spent: spentByBucket[bucket] ?? 0,
                      currency: currency,
                      bucketExpenses: c.currentCycleExpenses
                          .where((e) => e.bucket == bucket)
                          .toList(),
                    );
                  },
                ),
                AnimatedCrossFade(
                  duration: const Duration(milliseconds: 260),
                  crossFadeState: _showSliders
                      ? CrossFadeState.showSecond
                      : CrossFadeState.showFirst,
                  firstChild: const SizedBox.shrink(),
                  secondChild: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Divider(height: 24),
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              'حرّك لتعديل النسب كما يناسبك',
                              style: theme.textTheme.titleMedium,
                            ),
                          ),
                          if (plan.isCustomized)
                            TextButton(
                              onPressed: c.resetRecommendedPlan,
                              child: const Text('استعادة المقترح'),
                            ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      ...AppCategories.buckets.map((meta) {
                        final pct = plan.percentageFor(meta.bucket);
                        return Column(
                          children: [
                            Row(
                              children: [
                                Icon(meta.icon, size: 18, color: meta.color),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: Text(
                                    meta.titleAr,
                                    style: theme.textTheme.bodyLarge,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Flexible(
                                  child: Text(
                                    '${pct.toStringAsFixed(0)}% • ${ArabicFormatters.formatCompactMoney(plan.amountFor(meta.bucket), currency)}',
                                    textAlign: TextAlign.end,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      fontFamily: 'Cairo',
                                      fontWeight: FontWeight.w700,
                                      color: meta.color,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            Slider(
                              value: pct.clamp(0.0, 85.0),
                              min: 0,
                              max: 85,
                              divisions: 85,
                              activeColor: meta.color,
                              onChanged: (v) =>
                                  c.updatePlanSlider(meta.bucket, v),
                            ),
                          ],
                        );
                      }),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 12),

        // 6. Health Score + Key Tip + Link to Debt & Challenge screen
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    SizedBox(
                      width: 64,
                      height: 64,
                      child: Stack(
                        alignment: Alignment.center,
                        children: [
                          TweenAnimationBuilder<double>(
                            tween: Tween<double>(
                              begin: 0,
                              end: plan.healthScore.toDouble(),
                            ),
                            duration: const Duration(milliseconds: 800),
                            builder: (context, value, _) {
                              return CircularProgressIndicator(
                                value: value / 100,
                                strokeWidth: 7,
                                backgroundColor:
                                    healthColor.withValues(alpha: 0.15),
                                valueColor:
                                    AlwaysStoppedAnimation<Color>(healthColor),
                              );
                            },
                          ),
                          Text(
                            '${plan.healthScore}',
                            style: theme.textTheme.titleLarge?.copyWith(
                              color: healthColor,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'صحة خطة راتبك: ${plan.healthLabelAr}',
                            style: theme.textTheme.titleMedium,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            smartAdvice.isNotEmpty
                                ? smartAdvice.first.titleAr
                                : FinancialTipsData.tipOfToday(),
                            style: theme.textTheme.bodyMedium,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
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
                  icon: const Icon(Icons.tips_and_updates_rounded),
                  label: const Text('عرض النصائح، خطة سداد الديون وتحدي الـ 30 يوم'),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
