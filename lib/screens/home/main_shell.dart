import 'package:flutter/material.dart';

import '../../core/constants/financial_tips_data.dart';
import '../../core/utils/arabic_formatters.dart';
import '../../state/app_controller.dart';
import '../../widgets/quick_expense_sheet.dart';
import '../advice/advice_screen.dart';
import '../expenses/expenses_screen.dart';
import '../goals/goals_screen.dart';
import '../more/more_screen.dart';
import '../plan/plan_screen.dart';

class MainShell extends StatefulWidget {
  const MainShell({super.key, required this.controller});

  final AppController controller;

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _tabIndex = 0;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onControllerUpdate);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onControllerUpdate);
    super.dispose();
  }

  void _onControllerUpdate() {
    final badgeId = widget.controller.pendingBadgeBanner;
    if (badgeId != null && mounted) {
      final badge = FinancialTipsData.badges.firstWhere(
        (b) => b.id == badgeId,
        orElse: () => FinancialTipsData.badges.first,
      );
      widget.controller.clearBadgeBanner();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: const Color(0xFF0B6E4F),
          content: Row(
            children: [
              const Icon(Icons.emoji_events_rounded, color: Color(0xFFF5C443)),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  'مبارك! حصلت على شارة «${badge.titleAr}» 🏅',
                  style: const TextStyle(
                    fontFamily: 'Cairo',
                    fontWeight: FontWeight.w700,
                    color: Colors.white,
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        final c = widget.controller;
        final theme = Theme.of(context);
        final profile = c.profile;
        final daysLeft = profile != null
            ? ArabicFormatters.daysUntilSalary(profile.salaryDay)
            : 0;

        final screens = [
          PlanScreen(controller: c),
          ExpensesScreen(controller: c),
          GoalsScreen(controller: c),
          AdviceScreen(controller: c),
          MoreScreen(controller: c),
        ];

        const titles = [
          'خطة راتبك',
          'متابعة المصاريف',
          'الأهداف المالية',
          'النصائح الذكية',
          'المزيد والإضافات',
        ];

        return Scaffold(
          appBar: AppBar(
            title: FittedBox(
              fit: BoxFit.scaleDown,
              alignment: AlignmentDirectional.centerStart,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(titles[_tabIndex]),
                  if (profile != null && _tabIndex == 0) ...[
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.secondary.withValues(alpha: 0.16),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        '$daysLeft يوم للراتب',
                        style: TextStyle(
                          fontFamily: 'Cairo',
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: theme.colorScheme.secondary,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
            leading: IconButton(
              tooltip: c.settings.soundEnabled
                  ? 'إيقاف الأصوات'
                  : 'تشغيل الأصوات',
              icon: Icon(
                c.settings.soundEnabled
                    ? Icons.volume_up_rounded
                    : Icons.volume_off_rounded,
                color: c.settings.soundEnabled
                    ? theme.colorScheme.primary
                    : Colors.grey,
              ),
              onPressed: c.toggleSound,
            ),
            actions: [
              IconButton(
                tooltip: c.settings.musicEnabled
                    ? 'إيقاف الموسيقى الهادئة'
                    : 'تشغيل الموسيقى الهادئة',
                icon: Icon(
                  c.settings.musicEnabled
                      ? Icons.music_note_rounded
                      : Icons.music_off_rounded,
                  color: c.settings.musicEnabled
                      ? theme.colorScheme.secondary
                      : Colors.grey,
                ),
                onPressed: c.toggleMusic,
              ),
              IconButton(
                tooltip: c.settings.isDarkMode ? 'الوضع النهاري' : 'الوضع الليلي',
                icon: Icon(
                  c.settings.isDarkMode
                      ? Icons.light_mode_rounded
                      : Icons.dark_mode_rounded,
                ),
                onPressed: c.toggleDarkMode,
              ),
            ],
          ),
          body: IndexedStack(
            index: _tabIndex,
            children: screens,
          ),
          floatingActionButton: FloatingActionButton.extended(
            onPressed: () => showQuickExpenseSheet(context, c),
            backgroundColor: theme.colorScheme.secondary,
            foregroundColor: const Color(0xFF1A1302),
            icon: const Icon(Icons.add_rounded, size: 26),
            label: const Text(
              'مصروف',
              style: TextStyle(
                fontFamily: 'Cairo',
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          bottomNavigationBar: NavigationBar(
            selectedIndex: _tabIndex,
            onDestinationSelected: (idx) => setState(() => _tabIndex = idx),
            destinations: const [
              NavigationDestination(
                icon: Icon(Icons.pie_chart_outline_rounded),
                selectedIcon: Icon(Icons.pie_chart_rounded),
                label: 'الخطة',
              ),
              NavigationDestination(
                icon: Icon(Icons.receipt_long_outlined),
                selectedIcon: Icon(Icons.receipt_long_rounded),
                label: 'المصاريف',
              ),
              NavigationDestination(
                icon: Icon(Icons.flag_outlined),
                selectedIcon: Icon(Icons.flag_rounded),
                label: 'الأهداف',
              ),
              NavigationDestination(
                icon: Icon(Icons.tips_and_updates_outlined),
                selectedIcon: Icon(Icons.tips_and_updates_rounded),
                label: 'النصائح',
              ),
              NavigationDestination(
                icon: Icon(Icons.grid_view_outlined),
                selectedIcon: Icon(Icons.grid_view_rounded),
                label: 'المزيد',
              ),
            ],
          ),
        );
      },
    );
  }
}
