import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../core/constants/categories.dart';
import '../core/constants/currencies.dart';
import '../core/utils/arabic_formatters.dart';
import '../models/budget_plan.dart';

class BudgetDonutChart extends StatefulWidget {
  const BudgetDonutChart({
    super.key,
    required this.plan,
    required this.currency,
    this.onBucketTap,
  });

  final BudgetPlan plan;
  final AppCurrency currency;
  final ValueChanged<BudgetBucket>? onBucketTap;

  @override
  State<BudgetDonutChart> createState() => _BudgetDonutChartState();
}

class _BudgetDonutChartState extends State<BudgetDonutChart> {
  int _touchedIndex = -1;
  bool _appeared = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        setState(() => _appeared = true);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final activeBuckets = AppCategories.buckets
        .where((b) => widget.plan.percentageFor(b.bucket) > 0.4)
        .toList();

    final selectedMeta = (_touchedIndex >= 0 && _touchedIndex < activeBuckets.length)
        ? activeBuckets[_touchedIndex]
        : null;

    return Column(
      children: [
        SizedBox(
          height: 235,
          child: Stack(
            alignment: Alignment.center,
            children: [
              AnimatedScale(
                scale: _appeared ? 1.0 : 0.72,
                duration: const Duration(milliseconds: 650),
                curve: Curves.easeOutBack,
                child: AnimatedOpacity(
                  opacity: _appeared ? 1.0 : 0.0,
                  duration: const Duration(milliseconds: 500),
                  child: PieChart(
                    PieChartData(
                      startDegreeOffset: -90,
                      sectionsSpace: 3,
                      centerSpaceRadius: 62,
                      pieTouchData: PieTouchData(
                        touchCallback: (event, response) {
                          if (!event.isInterestedForInteractions ||
                              response == null ||
                              response.touchedSection == null) {
                            return;
                          }
                          final idx =
                              response.touchedSection!.touchedSectionIndex;
                          setState(() => _touchedIndex = idx);
                          if (event is FlTapUpEvent &&
                              idx >= 0 &&
                              idx < activeBuckets.length) {
                            widget.onBucketTap?.call(activeBuckets[idx].bucket);
                          }
                        },
                      ),
                      sections: List.generate(activeBuckets.length, (i) {
                        final meta = activeBuckets[i];
                        final pct = widget.plan.percentageFor(meta.bucket);
                        final isTouched = i == _touchedIndex;
                        return PieChartSectionData(
                          color: meta.color,
                          value: _appeared ? pct : (i == 0 ? 100 : 0.1),
                          title: pct >= 7 ? '${pct.round()}%' : '',
                          radius: isTouched ? 52 : 44,
                          titleStyle: const TextStyle(
                            fontFamily: 'Cairo',
                            fontSize: 12.5,
                            fontWeight: FontWeight.w800,
                            color: Colors.white,
                          ),
                        );
                      }),
                    ),
                    duration: const Duration(milliseconds: 450),
                    curve: Curves.easeOutCubic,
                  ),
                ),
              ),
              SizedBox(
                width: 112,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      selectedMeta?.titleAr ?? 'الدخل الشهري',
                      textAlign: TextAlign.center,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            fontSize: 11.5,
                            fontWeight: FontWeight.w700,
                          ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      ArabicFormatters.formatCompactMoney(
                        selectedMeta != null
                            ? widget.plan.amountFor(selectedMeta.bucket)
                            : widget.plan.totalIncome,
                        widget.currency,
                      ),
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontSize: 14,
                            fontWeight: FontWeight.w800,
                          ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 10),
        Wrap(
          spacing: 12,
          runSpacing: 8,
          alignment: WrapAlignment.center,
          children: AppCategories.buckets.map((b) {
            final pct = widget.plan.percentageFor(b.bucket);
            return InkWell(
              onTap: () => widget.onBucketTap?.call(b.bucket),
              borderRadius: BorderRadius.circular(12),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(
                        color: b.color,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 6),
                    Text(
                      '${b.titleAr} (${pct.round()}%)',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                          ),
                    ),
                  ],
                ),
              ),
            );
          }).toList(),
        ),
      ],
    );
  }
}
