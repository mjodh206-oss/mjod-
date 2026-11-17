import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';

import '../../core/utils/page_transitions.dart';
import '../../state/app_controller.dart';
import '../setup/setup_wizard_screen.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen>
    with SingleTickerProviderStateMixin {
  final PageController _pageController = PageController();
  late final AnimationController _pulseController;
  late final Animation<double> _pulseAnim;
  int _currentPage = 0;

  static const _slides = [
    (
      title: 'اكتب راتبك فقط',
      subtitle: 'اختر عملتك واكتب راتبك الشهري خلال 5 ثوانٍ، بدون أي تعقيد أو حسابات صعبة.',
      lottie: 'assets/lottie/onboarding_pen.json',
      fallbackIcon: Icons.edit_note_rounded,
    ),
    (
      title: 'خطتك بتجهز تلقائياً',
      subtitle: 'BNB بيقسم راتبك فوراً: بيت وأكل، مصروفك الشخصي، وتوفير مضمون من أول يوم.',
      lottie: 'assets/lottie/onboarding_questions.json',
      fallbackIcon: Icons.auto_awesome_rounded,
    ),
    (
      title: 'اعرف كم تصرف باليوم',
      subtitle: 'بتشوف قدامك كم مسموح تصرف كل يوم وأسبوع حتى يكفيك الراتب لآخر الشهر وتوفّر كمان.',
      lottie: 'assets/lottie/onboarding_chart.json',
      fallbackIcon: Icons.pie_chart_rounded,
    ),
  ];

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1100),
    )..repeat(reverse: true);
    _pulseAnim = Tween<double>(begin: 0.97, end: 1.03).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pageController.dispose();
    _pulseController.dispose();
    super.dispose();
  }

  Future<void> _finishOnboarding() async {
    await widget.controller.markOnboardingComplete();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      SmoothPageRoute(
        builder: (_) => SetupWizardScreen(controller: widget.controller),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isLast = _currentPage == _slides.length - 1;

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: Column(
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'BNB',
                    style: theme.textTheme.titleLarge?.copyWith(
                      color: theme.colorScheme.primary,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  TextButton(
                    onPressed: _finishOnboarding,
                    child: Text(
                      'تخطي',
                      style: TextStyle(
                        fontFamily: 'Cairo',
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: theme.colorScheme.secondary,
                      ),
                    ),
                  ),
                ],
              ),
              Expanded(
                child: PageView.builder(
                  controller: _pageController,
                  itemCount: _slides.length,
                  onPageChanged: (idx) => setState(() => _currentPage = idx),
                  itemBuilder: (context, idx) {
                    final slide = _slides[idx];
                    return Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          width: 240,
                          height: 240,
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            gradient: RadialGradient(
                              colors: [
                                theme.colorScheme.secondary
                                    .withValues(alpha: 0.16),
                                theme.colorScheme.primary
                                    .withValues(alpha: 0.05),
                              ],
                            ),
                          ),
                          child: Lottie.asset(
                            slide.lottie,
                            fit: BoxFit.contain,
                            errorBuilder: (_, __, ___) => Icon(
                              slide.fallbackIcon,
                              size: 110,
                              color: theme.colorScheme.primary,
                            ),
                          ),
                        ),
                        const SizedBox(height: 28),
                        Text(
                          slide.title,
                          textAlign: TextAlign.center,
                          style: theme.textTheme.headlineLarge?.copyWith(
                            fontSize: 28,
                          ),
                        ),
                        const SizedBox(height: 12),
                        Text(
                          slide.subtitle,
                          textAlign: TextAlign.center,
                          style: theme.textTheme.bodyLarge?.copyWith(
                            color: theme.textTheme.bodyMedium?.color,
                            height: 1.6,
                          ),
                        ),
                      ],
                    );
                  },
                ),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(_slides.length, (i) {
                  final active = i == _currentPage;
                  return AnimatedContainer(
                    duration: const Duration(milliseconds: 250),
                    margin: const EdgeInsets.symmetric(horizontal: 5),
                    height: 9,
                    width: active ? 28 : 9,
                    decoration: BoxDecoration(
                      color: active
                          ? theme.colorScheme.secondary
                          : theme.colorScheme.primary.withValues(alpha: 0.25),
                      borderRadius: BorderRadius.circular(10),
                    ),
                  );
                }),
              ),
              const SizedBox(height: 24),
              ScaleTransition(
                scale: isLast
                    ? _pulseAnim
                    : const AlwaysStoppedAnimation<double>(1.0),
                child: FilledButton.icon(
                  onPressed: isLast
                      ? _finishOnboarding
                      : () {
                          _pageController.nextPage(
                            duration: const Duration(milliseconds: 350),
                            curve: Curves.easeOutCubic,
                          );
                        },
                  icon: Icon(
                    isLast
                        ? Icons.rocket_launch_rounded
                        : Icons.arrow_back_rounded,
                  ),
                  label: Text(isLast ? 'يلا نبدأ' : 'التالي'),
                ),
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }
}
