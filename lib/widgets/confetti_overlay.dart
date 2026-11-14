import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';

class ConfettiOverlay extends StatefulWidget {
  const ConfettiOverlay({
    super.key,
    required this.visible,
    this.onFinished,
  });

  final bool visible;
  final VoidCallback? onFinished;

  @override
  State<ConfettiOverlay> createState() => _ConfettiOverlayState();
}

class _ConfettiOverlayState extends State<ConfettiOverlay>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final List<_Particle> _particles;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2200),
    )..addStatusListener((status) {
        if (status == AnimationStatus.completed) {
          widget.onFinished?.call();
        }
      });

    final rng = math.Random(42);
    const palette = [
      Color(0xFFD4AF37),
      Color(0xFF10B981),
      Color(0xFFF59E0B),
      Color(0xFF0EA5E9),
      Color(0xFFEF4444),
      Color(0xFFFDE047),
    ];
    _particles = List.generate(48, (i) {
      return _Particle(
        xFraction: rng.nextDouble(),
        speed: 0.55 + rng.nextDouble() * 0.7,
        size: 6 + rng.nextDouble() * 8,
        drift: (rng.nextDouble() - 0.5) * 80,
        rotationSpeed: (rng.nextDouble() - 0.5) * 10,
        color: palette[i % palette.length],
        isCircle: i % 3 == 0,
      );
    });

    if (widget.visible) {
      _controller.forward(from: 0);
    }
  }

  @override
  void didUpdateWidget(covariant ConfettiOverlay oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.visible && !oldWidget.visible) {
      _controller.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.visible) return const SizedBox.shrink();

    return IgnorePointer(
      child: Stack(
        fit: StackFit.expand,
        children: [
          AnimatedBuilder(
            animation: _controller,
            builder: (context, _) {
              return CustomPaint(
                painter: _ConfettiPainter(
                  progress: _controller.value,
                  particles: _particles,
                ),
              );
            },
          ),
          Center(
            child: SizedBox(
              width: 280,
              height: 280,
              child: Lottie.asset(
                'assets/lottie/celebration_confetti.json',
                repeat: false,
                fit: BoxFit.contain,
                errorBuilder: (_, __, ___) => const SizedBox.shrink(),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Particle {
  const _Particle({
    required this.xFraction,
    required this.speed,
    required this.size,
    required this.drift,
    required this.rotationSpeed,
    required this.color,
    required this.isCircle,
  });

  final double xFraction;
  final double speed;
  final double size;
  final double drift;
  final double rotationSpeed;
  final Color color;
  final bool isCircle;
}

class _ConfettiPainter extends CustomPainter {
  _ConfettiPainter({required this.progress, required this.particles});

  final double progress;
  final List<_Particle> particles;

  @override
  void paint(Canvas canvas, Size size) {
    final fade = progress > 0.78 ? (1.0 - (progress - 0.78) / 0.22) : 1.0;
    for (final p in particles) {
      final paint = Paint()
        ..color = p.color.withValues(alpha: fade.clamp(0.0, 1.0));
      final x = p.xFraction * size.width +
          math.sin(progress * math.pi * 2) * p.drift * 0.4;
      final y = -20 + progress * size.height * 1.15 * p.speed;

      canvas.save();
      canvas.translate(x, y);
      canvas.rotate(progress * p.rotationSpeed);
      if (p.isCircle) {
        canvas.drawCircle(Offset.zero, p.size * 0.5, paint);
      } else {
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            Rect.fromCenter(
              center: Offset.zero,
              width: p.size * 1.2,
              height: p.size * 0.65,
            ),
            const Radius.circular(2),
          ),
          paint,
        );
      }
      canvas.restore();
    }
  }

  @override
  bool shouldRepaint(covariant _ConfettiPainter oldDelegate) =>
      oldDelegate.progress != progress;
}
