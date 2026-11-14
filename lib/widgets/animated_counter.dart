import 'package:flutter/material.dart';

class AnimatedNumberText extends StatelessWidget {
  const AnimatedNumberText({
    super.key,
    required this.value,
    required this.formatter,
    this.style,
    this.duration = const Duration(milliseconds: 700),
    this.textAlign,
  });

  final double value;
  final String Function(double val) formatter;
  final TextStyle? style;
  final Duration duration;
  final TextAlign? textAlign;

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: value),
      duration: duration,
      curve: Curves.easeOutCubic,
      builder: (context, animatedVal, _) {
        return Text(
          formatter(animatedVal),
          style: style,
          textAlign: textAlign,
        );
      },
    );
  }
}

class AnimatedStepNumber extends StatelessWidget {
  const AnimatedStepNumber({
    super.key,
    required this.step,
    required this.total,
    this.style,
  });

  final int step;
  final int total;
  final TextStyle? style;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text('السؤال ', style: style),
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 280),
          transitionBuilder: (child, anim) {
            return SlideTransition(
              position: Tween<Offset>(
                begin: const Offset(0, 0.5),
                end: Offset.zero,
              ).animate(anim),
              child: FadeTransition(opacity: anim, child: child),
            );
          },
          child: Text(
            '$step',
            key: ValueKey<int>(step),
            style: style?.copyWith(fontWeight: FontWeight.w800),
          ),
        ),
        Text(' من $total', style: style),
      ],
    );
  }
}
