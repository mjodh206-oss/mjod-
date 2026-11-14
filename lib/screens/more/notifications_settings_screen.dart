import 'package:flutter/material.dart';

import '../../state/app_controller.dart';

class NotificationsSettingsScreen extends StatelessWidget {
  const NotificationsSettingsScreen({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final s = controller.settings;
        final theme = Theme.of(context);
        final eveningTime = TimeOfDay(hour: s.eveningHour, minute: s.eveningMinute);

        return Scaffold(
          appBar: AppBar(title: const Text('الإشعارات والأصوات')),
          body: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text('الأصوات والمؤثرات', style: theme.textTheme.titleLarge),
              const SizedBox(height: 8),
              Card(
                child: Column(
                  children: [
                    SwitchListTile(
                      title: const Text('المؤثرات الصوتية'),
                      subtitle: const Text(
                        'صوت الافتتاحية، صوت إضافة المصروف، وصوت النجاح',
                      ),
                      value: s.soundEnabled,
                      onChanged: (v) => controller.updateSettings(
                        s.copyWith(soundEnabled: v),
                      ),
                    ),
                    const Divider(height: 1),
                    SwitchListTile(
                      title: const Text('موسيقى خلفية هادئة'),
                      subtitle: const Text('نغمة مريحة أثناء تنظيم ميزانيتك'),
                      value: s.musicEnabled,
                      onChanged: (v) => controller.updateSettings(
                        s.copyWith(musicEnabled: v),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 18),
              Text('الإشعارات الذكية', style: theme.textTheme.titleLarge),
              const SizedBox(height: 8),
              Card(
                child: Column(
                  children: [
                    SwitchListTile(
                      title: const Text('تذكير يوم الراتب 💰'),
                      subtitle: const Text(
                        '«نزل الراتب! خلينا نوزعه» في يوم استلام راتبك',
                      ),
                      value: s.salaryDayReminder,
                      onChanged: (v) => controller.updateSettings(
                        s.copyWith(salaryDayReminder: v),
                      ),
                    ),
                    const Divider(height: 1),
                    SwitchListTile(
                      title: const Text('تذكير مسائي بتسجيل المصاريف'),
                      subtitle: Text(
                        'يومياً الساعة ${eveningTime.format(context)}',
                      ),
                      value: s.eveningReminder,
                      onChanged: (v) => controller.updateSettings(
                        s.copyWith(eveningReminder: v),
                      ),
                    ),
                    if (s.eveningReminder)
                      ListTile(
                        leading: const Icon(Icons.access_time_rounded),
                        title: const Text('تعديل وقت التذكير المسائي'),
                        trailing: Text(
                          eveningTime.format(context),
                          style: theme.textTheme.titleMedium,
                        ),
                        onTap: () async {
                          final picked = await showTimePicker(
                            context: context,
                            initialTime: eveningTime,
                          );
                          if (picked != null) {
                            await controller.updateSettings(
                              s.copyWith(
                                eveningHour: picked.hour,
                                eveningMinute: picked.minute,
                              ),
                            );
                          }
                        },
                      ),
                    const Divider(height: 1),
                    SwitchListTile(
                      title: const Text('التحفيز الأسبوعي'),
                      subtitle: const Text('«وفرت هذا الأسبوع كذا، أحسنت!»'),
                      value: s.weeklyMotivation,
                      onChanged: (v) => controller.updateSettings(
                        s.copyWith(weeklyMotivation: v),
                      ),
                    ),
                    const Divider(height: 1),
                    SwitchListTile(
                      title: const Text('تذكير مواعيد الأقساط والفواتير'),
                      subtitle: const Text('تنبيه قبل استحقاق الفواتير والديون'),
                      value: s.billDueReminder,
                      onChanged: (v) => controller.updateSettings(
                        s.copyWith(billDueReminder: v),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 18),
              FilledButton.icon(
                onPressed: () async {
                  await controller.notifications.requestPermission();
                  await controller.notifications.showTestNotification();
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('تم إرسال إشعار تجريبي إلى هاتفك ✓'),
                      ),
                    );
                  }
                },
                icon: const Icon(Icons.notifications_active_rounded),
                label: const Text('إرسال إشعار تجريبي الآن'),
              ),
            ],
          ),
        );
      },
    );
  }
}
