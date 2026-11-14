import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/theme/app_theme.dart';
import '../../core/utils/page_transitions.dart';
import '../../state/app_controller.dart';
import '../setup/setup_wizard_screen.dart';
import 'about_screen.dart';
import 'badges_screen.dart';
import 'notifications_settings_screen.dart';
import 'savings_calculator_screen.dart';

class MoreScreen extends StatefulWidget {
  const MoreScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<MoreScreen> createState() => _MoreScreenState();
}

class _MoreScreenState extends State<MoreScreen> {
  bool _busy = false;

  Future<void> _exportPdf() async {
    if (widget.controller.profile == null || widget.controller.plan == null) {
      return;
    }
    setState(() => _busy = true);
    try {
      await widget.controller.exportAndSharePdf();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('تعذر تصدير التقرير: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _exportBackup() async {
    setState(() => _busy = true);
    try {
      await widget.controller.exportAndShareBackup();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('تعذر تصدير النسخة الاحتياطية: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _importBackup() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['json'],
        withData: true,
      );
      if (result == null || result.files.isEmpty) return;
      final file = result.files.first;
      String rawJson;
      if (file.bytes != null) {
        rawJson = utf8.decode(file.bytes!);
      } else if (file.path != null) {
        rawJson = await File(file.path!).readAsString();
      } else {
        return;
      }
      await widget.controller.importBackupFromString(rawJson);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('تمت استعادة النسخة الاحتياطية بنجاح ✓'),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('ملف غير صالح: $e')),
        );
      }
    }
  }

  Future<void> _configurePin() async {
    final c = widget.controller;
    final ctrl = TextEditingController();
    final hasPin = (c.settings.pinCode ?? '').isNotEmpty;

    final action = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(hasPin ? 'تعديل رمز الحماية' : 'تعيين رمز حماية (4 أرقام)'),
        content: TextField(
          controller: ctrl,
          keyboardType: TextInputType.number,
          maxLength: 4,
          obscureText: true,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          decoration: const InputDecoration(
            hintText: 'أدخل 4 أرقام',
          ),
        ),
        actions: [
          if (hasPin)
            TextButton(
              onPressed: () => Navigator.of(ctx).pop('remove'),
              child: const Text(
                'إلغاء الرمز',
                style: TextStyle(color: Color(0xFFEF4444)),
              ),
            ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('إغلاق'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(ctx).pop('save'),
            child: const Text('حفظ'),
          ),
        ],
      ),
    );

    if (action == 'remove') {
      await c.updateSettings(c.settings.copyWith(clearPin: true));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('تمت إزالة رمز الحماية')),
        );
      }
    } else if (action == 'save') {
      final pin = ctrl.text.trim();
      if (pin.length != 4) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('يجب إدخال 4 أرقام بالضبط')),
          );
        }
        return;
      }
      await c.updateSettings(c.settings.copyWith(pinCode: pin));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('تم تفعيل قفل الرمز بنجاح ✓')),
        );
      }
    }
  }

  Future<void> _toggleBiometric(bool enable) async {
    final c = widget.controller;
    if (enable) {
      final available = await c.security.isBiometricAvailable();
      if (!available) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('جهازك لا يدعم البصمة أو لم يتم تفعيلها في النظام'),
            ),
          );
        }
        return;
      }
      final verified = await c.security.authenticateWithBiometrics();
      if (!verified) return;
    }
    await c.updateSettings(c.settings.copyWith(biometricEnabled: enable));
  }

  Future<void> _confirmReset() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('إعادة ضبط البيانات'),
        content: const Text(
          'سيتم مسح راتبك ومصاريفك وأهدافك والبدء من جديد. هل أنت متأكد؟',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('تراجع'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFFEF4444),
            ),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('نعم، امسح الكل'),
          ),
        ],
      ),
    );
    if (confirm != true || !mounted) return;
    await widget.controller.resetAllData();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      SmoothPageRoute(
        builder: (_) => SetupWizardScreen(controller: widget.controller),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final s = c.settings;
    final theme = Theme.of(context);

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
      children: [
        // Tools & Extras Grid
        Row(
          children: [
            Expanded(
              child: _quickCard(
                theme: theme,
                icon: Icons.calculate_rounded,
                color: const Color(0xFF10B981),
                title: 'حاسبة الادخار',
                subtitle: 'كم أحتاج لأدخر؟',
                onTap: () => Navigator.of(context).push(
                  SmoothPageRoute(
                    builder: (_) => SavingsCalculatorScreen(controller: c),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: _quickCard(
                theme: theme,
                icon: Icons.emoji_events_rounded,
                color: const Color(0xFFD4AF37),
                title: 'الشارات (${s.unlockedBadgeIds.length})',
                subtitle: 'إنجازاتك المالية',
                onTap: () => Navigator.of(context).push(
                  SmoothPageRoute(
                    builder: (_) => BadgesScreen(controller: c),
                  ),
                ),
              ),
            ),
          ],
        ),

        const SizedBox(height: 16),

        // Theme & Colors
        Text('المظهر والألوان', style: theme.textTheme.titleLarge),
        const SizedBox(height: 8),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  secondary: Icon(
                    s.isDarkMode
                        ? Icons.dark_mode_rounded
                        : Icons.light_mode_rounded,
                    color: theme.colorScheme.secondary,
                  ),
                  title: const Text('الوضع الليلي'),
                  value: s.isDarkMode,
                  onChanged: (_) => c.toggleDarkMode(),
                ),
                const Divider(),
                const SizedBox(height: 6),
                Text('لون التطبيق المفضل', style: theme.textTheme.titleMedium),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: AppThemes.palettes.map((pal) {
                    final selected = pal.key == s.themePaletteKey;
                    return ChoiceChip(
                      avatar: CircleAvatar(
                        backgroundColor: pal.primary,
                        radius: 10,
                      ),
                      label: Text(pal.nameAr),
                      selected: selected,
                      onSelected: (_) => c.updateSettings(
                        s.copyWith(themePaletteKey: pal.key),
                      ),
                    );
                  }).toList(),
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 16),

        // Privacy & Security
        Text('الأمان والخصوصية', style: theme.textTheme.titleLarge),
        const SizedBox(height: 8),
        Card(
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.pin_rounded),
                title: const Text('قفل برمز سري (PIN)'),
                subtitle: Text(
                  (s.pinCode ?? '').isNotEmpty
                      ? 'مفعّل — اضغط للتعديل أو الإلغاء'
                      : 'غير مفعّل',
                ),
                trailing: const Icon(Icons.chevron_left_rounded),
                onTap: _configurePin,
              ),
              const Divider(height: 1),
              SwitchListTile(
                secondary: const Icon(Icons.fingerprint_rounded),
                title: const Text('القفل بالبصمة'),
                subtitle: const Text('حماية بياناتك ببصمة الإصبع'),
                value: s.biometricEnabled,
                onChanged: _toggleBiometric,
              ),
            ],
          ),
        ),

        const SizedBox(height: 16),

        // Notifications, Reports, Backup & Data
        Text('التقارير والنسخ الاحتياطي', style: theme.textTheme.titleLarge),
        const SizedBox(height: 8),
        Card(
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.notifications_active_rounded),
                title: const Text('الإشعارات والأصوات'),
                subtitle: const Text('تذكير الراتب، التنبيهات، الموسيقى'),
                trailing: const Icon(Icons.chevron_left_rounded),
                onTap: () => Navigator.of(context).push(
                  SmoothPageRoute(
                    builder: (_) => NotificationsSettingsScreen(controller: c),
                  ),
                ),
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(
                  Icons.picture_as_pdf_rounded,
                  color: Color(0xFFEF4444),
                ),
                title: const Text('تصدير تقرير شهري PDF'),
                subtitle: const Text('تقرير عربي أنيق للخطة والمصاريف والأهداف'),
                trailing: _busy
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.share_rounded),
                onTap: _busy ? null : _exportPdf,
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.cloud_download_rounded),
                title: const Text('حفظ نسخة احتياطية (JSON)'),
                subtitle: const Text('تصدير كل بياناتك بأمان لمشاركتها أو حفظها'),
                trailing: const Icon(Icons.chevron_left_rounded),
                onTap: _busy ? null : _exportBackup,
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.restore_rounded),
                title: const Text('استعادة نسخة احتياطية'),
                subtitle: const Text('استيراد ملف JSON محفوظ سابقاً'),
                trailing: const Icon(Icons.chevron_left_rounded),
                onTap: _importBackup,
              ),
            ],
          ),
        ),

        const SizedBox(height: 16),

        // Profile, About & Reset
        Card(
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.tune_rounded),
                title: const Text('تعديل بيانات الراتب والأسئلة'),
                trailing: const Icon(Icons.chevron_left_rounded),
                onTap: () => Navigator.of(context).push(
                  SmoothPageRoute(
                    builder: (_) =>
                        SetupWizardScreen(controller: c, isEditing: true),
                  ),
                ),
              ),
              const Divider(height: 1),
              ListTile(
                leading: Icon(
                  Icons.info_rounded,
                  color: theme.colorScheme.primary,
                ),
                title: const Text('عن التطبيق'),
                subtitle: const Text('تنفيذ وتطوير: أمجد حبيب'),
                trailing: const Icon(Icons.chevron_left_rounded),
                onTap: () => Navigator.of(context).push(
                  SmoothPageRoute(builder: (_) => const AboutScreen()),
                ),
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(
                  Icons.delete_forever_rounded,
                  color: Color(0xFFEF4444),
                ),
                title: const Text(
                  'إعادة ضبط جميع البيانات',
                  style: TextStyle(color: Color(0xFFEF4444)),
                ),
                onTap: _confirmReset,
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _quickCard({
    required ThemeData theme,
    required IconData icon,
    required Color color,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.16),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(icon, color: color, size: 26),
              ),
              const SizedBox(height: 12),
              Text(title, style: theme.textTheme.titleMedium),
              const SizedBox(height: 2),
              Text(
                subtitle,
                style: theme.textTheme.bodyMedium?.copyWith(fontSize: 12),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
