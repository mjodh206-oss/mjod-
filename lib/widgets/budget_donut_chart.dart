import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../core/constants/categories.dart';
import '../core/constants/currencies.dart';
import '../core/utils/arabic_formatters.dart';
import '../models/budget_plan.dart';
import '../services/plan_engine.dart';

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

  String _sliceMoneyLabel(double amount) {
    final abs = amount.abs();
    if (abs >= 1000000) {
      return '${(abs / 1000000).toStringAsFixed(1)}M';
    }
    if (abs >= 10000) {
      return '${(abs / 1000).toStringAsFixed(0)}K';
    }
    return ArabicFormatters.formatNumber(
      amount,
      allowDecimals: widget.currency.allowDecimals,
    );
  }

  @override
  Widget build(BuildContext context) {
    final splits = PlanEngine.exactMoneySplit(
      plan: widget.plan,
      currency: widget.currency,
    );
    final activeBuckets = AppCategories.buckets
        .where((b) => (splits[b.bucket] ?? 0.0) > 0)
        .toList();

    final selectedMeta = (_touchedIndex >= 0 && _touchedIndex < activeBuckets.length)
        ? activeBuckets[_touchedIndex]
        : null;

    return Column(
      children: [
        SizedBox(
          height: 225,
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
                      centerSpaceRadius: 60,
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
                        final amount = splits[meta.bucket] ?? 0.0;
                        final pct = widget.plan.percentageFor(meta.bucket);
                        final isTouched = i == _touchedIndex;
                        return PieChartSectionData(
                          color: meta.color,
                          value: _appeared ? mathMax(amount, 0.1) : (i == 0 ? 100 : 0.1),
                          title: pct >= 9 ? _sliceMoneyLabel(amount) : '',
                          radius: isTouched ? 52 : 44,
                          titleStyle: const TextStyle(
                            fontFamily: 'Cairo',
                            fontSize: 11.5,
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
                width: 110,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      selectedMeta?.titleAr ?? 'إجمالي راتبك',
                      textAlign: TextAlign.center,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                          ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      ArabicFormatters.formatMoney(
                        selectedMeta != null
                            ? (splits[selectedMeta.bucket] ?? 0.0)
                            : widget.plan.totalIncome,
                        widget.currency,
                      ),
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontSize: 13.5,
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
        ...activeBuckets.map((b) {
          final amount = splits[b.bucket] ?? 0.0;
          return InkWell(
            onTap: () => widget.onBucketTap?.call(b.bucket),
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
              child: Row(
                children: [
                  Container(
                    width: 11,
                    height: 11,
                    decoration: BoxDecoration(
                      color: b.color,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      b.titleAr,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                          ),
                    ),
                  ),
                  Text(
                    ArabicFormatters.formatMoney(amount, widget.currency),
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          fontSize: 14,
                          fontWeight: FontWeight.w800,
                          color: b.color,
                        ),
                  ),
                ],
              ),
            ),
          );
        }),
      ],
    );
  }

  double mathMax(double a, double b) => a > b ? a : b;
}
