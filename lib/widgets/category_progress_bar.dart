import 'package:flutter/material.dart';

class CategoryProgressBar extends StatelessWidget {
  const CategoryProgressBar({
    super.key,
    required this.ratio,
    this.height = 10,
  });

  final double ratio;
  final double height;

  Color _colorForRatio(double r) {
    if (r >= 0.95) return const Color(0xFFEF4444); // Red
    if (r >= 0.70) return const Color(0xFFF59E0B); // Orange
    return const Color(0xFF10B981); // Green
  }

  @override
  Widget build(BuildContext context) {
    final clamped = ratio.clamp(0.0, 1.0);
    final barColor = _colorForRatio(ratio);

    return ClipRRect(
      borderRadius: BorderRadius.circular(height),
      child: Container(
        height: height,
        width: double.infinity,
        color: barColor.withValues(alpha: 0.16),
        child: TweenAnimationBuilder<double>(
          tween: Tween<double>(begin: 0, end: clamped),
          duration: const Duration(milliseconds: 500),
          curve: Curves.easeOutCubic,
          builder: (context, value, _) {
            return FractionallySizedBox(
              alignment: AlignmentDirectional.centerStart,
              widthFactor: value,
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 300),
                decoration: BoxDecoration(
                  color: barColor,
                  borderRadius: BorderRadius.circular(height),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
