import 'package:flutter/material.dart';

import '../../core/constants/financial_tips_data.dart';
import '../../state/app_controller.dart';

class BadgesScreen extends StatelessWidget {
  const BadgesScreen({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final unlocked = controller.settings.unlockedBadgeIds.toSet();

    return Scaffold(
      appBar: AppBar(title: const Text('الشارات والإنجازات')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            color: theme.colorScheme.secondary.withValues(alpha: 0.14),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  const Icon(
                    Icons.emoji_events_rounded,
                    color: Color(0xFFD4AF37),
                    size: 36,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'حصلت على ${unlocked.length} من أصل ${FinancialTipsData.badges.length} شارات',
                          style: theme.textTheme.titleMedium,
                        ),
                        Text(
                          'كل خطوة انضباط وتوفير تفتح لك وساماً جديداً!',
                          style: theme.textTheme.bodyMedium,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          ...FinancialTipsData.badges.map((b) {
            final isEarned = unlocked.contains(b.id);
            return Card(
              margin: const EdgeInsets.only(bottom: 10),
              child: ListTile(
                contentPadding: const EdgeInsets.all(14),
                leading: CircleAvatar(
                  radius: 26,
                  backgroundColor: isEarned
                      ? b.color.withValues(alpha: 0.18)
                      : Colors.grey.withValues(alpha: 0.14),
                  child: Icon(
                    b.icon,
                    size: 28,
                    color: isEarned ? b.color : Colors.grey,
                  ),
                ),
                title: Row(
                  children: [
                    Expanded(
                      child: Text(
                        b.titleAr,
                        style: theme.textTheme.titleMedium?.copyWith(
                          color: isEarned ? null : Colors.grey,
                        ),
                      ),
                    ),
                    if (isEarned)
                      const Icon(
                        Icons.verified_rounded,
                        color: Color(0xFF10B981),
                        size: 20,
                      )
                    else
                      const Icon(
                        Icons.lock_outline_rounded,
                        color: Colors.grey,
                        size: 18,
                      ),
                  ],
                ),
                subtitle: Text(
                  b.descriptionAr,
                  style: theme.textTheme.bodyMedium,
                ),
              ),
            );
          }),
        ],
      ),
    );
  }
}
