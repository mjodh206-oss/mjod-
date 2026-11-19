import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

import '../core/constants/categories.dart';
import '../core/constants/currencies.dart';
import '../core/utils/arabic_formatters.dart';
import '../models/budget_plan.dart';
import '../models/debt_item.dart';
import '../models/salary_profile.dart';
import '../models/savings_goal.dart';
import 'plan_engine.dart';

class AiAdviceResult {
  const AiAdviceResult({
    required this.message,
    required this.isFromCloudAi,
    required this.generatedAt,
    this.questionAsked,
  });

  final String message;
  final bool isFromCloudAi;
  final DateTime generatedAt;
  final String? questionAsked;
}

class AiAdvisorService {
  Future<AiAdviceResult> analyze({
    required SalaryProfile profile,
    required BudgetPlan plan,
    required AppCurrency currency,
    required Map<BudgetBucket, double> spentByBucket,
    required double totalSpentCycle,
    required double dailySafeSpend,
    required int daysLeft,
    required List<SavingsGoal> goals,
    required List<DebtItem> debts,
    String? userQuestion,
    bool allowCloud = true,
  }) async {
    final cleanQ = userQuestion?.trim();
    final splits = PlanEngine.exactMoneySplit(plan: plan, currency: currency);

    final systemPrompt =
        'أنت مستشار مالي شخصي لطيف داخل تطبيق BNB لمساعدة الناس البسطاء على ترتيب رواتبهم.\n'
        'قواعد مهمة جداً في إجابتك:\n'
        '1. اكتب بالعربية السهلة البسيطة جداً (كأنك صديق يشرح بلطف ووضوح).\n'
        '2. استخدم مبالغ مالية حقيقية فقط (${currency.symbol})، ولا تذكر نسباً مئوية أبداً إلا إذا سألك المستخدم عنها.\n'
        '3. اجعل الإجابة قصيرة ومرقّمة (4 إلى 5 نقاط واضحة) تبدأ مباشرة بما يفعله المستخدم بماله اليوم.';

    final contextSummary = _buildPromptContext(
      profile: profile,
      currency: currency,
      splits: splits,
      spentByBucket: spentByBucket,
      totalSpentCycle: totalSpentCycle,
      dailySafeSpend: dailySafeSpend,
      daysLeft: daysLeft,
      goals: goals,
      debts: debts,
      question: cleanQ,
    );

    final cloudReply = allowCloud
        ? await _tryFreeCloudAi(
            systemPrompt: systemPrompt,
            userPrompt: contextSummary,
          )
        : null;

    if (cloudReply != null && cloudReply.trim().isNotEmpty) {
      return AiAdviceResult(
        message: cloudReply.trim(),
        isFromCloudAi: true,
        generatedAt: DateTime.now(),
        questionAsked: (cleanQ != null && cleanQ.isNotEmpty) ? cleanQ : null,
      );
    }

    final localReply = _buildSmartLocalAnalysis(
      profile: profile,
      plan: plan,
      currency: currency,
      splits: splits,
      spentByBucket: spentByBucket,
      totalSpentCycle: totalSpentCycle,
      dailySafeSpend: dailySafeSpend,
      daysLeft: daysLeft,
      goals: goals,
      debts: debts,
      question: cleanQ,
    );

    return AiAdviceResult(
      message: localReply,
      isFromCloudAi: false,
      generatedAt: DateTime.now(),
      questionAsked: (cleanQ != null && cleanQ.isNotEmpty) ? cleanQ : null,
    );
  }

  String _buildPromptContext({
    required SalaryProfile profile,
    required AppCurrency currency,
    required Map<BudgetBucket, double> splits,
    required Map<BudgetBucket, double> spentByBucket,
    required double totalSpentCycle,
    required double dailySafeSpend,
    required int daysLeft,
    required List<SavingsGoal> goals,
    required List<DebtItem> debts,
    required String? question,
  }) {
    String fmt(double v) => ArabicFormatters.formatMoney(v, currency);

    final periodAr = profile.payFrequency.periodAr;
    final buf = StringBuffer()
      ..writeln('بيانات المستخدم الحالية:')
      ..writeln('- نظام الراتب: ${profile.payFrequency.labelAr}')
      ..writeln('- الدخل كل $periodAr: ${fmt(profile.totalMonthlyIncome)}')
      ..writeln('- المتاح يومياً الآن حتى القبضة الجاية: ${fmt(dailySafeSpend)} (باقي $daysLeft يوم)')
      ..writeln('- حصة الإيجار في $periodAr: ${profile.isRenting ? fmt(profile.rentAmount) : "لا يوجد إيجار"}')
      ..writeln('- توزيع الخطة بالمبالغ:')
      ..writeln('  * البيت والأساسيات: ${fmt(splits[BudgetBucket.necessities] ?? 0)} (مصروف منها ${fmt(spentByBucket[BudgetBucket.necessities] ?? 0)})')
      ..writeln('  * مصروفك الشخصي: ${fmt(splits[BudgetBucket.wants] ?? 0)} (مصروف منها ${fmt(spentByBucket[BudgetBucket.wants] ?? 0)})')
      ..writeln('  * التوفير والادخار: ${fmt(splits[BudgetBucket.savings] ?? 0)}')
      ..writeln('  * صندوق الطوارئ: ${fmt(splits[BudgetBucket.emergency] ?? 0)}');

    final debtAmt = splits[BudgetBucket.debts] ?? 0;
    if (debtAmt > 0 || debts.isNotEmpty) {
      final totalDebt = debts.fold<double>(0.0, (s, d) => s + d.remainingBalance);
      buf.writeln('  * الديون والأقساط: ${fmt(debtAmt)} في $periodAr (إجمالي متبقي ${fmt(totalDebt)})');
    }
    buf.writeln('- إجمالي ما صُرف هذا $periodAr حتى الآن: ${fmt(totalSpentCycle)}');
    if (goals.isNotEmpty) {
      final g = goals.first;
      buf.writeln('- أقرب هدف ادخار: «${g.title}» (جمع ${fmt(g.savedAmount)} من ${fmt(g.targetAmount)})');
    }
    buf.writeln();
    if (question != null && question.isNotEmpty) {
      buf.writeln('سؤال المستخدم: $question');
    } else {
      buf.writeln('المطلوب: أعطه تحليلاً سريعاً ومشجعاً لوضعه المالي الآن مع خطوات عملية بالمبالغ.');
    }
    return buf.toString();
  }

  Future<String?> _tryFreeCloudAi({
    required String systemPrompt,
    required String userPrompt,
  }) async {
    final postResult = await _postPollinations(systemPrompt, userPrompt);
    if (postResult != null && postResult.isNotEmpty) {
      return postResult;
    }
    return _getPollinations('$systemPrompt\n\n$userPrompt');
  }

  Future<String?> _postPollinations(
    String systemPrompt,
    String userPrompt,
  ) async {
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 7);
    try {
      final req = await client
          .postUrl(Uri.parse('https://text.pollinations.ai/openai'))
          .timeout(const Duration(seconds: 10));
      req.headers.contentType = ContentType.json;
      req.headers.set(HttpHeaders.acceptHeader, 'application/json');
      req.write(
        jsonEncode({
          'model': 'openai-fast',
          'messages': [
            {'role': 'system', 'content': systemPrompt},
            {'role': 'user', 'content': userPrompt},
          ],
          'temperature': 0.5,
        }),
      );
      final res = await req.close().timeout(const Duration(seconds: 14));
      if (res.statusCode < 200 || res.statusCode >= 300) return null;
      final body = await res.transform(utf8.decoder).join().timeout(
            const Duration(seconds: 8),
          );
      return _extractOpenAiText(body);
    } catch (_) {
      return null;
    } finally {
      client.close(force: true);
    }
  }

  Future<String?> _getPollinations(String prompt) async {
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 7);
    try {
      final uri = Uri.https(
        'text.pollinations.ai',
        '/${Uri.encodeComponent(prompt)}',
        {'model': 'openai-fast'},
      );
      final req = await client.getUrl(uri).timeout(const Duration(seconds: 10));
      final res = await req.close().timeout(const Duration(seconds: 14));
      if (res.statusCode < 200 || res.statusCode >= 300) return null;
      final body = await res.transform(utf8.decoder).join().timeout(
            const Duration(seconds: 8),
          );
      final trimmed = body.trim();
      if (trimmed.isEmpty ||
          trimmed.startsWith('<!DOCTYPE') ||
          trimmed.startsWith('<html') ||
          trimmed.contains('deprecation_notice')) {
        return null;
      }
      return trimmed;
    } catch (_) {
      return null;
    } finally {
      client.close(force: true);
    }
  }

  String? _extractOpenAiText(String body) {
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map<String, dynamic>) {
        if (decoded.containsKey('deprecation_notice')) return null;
        final choices = decoded['choices'];
        if (choices is List && choices.isNotEmpty) {
          final first = choices.first;
          if (first is Map && first['message'] is Map) {
            final content = first['message']['content']?.toString().trim();
            if (content != null && content.isNotEmpty) {
              return content;
            }
          }
        }
      }
    } catch (_) {
      // ignore malformed JSON
    }
    return null;
  }

  String _buildSmartLocalAnalysis({
    required SalaryProfile profile,
    required BudgetPlan plan,
    required AppCurrency currency,
    required Map<BudgetBucket, double> splits,
    required Map<BudgetBucket, double> spentByBucket,
    required double totalSpentCycle,
    required double dailySafeSpend,
    required int daysLeft,
    required List<SavingsGoal> goals,
    required List<DebtItem> debts,
    required String? question,
  }) {
    String fmt(double v) => ArabicFormatters.formatMoney(v, currency);

    final sav = splits[BudgetBucket.savings] ?? 0;
    final emg = splits[BudgetBucket.emergency] ?? 0;
    final nec = splits[BudgetBucket.necessities] ?? 0;
    final wants = splits[BudgetBucket.wants] ?? 0;
    final wantsSpent = spentByBucket[BudgetBucket.wants] ?? 0;
    final wantsLeft = math.max(0.0, wants - wantsSpent);
    final necSpent = spentByBucket[BudgetBucket.necessities] ?? 0;
    final necLeft = math.max(0.0, nec - necSpent);
    final days = math.max(daysLeft, 1);

    final lines = <String>[];

    // If user asked about spending a specific number (e.g. "أشتري شي بـ 500؟")
    if (question != null && question.isNotEmpty) {
      final match = RegExp(r'([0-9]+(?:[.,][0-9]+)?)').firstMatch(
        question.replaceAll(',', ''),
      );
      if (match != null) {
        final askedAmt = double.tryParse(match.group(1)!) ?? 0;
        if (askedAmt > 0) {
          final afterSpendWants = wantsLeft - askedAmt;
          final totalSpendableLeft = dailySafeSpend * days;
          final newDaily =
              math.max(0.0, totalSpendableLeft - askedAmt) / days;

          if (afterSpendWants >= 0) {
            lines.add(
              '✅ تقدر تصرف ${fmt(askedAmt)} من مصروفك الشخصي بأمان بدون ما تلمس التوفير أو مصاريف البيت.',
            );
            lines.add(
              '• بيبقى بمصروفك الشخصي ${fmt(afterSpendWants)}، ومصروفك اليومي المسموح بيصير ${fmt(newDaily)} لمدة $days يوم.',
            );
          } else if (totalSpendableLeft >= askedAmt) {
            lines.add(
              '⚠️ مبلغ ${fmt(askedAmt)} أكبر من المتبقي في مصروفك الشخصي (${fmt(wantsLeft)}).',
            );
            lines.add(
              '• لو صرفته الآن، رح يقل حدك اليومي من ${fmt(dailySafeSpend)} إلى ${fmt(newDaily)} حتى الراتب القادم.',
            );
            lines.add(
              '💡 اقتراح: أجّل الشراء لبعد الراتب أو خُذ نصه فقط هالشهر.',
            );
          } else {
            lines.add(
              '🛑 الأفضل تأجّل شراء ${fmt(askedAmt)} للراتب الجاي؛ لأنه أكبر من كل المتبقي للمعيشة هذا الشهر (${fmt(totalSpendableLeft)}).',
            );
          }
          return lines.join('\n');
        }
      }
    }

    final periodWord = profile.isWeekly ? 'الأسبوع' : 'الشهر';
    lines.add('خلاصة وضعك المالي الآن بالأرقام:');
    lines.add(
      '1. أول ما تقبض: خبّي ${fmt(sav + emg)} للتوفير والطوارئ (تقدر توفّر ${fmt((sav + emg) * profile.annualCycles)} خلال سنة كاملة).',
    );
    lines.add(
      profile.isWeekly
          ? '2. مصروفك اليومي المريح الآن: ${fmt(dailySafeSpend)} باليوم لمدة $days يوم حتى القبضة الأسبوعية الجاية.'
          : '2. مصروفك اليومي المريح الآن: ${fmt(dailySafeSpend)} باليوم (${fmt(dailySafeSpend * 7)} بالأسبوع) لمدة $days يوم لآخر $periodWord.',
    );

    if (profile.isRenting &&
        profile.rentAmount > profile.totalMonthlyIncome * 0.40) {
      lines.add(
        '3. ملاحظة مهمة: الإيجار (${fmt(profile.rentAmount)}) بياخد جزء كبير من راتبك، فحافظ على مصروفك الشخصي بحدود ${fmt(wants)} وما تتجاوزه.',
      );
    } else {
      lines.add(
        '3. باقي لك حالياً ${fmt(necLeft)} للبيت والأكل و ${fmt(wantsLeft)} لمصروفك الشخصي.',
      );
    }

    if (goals.isNotEmpty) {
      final g = goals.first;
      final needed = math.max(0.0, g.targetAmount - g.savedAmount);
      final cyclesNeeded = sav > 0 ? (needed / sav).ceil() : 0;
      final unit = profile.isWeekly ? 'أسابيع' : 'أشهر';
      lines.add(
        sav > 0
            ? '4. لهدف «${g.title}»: باقي له ${fmt(needed)}، وبوتيرة التوفير الحالية رح تحققه خلال نحو $cyclesNeeded $unit.'
            : '4. لهدف «${g.title}»: باقي له ${fmt(needed)}؛ ارفع التوفير ولو بمبلغ بسيط حتى تقترب منه.',
      );
    } else if (debts.isNotEmpty) {
      final totalDebt =
          debts.fold<double>(0.0, (s, d) => s + d.remainingBalance);
      lines.add(
        '4. ديونك المتبقية ${fmt(totalDebt)}؛ ركّز الدفع الإضافي على أصغر دين حتى يختفي أولاً.',
      );
    } else {
      final extraTip = sav * 0.1;
      lines.add(
        '4. اقتراح بسيط: لو نقصت من المصروف الشخصي ${fmt(math.max(extraTip, 1))} وحوّلتها للتوفير، بيزيد رصيدك أسرع بدون ضغط.',
      );
    }

    return lines.join('\n');
  }
}
