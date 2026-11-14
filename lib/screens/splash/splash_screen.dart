import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';

import '../../core/utils/page_transitions.dart';
import '../../state/app_controller.dart';
import '../../widgets/rattib_logo.dart';
import '../home/main_shell.dart';
import '../onboarding/onboarding_screen.dart';
import '../security/lock_screen.dart';
import '../setup/setup_wizard_screen.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({
    super.key,
    required this.controller,
    this.autoNavigate = true,
  });

  final AppController controller;
  final bool autoNavigate;

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with TickerProviderStateMixin {
  late final AnimationController _iconController;
  late final AnimationController _coinsController;
  late final Animation<double> _scaleAnim;
  late final Animation<double> _glowAnim;

  static const String _fullTitle = 'رَتِّبْ';
  int _typedLength = 0;
  bool _showSubtitle = false;
  Timer? _typeTimer;
  Timer? _navTimer;

  @override
  void initState() {
    super.initState();

    _iconController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    );
    _scaleAnim = Tween<double>(begin: 0.45, end: 1.0).animate(
      CurvedAnimation(parent: _iconController, curve: Curves.easeOutBack),
    );
    _glowAnim = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _iconController, curve: Curves.easeInOut),
    );

    _coinsController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    );

    _iconController.forward();
    _coinsController.forward();
    widget.controller.sound.playSplashIntro();

    // Typewriter animation for "رَتِّبْ"
    _typeTimer = Timer.periodic(const Duration(milliseconds: 110), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      if (_typedLength < _fullTitle.length) {
        setState(() => _typedLength++);
      } else {
        timer.cancel();
        if (mounted && !_showSubtitle) {
          setState(() => _showSubtitle = true);
        }
      }
    });

    if (widget.autoNavigate) {
      _navTimer = Timer(const Duration(milliseconds: 2450), _navigateNext);
    }
  }

  void _navigateNext() {
    if (!mounted) return;
    final c = widget.controller;
    final Widget next;
    if (!c.settings.hasSeenOnboarding) {
      next = OnboardingScreen(controller: c);
    } else if (!c.settings.hasCompletedSetup || c.profile == null) {
      next = SetupWizardScreen(controller: c);
    } else if (c.isLocked) {
      next = LockScreen(controller: c);
    } else {
      next = MainShell(controller: c);
    }
    Navigator.of(context).pushReplacement(
      FadePageRoute(builder: (_) => next),
    );
  }

  @override
  void dispose() {
    _typeTimer?.cancel();
    _navTimer?.cancel();
    _iconController.dispose();
    _coinsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final typedTitle = _fullTitle.substring(0, _typedLength);

    return Scaffold(
      backgroundColor: const Color(0xFF051811),
      body: Container(
        decoration: const BoxDecoration(
          gradient: RadialGradient(
            center: Alignment(0, -0.2),
            radius: 1.15,
            colors: [
              Color(0xFF0E3E2D),
              Color(0xFF061B14),
              Color(0xFF030F0B),
            ],
          ),
        ),
        child: SafeArea(
          child: Stack(
            children: [
              // Falling golden coins collecting into the wallet
              Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    SizedBox(
                      width: 220,
                      height: 220,
                      child: Stack(
                        alignment: Alignment.center,
                        children: [
                          Positioned(
                            top: 0,
                            child: SizedBox(
                              width: 170,
                              height: 150,
                              child: Lottie.asset(
                                'assets/lottie/splash_coins.json',
                                repeat: false,
                                fit: BoxFit.contain,
                                errorBuilder: (_, __, ___) =>
                                    const SizedBox.shrink(),
                              ),
                            ),
                          ),
                          AnimatedBuilder(
                            animation: _coinsController,
                            builder: (context, _) {
                              return CustomPaint(
                                size: const Size(210, 210),
                                painter: _FallingCoinsPainter(
                                  progress: _coinsController.value,
                                ),
                              );
                            },
                          ),
                          AnimatedBuilder(
                            animation: _iconController,
                            builder: (context, child) {
                              return Transform.scale(
                                scale: _scaleAnim.value,
                                child: Container(
                                  decoration: BoxDecoration(
                                    borderRadius: BorderRadius.circular(32),
                                    boxShadow: [
                                      BoxShadow(
                                        color: const Color(0xFFF5C443)
                                            .withValues(
                                          alpha: 0.36 * _glowAnim.value,
                                        ),
                                        blurRadius: 34 * _glowAnim.value,
                                        spreadRadius: 4 * _glowAnim.value,
                                      ),
                                    ],
                                  ),
                                  child: child,
                                ),
                              );
                            },
                            child: const Padding(
                              padding: EdgeInsets.only(top: 32),
                              child: RattibLogo(size: 118, showGlow: false),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 18),
                    Text(
                      typedTitle,
                      style: const TextStyle(
                        fontFamily: 'Cairo',
                        fontSize: 44,
                        fontWeight: FontWeight.w800,
                        color: Color(0xFFF5C443),
                        letterSpacing: 1.5,
                      ),
                    ),
                    const SizedBox(height: 8),
                    AnimatedOpacity(
                      opacity: _showSubtitle ? 1.0 : 0.0,
                      duration: const Duration(milliseconds: 500),
                      child: const Text(
                        'فلوسك بتستاهل خطة',
                        style: TextStyle(
                          fontFamily: 'Cairo',
                          fontSize: 18,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFFD7F3E6),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              Positioned(
                bottom: 24,
                left: 0,
                right: 0,
                child: Column(
                  children: [
                    Text(
                      'تنفيذ وتطوير أمجد حبيب',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontFamily: 'Cairo',
                        fontSize: 12.5,
                        fontWeight: FontWeight.w600,
                        color: const Color(0xFFA7D3C0).withValues(alpha: 0.85),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _FallingCoinsPainter extends CustomPainter {
  _FallingCoinsPainter({required this.progress});

  final double progress;

  @override
  void paint(Canvas canvas, Size size) {
    final coins = [
      (startX: 0.25, delay: 0.00, r: 9.0),
      (startX: 0.50, delay: 0.12, r: 10.5),
      (startX: 0.75, delay: 0.22, r: 8.5),
      (startX: 0.36, delay: 0.34, r: 8.0),
      (startX: 0.64, delay: 0.44, r: 9.0),
    ];

    for (final c in coins) {
      final local = ((progress - c.delay) / 0.45).clamp(0.0, 1.0);
      if (local <= 0 || local >= 1.0) continue;

      final ease = Curves.easeInQuad.transform(local);
      final x = size.width * (c.startX + (0.50 - c.startX) * ease * 0.55);
      final y = 12 + ease * (size.height * 0.54);
      final alpha = local < 0.15
          ? local / 0.15
          : (local > 0.85 ? (1.0 - local) / 0.15 : 1.0);

      final outer = Paint()
        ..color = const Color(0xFFF5C443).withValues(alpha: alpha);
      final inner = Paint()
        ..color = const Color(0xFFFFF2B2).withValues(alpha: alpha);
      final rim = Paint()
        ..color = const Color(0xFFB57B14).withValues(alpha: alpha)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.6;

      final scaleX = 0.65 + 0.35 * math.cos(local * math.pi * 4).abs();
      canvas.save();
      canvas.translate(x, y);
      canvas.scale(scaleX, 1.0);
      canvas.drawCircle(Offset.zero, c.r, outer);
      canvas.drawCircle(Offset.zero, c.r * 0.58, inner);
      canvas.drawCircle(Offset.zero, c.r, rim);
      canvas.restore();
    }
  }

  @override
  bool shouldRepaint(covariant _FallingCoinsPainter oldDelegate) =>
      oldDelegate.progress != progress;
}
