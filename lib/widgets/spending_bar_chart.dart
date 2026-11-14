import 'dart:math' as math;

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../core/constants/categories.dart';
import '../core/constants/currencies.dart';
import '../core/utils/arabic_formatters.dart';
import '../models/budget_plan.dart';

class SpendingBarPoint {
  const SpendingBarPoint({required this.label, required this.amount});

  final String label;
  final double amount;
}

class SpendingTrendBarChart extends StatelessWidget {
  const SpendingTrendBarChart({
    super.key,
    required this.points,
    required this.currency,
  });

  final List<SpendingBarPoint> points;
  final AppCurrency currency;

  @override
  Widget build(BuildContext context) {
    final maxAmount = points.fold<double>(
      10,
      (maxVal, p) => math.max(maxVal, p.amount),
    );
    final barColor = Theme.of(context).colorScheme.primary;
    final secondary = Theme.of(context).colorScheme.secondary;

    return SizedBox(
      height: 190,
      child: BarChart(
        BarChartData(
          alignment: BarChartAlignment.spaceAround,
          maxY: maxAmount * 1.18,
          gridData: FlGridData(
            show: true,
            drawVerticalLine: false,
            horizontalInterval: math.max(maxAmount / 3, 1),
            getDrawingHorizontalLine: (val) => FlLine(
              color: Colors.grey.withValues(alpha: 0.16),
              strokeWidth: 1,
            ),
          ),
          borderData: FlBorderData(show: false),
          barTouchData: BarTouchData(
            touchTooltipData: BarTouchTooltipData(
              getTooltipItem: (group, groupIdx, rod, rodIdx) {
                final pt = points[group.x.toInt()];
                return BarTooltipItem(
                  '${pt.label}\n${ArabicFormatters.formatMoney(pt.amount, currency)}',
                  const TextStyle(
                    fontFamily: 'Cairo',
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                    fontSize: 11.5,
                  ),
                );
              },
            ),
          ),
          titlesData: FlTitlesData(
            topTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
            rightTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
            leftTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                reservedSize: 30,
                getTitlesWidget: (value, meta) {
                  final i = value.toInt();
                  if (i < 0 || i >= points.length) {
                    return const SizedBox.shrink();
                  }
                  return SideTitleWidget(
                    meta: meta,
                    space: 6,
                    child: Text(
                      points[i].label,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            fontSize: 10.5,
                            fontWeight: FontWeight.w600,
                          ),
                    ),
                  );
                },
              ),
            ),
          ),
          barGroups: List.generate(points.length, (i) {
            return BarChartGroupData(
              x: i,
              barRods: [
                BarChartRodData(
                  toY: points[i].amount,
                  width: points.length <= 4 ? 26 : 16,
                  borderRadius: const BorderRadius.vertical(
                    top: Radius.circular(8),
                  ),
                  gradient: LinearGradient(
                    begin: Alignment.bottomCenter,
                    end: Alignment.topCenter,
                    colors: [barColor, secondary],
                  ),
                ),
              ],
            );
          }),
        ),
        duration: const Duration(milliseconds: 450),
        curve: Curves.easeOutCubic,
      ),
    );
  }
}

class PlanVsActualBarChart extends StatelessWidget {
  const PlanVsActualBarChart({
    super.key,
    required this.plan,
    required this.spentByBucket,
    required this.currency,
  });

  final BudgetPlan plan;
  final Map<BudgetBucket, double> spentByBucket;
  final AppCurrency currency;

  @override
  Widget build(BuildContext context) {
    final buckets = AppCategories.buckets;
    double maxY = 10;
    for (final b in buckets) {
      maxY = math.max(maxY, plan.amountFor(b.bucket));
      maxY = math.max(maxY, spentByBucket[b.bucket] ?? 0);
    }

    return Column(
      children: [
        SizedBox(
          height: 210,
          child: BarChart(
            BarChartData(
              alignment: BarChartAlignment.spaceAround,
              maxY: maxY * 1.18,
              gridData: FlGridData(
                show: true,
                drawVerticalLine: false,
                horizontalInterval: math.max(maxY / 3, 1),
                getDrawingHorizontalLine: (val) => FlLine(
                  color: Colors.grey.withValues(alpha: 0.16),
                  strokeWidth: 1,
                ),
              ),
              borderData: FlBorderData(show: false),
              barTouchData: BarTouchData(
                touchTooltipData: BarTouchTooltipData(
                  getTooltipItem: (group, groupIdx, rod, rodIdx) {
                    final b = buckets[group.x.toInt()];
                    final kind = rodIdx == 0 ? 'المخطط' : 'الواقع';
                    return BarTooltipItem(
                      '${b.titleAr} ($kind)\n${ArabicFormatters.formatMoney(rod.toY, currency)}',
                      const TextStyle(
                        fontFamily: 'Cairo',
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                        fontSize: 11,
                      ),
                    );
                  },
                ),
              ),
              titlesData: FlTitlesData(
                topTitles: const AxisTitles(
                  sideTitles: SideTitles(showTitles: false),
                ),
                rightTitles: const AxisTitles(
                  sideTitles: SideTitles(showTitles: false),
                ),
                leftTitles: const AxisTitles(
                  sideTitles: SideTitles(showTitles: false),
                ),
                bottomTitles: AxisTitles(
                  sideTitles: SideTitles(
                    showTitles: true,
                    reservedSize: 34,
                    getTitlesWidget: (value, meta) {
                      final idx = value.toInt();
                      if (idx < 0 || idx >= buckets.length) {
                        return const SizedBox.shrink();
                      }
                      final label = buckets[idx].titleAr.split(' ').first;
                      return SideTitleWidget(
                        meta: meta,
                        space: 6,
                        child: Text(
                          label,
                          style:
                              Theme.of(context).textTheme.bodyMedium?.copyWith(
                                    fontSize: 10.5,
                                    fontWeight: FontWeight.w700,
                                  ),
                        ),
                      );
                    },
                  ),
                ),
              ),
              barGroups: List.generate(buckets.length, (i) {
                final b = buckets[i];
                final planned = plan.amountFor(b.bucket);
                final actual = spentByBucket[b.bucket] ?? 0.0;
                final actualColor = actual > planned && planned > 0
                    ? const Color(0xFFEF4444)
                    : const Color(0xFFD4AF37);

                return BarChartGroupData(
                  x: i,
                  barsSpace: 5,
                  barRods: [
                    BarChartRodData(
                      toY: planned,
                      width: 11,
                      color: const Color(0xFF10B981),
                      borderRadius: const BorderRadius.vertical(
                        top: Radius.circular(6),
                      ),
                    ),
                    BarChartRodData(
                      toY: actual,
                      width: 11,
                      color: actualColor,
                      borderRadius: const BorderRadius.vertical(
                        top: Radius.circular(6),
                      ),
                    ),
                  ],
                );
              }),
            ),
            duration: const Duration(milliseconds: 450),
            curve: Curves.easeOutCubic,
          ),
        ),
        const SizedBox(height: 8),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            _legendDot('الخطة', const Color(0xFF10B981)),
            const SizedBox(width: 20),
            _legendDot('الواقع', const Color(0xFFD4AF37)),
            const SizedBox(width: 20),
            _legendDot('تجاوز', const Color(0xFFEF4444)),
          ],
        ),
      ],
    );
  }

  Widget _legendDot(String title, Color color) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 6),
        Text(
          title,
          style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
        ),
      ],
    );
  }
}
