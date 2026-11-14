import 'package:flutter/material.dart';

import '../../core/constants/categories.dart';
import '../../core/utils/arabic_formatters.dart';
import '../../core/utils/page_transitions.dart';
import '../../state/app_controller.dart';
import '../../widgets/animated_counter.dart';
import '../../widgets/budget_donut_chart.dart';
import '../../widgets/category_progress_bar.dart';
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
          child: FilledButton.icon(
            onPressed: () {
              Navigator.of(context).push(
                SmoothPageRoute(
                  builder: (_) => SetupWizardScreen(controller: c),
                ),
              );
            },
            icon: const Icon(Icons.auto_awesome_rounded),
            label: const Text('أنشئ خطة راتبك الآن'),
          ),
        ),
      );
    }

    final currency = c.currency;
    final spentByBucket = c.spentByBucketCurrentCycle;
    final remaining = c.remainingCurrentCycle;

    // Detect any over-budget alerts for prominent banner
    final overBuckets = AppCategories.buckets.where((b) {
      final planned = plan.amountFor(b.bucket);
      final spent = spentByBucket[b.bucket] ?? 0;
      return planned > 0 && spent > planned;
    }).toList();

    final healthColor = _healthColor(plan.healthScore);

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
      children: [
        // Top Emerald-Gold Salary Hero Card
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
                color: const Color(0xFF063C2B).withValues(alpha: 0.25),
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
                          'إجمالي الدخل الشهري',
                          style: TextStyle(
                            fontFamily: 'Cairo',
                            color: Color(0xFFD7F3E6),
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
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
                      ],
                    ),
                  ),
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
                    label: const Text('تعديل'),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 12,
                ),
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.22),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'المتبقي من الشهر',
                          style: TextStyle(
                            fontFamily: 'Cairo',
                            color: Color(0xFFF5C443),
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        Text(
                          ArabicFormatters.formatMoney(remaining, currency),
                          style: const TextStyle(
                            fontFamily: 'Cairo',
                            color: Colors.white,
                            fontSize: 18,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ],
                    ),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        const Text(
                          'المصروف حتى الآن',
                          style: TextStyle(
                            fontFamily: 'Cairo',
                            color: Color(0xFFD7F3E6),
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        Text(
                          ArabicFormatters.formatMoney(
                            c.totalSpentCurrentCycle,
                            currency,
                          ),
                          style: const TextStyle(
                            fontFamily: 'Cairo',
                            color: Colors.white,
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
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
                    'تنبيه: تجاوزت ميزانية ${overBuckets.map((b) => b.titleAr).join('، ')}. راجع المصاريف أو عدّل النسب.',
                    style: const TextStyle(
                      fontFamily: 'Cairo',
                      color: Color(0xFFDC2626),
                      fontWeight: FontWeight.w700,
                      fontSize: 13,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],

        const SizedBox(height: 14),

        // Donut Chart Card
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.pie_chart_rounded,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'توزيع راتبك الشهري',
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
                      ),
                      label: Text(_showSliders ? 'إخفاء النسب' : 'تعديل النسب'),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
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

                // Live Sliders section
                AnimatedCrossFade(
                  duration: const Duration(milliseconds: 280),
                  crossFadeState: _showSliders
                      ? CrossFadeState.showSecond
                      : CrossFadeState.showFirst,
                  firstChild: const SizedBox.shrink(),
                  secondChild: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Divider(height: 24),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            'حرّك المنزلقات — الرسم يتحدث لحظياً',
                            style: theme.textTheme.titleMedium,
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
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Row(
                                  children: [
                                    Icon(
                                      meta.icon,
                                      size: 18,
                                      color: meta.color,
                                    ),
                                    const SizedBox(width: 6),
                                    Text(
                                      meta.titleAr,
                                      style: theme.textTheme.bodyLarge,
                                    ),
                                  ],
                                ),
                                Text(
                                  '${pct.toStringAsFixed(0)}% • ${ArabicFormatters.formatCompactMoney(plan.amountFor(meta.bucket), currency)}',
                                  style: TextStyle(
                                    fontFamily: 'Cairo',
                                    fontWeight: FontWeight.w700,
                                    color: meta.color,
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

        // Salary Health Score + Annual Savings Row/Cards
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Row(
              children: [
                SizedBox(
                  width: 78,
                  height: 78,
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      TweenAnimationBuilder<double>(
                        tween: Tween<double>(
                          begin: 0,
                          end: plan.healthScore / 100.0,
                        ),
                        duration: const Duration(milliseconds: 700),
                        builder: (context, val, _) {
                          return CircularProgressIndicator(
                            value: val,
                            strokeWidth: 8,
                            backgroundColor:
                                healthColor.withValues(alpha: 0.16),
                            valueColor:
                                AlwaysStoppedAnimation<Color>(healthColor),
                            strokeCap: StrokeCap.round,
                          );
                        },
                      ),
                      Text(
                        '${plan.healthScore}',
                        style: TextStyle(
                          fontFamily: 'Cairo',
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                          color: healthColor,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'مؤشر صحة راتبك من 100',
                        style: theme.textTheme.bodyMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        plan.healthLabelAr,
                        style: theme.textTheme.titleLarge?.copyWith(
                          color: healthColor,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'مبني على قاعدة 50/30/20، ضغط السكن، الديون والادخار.',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 12),

        // "كم ممكن توفر بالسنة؟" Card with animated rolling number
        Card(
          color: theme.colorScheme.secondary.withValues(alpha: 0.13),
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.secondary,
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: const Icon(
                    Icons.auto_graph_rounded,
                    color: Color(0xFF1F1703),
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
                      const SizedBox(height: 2),
                      Text(
                        'وخلال 3 سنوات: ${ArabicFormatters.formatCompactMoney(plan.annualSavingsPotential * 3, currency)} (ادخار + طوارئ)',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 16),

        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('تفاصيل كل بند', style: theme.textTheme.titleLarge),
              Text(
                'المس أي بطاقة للتفاصيل',
                style: theme.textTheme.bodyMedium?.copyWith(fontSize: 12),
              ),
            ],
          ),
        ),
        const SizedBox(height: 10),

        // 5 Interactive Category Cards
        ...AppCategories.buckets.map((meta) {
          final pct = plan.percentageFor(meta.bucket);
          final planned = plan.amountFor(meta.bucket);
          final spent = spentByBucket[meta.bucket] ?? 0.0;
          final ratio = planned > 0 ? (spent / planned) : 0.0;

          return Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Card(
              child: InkWell(
                borderRadius: BorderRadius.circular(20),
                onTap: () => showBucketDetailSheet(
                  context: context,
                  meta: meta,
                  plan: plan,
                  spent: spent,
                  currency: currency,
                  bucketExpenses: c.currentCycleExpenses
                      .where((e) => e.bucket == meta.bucket)
                      .toList(),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    children: [
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              color: meta.color.withValues(alpha: 0.14),
                              borderRadius: BorderRadius.circular(14),
                            ),
                            child: Icon(
                              meta.icon,
                              color: meta.color,
                              size: 24,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  meta.titleAr,
                                  style: theme.textTheme.titleMedium,
                                ),
                                Text(
                                  meta.subtitleAr,
                                  style: theme.textTheme.bodyMedium?.copyWith(
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                ArabicFormatters.formatMoney(planned, currency),
                                style: theme.textTheme.titleMedium?.copyWith(
                                  color: meta.color,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              Text(
                                '${pct.toStringAsFixed(0)}%',
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      CategoryProgressBar(ratio: ratio, height: 8),
                      const SizedBox(height: 6),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            'المصروف: ${ArabicFormatters.formatMoney(spent, currency)}',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              fontSize: 12,
                            ),
                          ),
                          Text(
                            planned - spent >= 0
                                ? 'متبقي: ${ArabicFormatters.formatMoney(planned - spent, currency)}'
                                : 'تجاوز: ${ArabicFormatters.formatMoney((planned - spent).abs(), currency)}',
                            style: TextStyle(
                              fontFamily: 'Cairo',
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                              color: planned - spent >= 0
                                  ? const Color(0xFF10B981)
                                  : const Color(0xFFEF4444),
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
      ],
    );
  }
}
