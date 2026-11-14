import 'package:flutter/material.dart';

import '../../widgets/rattib_logo.dart';

class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('عن التطبيق')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Center(child: RattibLogo(size: 112)),
          const SizedBox(height: 16),
          Text(
            'رَتِّبْ',
            textAlign: TextAlign.center,
            style: theme.textTheme.headlineLarge?.copyWith(
              color: theme.colorScheme.primary,
              fontSize: 32,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'فلوسك بتستاهل خطة',
            textAlign: TextAlign.center,
            style: theme.textTheme.titleMedium?.copyWith(
              color: theme.colorScheme.secondary,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            'الإصدار 1.0.0',
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium,
          ),
          const SizedBox(height: 22),

          // Prominent Developer Attribution Card
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(22),
              gradient: const LinearGradient(
                colors: [Color(0xFF063C2B), Color(0xFF0C684A)],
              ),
            ),
            child: const Column(
              children: [
                Icon(
                  Icons.workspace_premium_rounded,
                  color: Color(0xFFF5C443),
                  size: 34,
                ),
                SizedBox(height: 8),
                Text(
                  'تنفيذ وتطوير: أمجد حبيب',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontFamily: 'Cairo',
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                    color: Colors.white,
                  ),
                ),
                SizedBox(height: 6),
                Text(
                  'صُمّم بعناية لمساعدتك على تنظيم راتبك، سداد التزاماتك، وبناء مدخراتك بثقة وبساطة.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontFamily: 'Cairo',
                    fontSize: 13.5,
                    color: Color(0xFFD7F3E6),
                    height: 1.5,
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 18),
          Card(
            child: Column(
              children: [
                const ListTile(
                  leading: Icon(Icons.wifi_off_rounded, color: Color(0xFF10B981)),
                  title: Text('يعمل بدون إنترنت بالكامل'),
                  subtitle: Text(
                    'جميع بياناتك وخططك محفوظة محلياً على هاتفك فقط لخصوصية وأمان كاملين.',
                  ),
                ),
                const Divider(height: 1),
                const ListTile(
                  leading: Icon(Icons.rule_rounded, color: Color(0xFFD4AF37)),
                  title: Text('مبني على قواعد مالية مجرّبة'),
                  subtitle: Text(
                    'قاعدة 50/30/20، صندوق الطوارئ، واستراتيجيتا كرة الثلج والانهيار الجليدي للديون.',
                  ),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: Icon(
                    Icons.widgets_rounded,
                    color: theme.colorScheme.primary,
                  ),
                  title: const Text('ويدجت الشاشة الرئيسية'),
                  subtitle: const Text(
                    'اضغط مطولاً على شاشة هاتفك الرئيسية ← الأدوات المصغرة (Widgets) ← اختر «رَتِّبْ» لمتابعة المتبقي من راتبك مباشرة.',
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
