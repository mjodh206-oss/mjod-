import 'dart:math' as math;

import '../core/constants/categories.dart';
import '../core/constants/currencies.dart';
import '../core/utils/arabic_formatters.dart';
import '../models/budget_plan.dart';
import '../models/debt_item.dart';
import '../models/salary_profile.dart';

class SmartAdviceItem {
  const SmartAdviceItem({
    required this.id,
    required this.titleAr,
    required this.bodyAr,
    required this.actionStepsAr,
    required this.severity,
    this.bucket,
  });

  final String id;
  final String titleAr;
  final String bodyAr;
  final List<String> actionStepsAr;
  final AdviceSeverity severity;
  final BudgetBucket? bucket;
}

enum AdviceSeverity {
  critical,
  warning,
  positive,
  info,
}

class DebtPayoffStep {
  const DebtPayoffStep({
    required this.debt,
    required this.payoffMonth,
    required this.orderIndex,
    required this.interestPaid,
  });

  final DebtItem debt;
  final int payoffMonth;
  final int orderIndex;
  final double interestPaid;
}

class DebtSimulationResult {
  const DebtSimulationResult({
    required this.strategy,
    required this.totalMonths,
    required this.totalInterestPaid,
    required this.steps,
  });

  final DebtStrategy strategy;
  final int totalMonths;
  final double totalInterestPaid;
  final List<DebtPayoffStep> steps;
}

enum PlanPreset {
  balanced(
    titleAr: 'خطة متوازنة',
    shortAr: 'متوازنة',
    descriptionAr: 'تغطي البيت والأكل، تترك لك مصروفاً مريحاً، وتوفّر جزءاً جيداً كل شهر.',
  ),
  strongSaving(
    titleAr: 'توفير أكبر',
    shortAr: 'توفير أكبر',
    descriptionAr: 'تقلل المصروف الشخصي وترفع التوفير لأعلى مبلغ لتصل لأهدافك بسرعة.',
  ),
  essentialsFirst(
    titleAr: 'للبيت أكثر',
    shortAr: 'للبيت أكثر',
    descriptionAr: 'مناسبة للرواتب المضغوطة أو الإيجار العالي: تغطي البيت والفواتير أولاً بأمان.',
  ),
  debtPayoff(
    titleAr: 'تسكير الديون',
    shortAr: 'تسكير الديون',
    descriptionAr: 'توجّه أكبر مبلغ لسداد الديون والأقساط حتى يصفى راتبك بالكامل.',
  );

  const PlanPreset({
    required this.titleAr,
    required this.shortAr,
    required this.descriptionAr,
  });

  final String titleAr;
  final String shortAr;
  final String descriptionAr;
}

class SalaryActionStep {
  const SalaryActionStep({
    required this.stepNumber,
    required this.titleAr,
    required this.instructionAr,
    required this.bucket,
    required this.monthlyAmount,
    required this.dailyAllowance,
    required this.spentAmount,
    required this.percentage,
  });

  final int stepNumber;
  final String titleAr;
  final String instructionAr;
  final BudgetBucket bucket;
  final double monthlyAmount;
  final double? dailyAllowance;
  final double spentAmount;
  final double percentage;
}

class PlanEngine {
  PlanEngine._();

  static BudgetPlan generatePresetPlan({
    required SalaryProfile profile,
    required PlanPreset preset,
  }) {
    final income = math.max(profile.totalMonthlyIncome, 1.0);
    final hasDebt = profile.totalDebtAndInstallments > 0;
    final fixedFloor = ((profile.totalFixedNonDebt / income) * 100.0).clamp(0.0, 70.0);

    late List<double> raw;
    switch (preset) {
      case PlanPreset.balanced:
        final nec = math.max(50.0, fixedFloor + 8.0).clamp(45.0, 62.0);
        final debts = hasDebt
            ? ((profile.totalDebtAndInstallments / income) * 100.0).clamp(5.0, 22.0)
            : 0.0;
        final rem = (100.0 - nec - debts).clamp(10.0, 55.0);
        final wants = (rem * 0.56).clamp(10.0, 32.0);
        final wealth = (rem - wants).clamp(5.0, 35.0);
        raw = [nec, wants, wealth * 0.65, debts, wealth * 0.35];
      case PlanPreset.strongSaving:
        final nec = math.max(45.0, fixedFloor + 6.0).clamp(42.0, 60.0);
        final debts = hasDebt
            ? ((profile.totalDebtAndInstallments / income) * 100.0).clamp(5.0, 20.0)
            : 0.0;
        final rem = (100.0 - nec - debts).clamp(10.0, 58.0);
        final wants = (rem * 0.32).clamp(8.0, 20.0);
        final wealth = (rem - wants).clamp(10.0, 45.0);
        raw = [nec, wants, wealth * 0.72, debts, wealth * 0.28];
      case PlanPreset.essentialsFirst:
        final nec = math.max(62.0, fixedFloor + 12.0).clamp(58.0, 75.0);
        final debts = hasDebt
            ? ((profile.totalDebtAndInstallments / income) * 100.0).clamp(4.0, 18.0)
            : 0.0;
        final rem = (100.0 - nec - debts).clamp(8.0, 42.0);
        final wants = (rem * 0.55).clamp(5.0, 22.0);
        final wealth = (rem - wants).clamp(4.0, 20.0);
        raw = [nec, wants, wealth * 0.45, debts, wealth * 0.55];
      case PlanPreset.debtPayoff:
        final nec = math.max(48.0, fixedFloor + 6.0).clamp(42.0, 60.0);
        final rawDebt = (profile.totalDebtAndInstallments / income) * 100.0;
        final debts = math.max(hasDebt ? rawDebt + 10.0 : 20.0, 18.0).clamp(15.0, 36.0);
        final rem = (100.0 - nec - debts).clamp(8.0, 42.0);
        final wants = (rem * 0.45).clamp(6.0, 20.0);
        final wealth = (rem - wants).clamp(5.0, 24.0);
        raw = [nec, wants, wealth * 0.55, debts, wealth * 0.45];
    }

    final normalized = _normalizeTo100(raw);
    final draft = BudgetPlan(
      totalIncome: profile.totalMonthlyIncome,
      necessitiesPct: normalized[0],
      wantsPct: normalized[1],
      savingsPct: normalized[2],
      debtsPct: normalized[3],
      emergencyPct: normalized[4],
      healthScore: 0,
      healthLabelAr: '',
      isCustomized: true,
    );
    final score = calculateHealthScore(profile, draft);
    return draft.copyWith(
      healthScore: score,
      healthLabelAr: healthLabelForScore(score),
    );
  }

  static Map<BudgetBucket, double> exactMoneySplit({
    required BudgetPlan plan,
    required AppCurrency currency,
  }) {
    final total = math.max(plan.totalIncome, 0.0);
    final result = <BudgetBucket, double>{};
    if (total <= 0) {
      for (final b in BudgetBucket.values) {
        result[b] = 0.0;
      }
      return result;
    }

    final factor = currency.allowDecimals ? 100.0 : 1.0;
    final totalUnits = (total * factor).round();

    BudgetBucket largestBucket = BudgetBucket.necessities;
    double largestPct = -1;
    int assignedUnits = 0;

    for (final b in BudgetBucket.values) {
      final pct = plan.percentageFor(b);
      if (pct <= 0.01) {
        result[b] = 0.0;
        continue;
      }
      if (pct > largestPct) {
        largestPct = pct;
        largestBucket = b;
      }
      final units = ((total * (pct / 100.0)) * factor).round();
      result[b] = units / factor;
      assignedUnits += units;
    }

    final diffUnits = totalUnits - assignedUnits;
    if (diffUnits != 0) {
      final adjusted = ((result[largestBucket]! * factor).round() + diffUnits)
          .clamp(0, totalUnits);
      result[largestBucket] = adjusted / factor;
    }
    return result;
  }

  static BudgetPlan rebalanceFromAmount({
    required SalaryProfile profile,
    required BudgetPlan current,
    required BudgetBucket changedBucket,
    required double newAmount,
  }) {
    final income = math.max(profile.totalMonthlyIncome, 1.0);
    final pct = ((newAmount / income) * 100.0).clamp(0.0, 85.0);
    return rebalanceFromSlider(
      profile: profile,
      current: current,
      changedBucket: changedBucket,
      newPercentage: pct,
    );
  }

  static double dailySafeSpend({
    required SalaryProfile profile,
    required BudgetPlan plan,
    required Map<BudgetBucket, double> spentByBucket,
    required int daysLeft,
    AppCurrency? currency,
  }) {
    final splits = currency != null
        ? exactMoneySplit(plan: plan, currency: currency)
        : null;
    final necBudget = splits?[BudgetBucket.necessities] ??
        plan.amountFor(BudgetBucket.necessities);
    final wantsBudget =
        splits?[BudgetBucket.wants] ?? plan.amountFor(BudgetBucket.wants);
    final rent = profile.isRenting ? profile.rentAmount : 0.0;
    final necSpent = spentByBucket[BudgetBucket.necessities] ?? 0.0;
    final wantsSpent = spentByBucket[BudgetBucket.wants] ?? 0.0;

    final necLivingLeft =
        math.max(0.0, (necBudget - rent) - math.max(0.0, necSpent - rent));
    final wantsLeft = math.max(0.0, wantsBudget - wantsSpent);
    final spendableLeft = necLivingLeft + wantsLeft;

    final days = math.max(daysLeft, 1);
    return spendableLeft / days;
  }

  static List<SalaryActionStep> buildSalaryActionPlan({
    required SalaryProfile profile,
    required BudgetPlan plan,
    required Map<BudgetBucket, double> spentByBucket,
    required AppCurrency currency,
    required int daysLeft,
  }) {
    final splits = exactMoneySplit(plan: plan, currency: currency);
    final steps = <SalaryActionStep>[];
    int num = 1;

    final savAmt = splits[BudgetBucket.savings] ?? 0.0;
    if (savAmt > 0) {
      steps.add(
        SalaryActionStep(
          stepNumber: num++,
          titleAr: 'التوفير والادخار (حطه على جنب أولاً)',
          instructionAr:
              'خبّي ${ArabicFormatters.formatMoney(savAmt, currency)} فور نزول الراتب — بالسنة بتصير ${ArabicFormatters.formatMoney(savAmt * 12, currency)}.',
          bucket: BudgetBucket.savings,
          monthlyAmount: savAmt,
          dailyAllowance: null,
          spentAmount: spentByBucket[BudgetBucket.savings] ?? 0.0,
          percentage: plan.savingsPct,
        ),
      );
    }

    final emgAmt = splits[BudgetBucket.emergency] ?? 0.0;
    if (emgAmt > 0) {
      steps.add(
        SalaryActionStep(
          stepNumber: num++,
          titleAr: 'صندوق الطوارئ (احتياط الأمان)',
          instructionAr:
              'اترك ${ArabicFormatters.formatMoney(emgAmt, currency)} لظرف مفاجئ لا سمح الله.',
          bucket: BudgetBucket.emergency,
          monthlyAmount: emgAmt,
          dailyAllowance: null,
          spentAmount: spentByBucket[BudgetBucket.emergency] ?? 0.0,
          percentage: plan.emergencyPct,
        ),
      );
    }

    final necAmt = splits[BudgetBucket.necessities] ?? 0.0;
    final rent = profile.isRenting ? profile.rentAmount : 0.0;
    final necLiving = math.max(0.0, necAmt - rent);
    final necDaily = necLiving / 30.0;
    steps.add(
      SalaryActionStep(
        stepNumber: num++,
        titleAr: 'البيت والأكل والفواتير',
        instructionAr: profile.isRenting && rent > 0
            ? 'ادفع الإيجار (${ArabicFormatters.formatMoney(rent, currency)}) والباقي ${ArabicFormatters.formatMoney(necLiving, currency)} للأكل والفواتير.'
            : 'للأكل والفواتير والمواصلات طوال الشهر (بحدود ${ArabicFormatters.formatMoney(necDaily, currency)} باليوم).',
        bucket: BudgetBucket.necessities,
        monthlyAmount: necAmt,
        dailyAllowance: necDaily,
        spentAmount: spentByBucket[BudgetBucket.necessities] ?? 0.0,
        percentage: plan.necessitiesPct,
      ),
    );

    final wantsAmt = splits[BudgetBucket.wants] ?? 0.0;
    final wantsDaily = wantsAmt / 30.0;
    if (wantsAmt > 0) {
      steps.add(
        SalaryActionStep(
          stepNumber: num++,
          titleAr: 'مصروفك الشخصي والطلعات',
          instructionAr:
              'إلك ${ArabicFormatters.formatMoney(wantsDaily, currency)} باليوم (أو ${ArabicFormatters.formatMoney(wantsAmt / 4.0, currency)} بالأسبوع) تصرفها على راحتك.',
          bucket: BudgetBucket.wants,
          monthlyAmount: wantsAmt,
          dailyAllowance: wantsDaily,
          spentAmount: spentByBucket[BudgetBucket.wants] ?? 0.0,
          percentage: plan.wantsPct,
        ),
      );
    }

    final debtAmt = splits[BudgetBucket.debts] ?? 0.0;
    if (debtAmt > 0) {
      steps.add(
        SalaryActionStep(
          stepNumber: num++,
          titleAr: 'سداد الديون والأقساط',
          instructionAr:
              'سدد ${ArabicFormatters.formatMoney(debtAmt, currency)} هذا الشهر لتخفف التزاماتك أسرع.',
          bucket: BudgetBucket.debts,
          monthlyAmount: debtAmt,
          dailyAllowance: null,
          spentAmount: spentByBucket[BudgetBucket.debts] ?? 0.0,
          percentage: plan.debtsPct,
        ),
      );
    }

    return steps;
  }

  static BudgetPlan generatePlan(SalaryProfile profile) {
    final income = math.max(profile.totalMonthlyIncome, 1.0);

    // Fixed non-debt commitments + household food/living allowance
    final fixedRatio = (profile.totalFixedNonDebt / income) * 100.0;
    final familyAllowance = (12.0 + profile.dependents * 2.5).clamp(10.0, 26.0);
    double necessities = math.max(40.0, fixedRatio + familyAllowance);
    necessities = necessities.clamp(35.0, 70.0);

    // Debt & installments commitment
    final rawDebtRatio = (profile.totalDebtAndInstallments / income) * 100.0;
    double debts = rawDebtRatio > 0 ? rawDebtRatio.clamp(3.0, 35.0) : 0.0;

    // Goal-oriented boost for debt payoff
    if (profile.primaryGoal == PrimaryFinancialGoal.debtPayoff && debts > 0) {
      debts = math.min(38.0, debts + 6.0);
    } else if (profile.primaryGoal == PrimaryFinancialGoal.debtPayoff &&
        debts == 0) {
      debts = 10.0;
    }

    final remainingForWantsAndWealth =
        (100.0 - necessities - debts).clamp(10.0, 65.0);

    // Base split between wants and wealth (savings + emergency)
    double wealthShare;
    if (remainingForWantsAndWealth >= 40) {
      wealthShare = math.max(20.0, remainingForWantsAndWealth * 0.45);
    } else if (remainingForWantsAndWealth >= 22) {
      wealthShare = math.max(12.0, remainingForWantsAndWealth * 0.48);
    } else {
      wealthShare = remainingForWantsAndWealth * 0.50;
    }

    double wants = (remainingForWantsAndWealth - wealthShare).clamp(5.0, 38.0);

    // Split wealthShare into Savings and Emergency Fund
    final needsStrongEmergency = !profile.hasCurrentSavings ||
        profile.currentSavings < (income * 1.5) ||
        profile.primaryGoal == PrimaryFinancialGoal.emergencyFund;

    double emergency;
    double savings;
    if (needsStrongEmergency) {
      emergency = (wealthShare * 0.55).clamp(4.0, 30.0);
      savings = (wealthShare - emergency).clamp(3.0, 35.0);
    } else {
      emergency = (wealthShare * 0.30).clamp(3.0, 20.0);
      savings = (wealthShare - emergency).clamp(5.0, 40.0);
    }

    if (profile.primaryGoal == PrimaryFinancialGoal.saving ||
        profile.primaryGoal == PrimaryFinancialGoal.purchase) {
      final shift = math.min(wants * 0.18, 4.0);
      wants -= shift;
      savings += shift;
    }

    final normalized = _normalizeTo100([
      necessities,
      wants,
      savings,
      debts,
      emergency,
    ]);

    final draft = BudgetPlan(
      totalIncome: profile.totalMonthlyIncome,
      necessitiesPct: normalized[0],
      wantsPct: normalized[1],
      savingsPct: normalized[2],
      debtsPct: normalized[3],
      emergencyPct: normalized[4],
      healthScore: 0,
      healthLabelAr: '',
      isCustomized: false,
    );

    final score = calculateHealthScore(profile, draft);
    return draft.copyWith(
      healthScore: score,
      healthLabelAr: healthLabelForScore(score),
    );
  }

  static BudgetPlan rebalanceFromSlider({
    required SalaryProfile profile,
    required BudgetPlan current,
    required BudgetBucket changedBucket,
    required double newPercentage,
  }) {
    final target = newPercentage.clamp(0.0, 85.0);
    final values = <BudgetBucket, double>{
      BudgetBucket.necessities: current.necessitiesPct,
      BudgetBucket.wants: current.wantsPct,
      BudgetBucket.savings: current.savingsPct,
      BudgetBucket.debts: current.debtsPct,
      BudgetBucket.emergency: current.emergencyPct,
    };

    final oldVal = values[changedBucket] ?? 0.0;
    if ((target - oldVal).abs() < 0.01) return current;

    values[changedBucket] = target;
    final remainingTarget = 100.0 - target;

    final others = BudgetBucket.values
        .where((b) => b != changedBucket)
        .toList();
    final othersSum =
        others.fold<double>(0.0, (sum, b) => sum + (values[b] ?? 0.0));

    if (othersSum <= 0.001) {
      final each = remainingTarget / others.length;
      for (final b in others) {
        values[b] = each;
      }
    } else {
      for (final b in others) {
        values[b] = ((values[b] ?? 0.0) / othersSum) * remainingTarget;
      }
    }

    final normalized = _normalizeTo100([
      values[BudgetBucket.necessities]!,
      values[BudgetBucket.wants]!,
      values[BudgetBucket.savings]!,
      values[BudgetBucket.debts]!,
      values[BudgetBucket.emergency]!,
    ]);

    final updated = current.copyWith(
      totalIncome: profile.totalMonthlyIncome,
      necessitiesPct: normalized[0],
      wantsPct: normalized[1],
      savingsPct: normalized[2],
      debtsPct: normalized[3],
      emergencyPct: normalized[4],
      isCustomized: true,
    );

    final score = calculateHealthScore(profile, updated);
    return updated.copyWith(
      healthScore: score,
      healthLabelAr: healthLabelForScore(score),
    );
  }

  static List<double> _normalizeTo100(List<double> raw) {
    final clamped = raw.map((e) => math.max(0.0, e)).toList();
    final sum = clamped.fold<double>(0.0, (a, b) => a + b);
    if (sum <= 0) return [50, 25, 15, 0, 10];

    final rounded = clamped
        .map((v) => ((v / sum) * 100.0 * 10).round() / 10.0)
        .toList();
    final diff =
        ((100.0 - rounded.fold<double>(0.0, (a, b) => a + b)) * 10).round() /
            10.0;
    // Apply rounding remainder to largest bucket
    var maxIdx = 0;
    for (var i = 1; i < rounded.length; i++) {
      if (rounded[i] > rounded[maxIdx]) maxIdx = i;
    }
    rounded[maxIdx] = ((rounded[maxIdx] + diff) * 10).round() / 10.0;
    return rounded;
  }

  static int calculateHealthScore(
    SalaryProfile profile,
    BudgetPlan plan, {
    double overBudgetCount = 0,
  }) {
    final income = math.max(profile.totalMonthlyIncome, 1.0);
    double score = 55.0;

    // 1. Savings + Emergency commitment (up to +25)
    final wealthPct = plan.savingsPct + plan.emergencyPct;
    if (wealthPct >= 25) {
      score += 25;
    } else if (wealthPct >= 20) {
      score += 21;
    } else if (wealthPct >= 12) {
      score += 14;
    } else if (wealthPct >= 5) {
      score += 6;
    } else {
      score -= 8;
    }

    // 2. Rent pressure check
    final rentRatio = profile.isRenting ? (profile.rentAmount / income) : 0.0;
    if (rentRatio > 0.45) {
      score -= 16;
    } else if (rentRatio > 0.35) {
      score -= 8;
    } else if (rentRatio > 0 && rentRatio <= 0.25) {
      score += 5;
    } else if (!profile.isRenting) {
      score += 8;
    }

    // 3. Debt pressure check
    final debtRatio = plan.debtsPct;
    if (debtRatio == 0) {
      score += 8;
    } else if (debtRatio <= 15) {
      score += 3;
    } else if (debtRatio > 30) {
      score -= 14;
    } else if (debtRatio > 20) {
      score -= 7;
    }

    // 4. Necessities discipline
    if (plan.necessitiesPct <= 55) {
      score += 6;
    } else if (plan.necessitiesPct > 68) {
      score -= 8;
    }

    // 5. Existing savings cushion
    if (profile.hasCurrentSavings && profile.currentSavings >= income * 3) {
      score += 8;
    } else if (profile.hasCurrentSavings && profile.currentSavings > 0) {
      score += 4;
    }

    // 6. Real spending penalty if user exceeded category budgets
    score -= (overBudgetCount * 5).clamp(0, 15);

    return score.round().clamp(15, 100);
  }

  static String healthLabelForScore(int score) {
    if (score >= 85) return 'وضعك ممتاز 🌟';
    if (score >= 70) return 'وضعك جيد جداً 👍';
    if (score >= 50) return 'وضعك مقبول — محتاج تعديل بسيط ⚡';
    return 'محتاج تعديل وخطة إنقاذ ⚠️';
  }

  static List<SmartAdviceItem> generateSmartAdvice({
    required SalaryProfile profile,
    required BudgetPlan plan,
    required Map<BudgetBucket, double> spentByBucket,
    required AppCurrency currency,
  }) {
    final advice = <SmartAdviceItem>[];
    final income = math.max(profile.totalMonthlyIncome, 1.0);

    // Check over-budget buckets first
    for (final meta in AppCategories.buckets) {
      final planned = plan.amountFor(meta.bucket);
      final spent = spentByBucket[meta.bucket] ?? 0.0;
      if (planned > 0 && spent > planned) {
        final over = spent - planned;
        advice.add(
          SmartAdviceItem(
            id: 'over_${meta.bucket.name}',
            titleAr: 'تنبيه: تجاوزت ميزانية ${meta.titleAr}',
            bodyAr:
                'صرفت ${ArabicFormatters.formatMoney(spent, currency)} من أصل ${ArabicFormatters.formatMoney(planned, currency)} (زيادة ${ArabicFormatters.formatMoney(over, currency)}).',
            actionStepsAr: [
              'أوقف المصاريف الاختيارية في هذا البند لنهاية الشهر.',
              'عوّض الزيادة من بند الاحتياجات المرنة عبر منزلقات الخطة.',
            ],
            severity: AdviceSeverity.critical,
            bucket: meta.bucket,
          ),
        );
      } else if (planned > 0 && spent >= planned * 0.85) {
        final pct = ((spent / planned) * 100).round();
        advice.add(
          SmartAdviceItem(
            id: 'near_${meta.bucket.name}',
            titleAr: 'اقتربت من حد ميزانية ${meta.titleAr} ($pct%)',
            bodyAr:
                'المتبقي لك في هذا البند ${ArabicFormatters.formatMoney(planned - spent, currency)} فقط حتى نهاية الدورة.',
            actionStepsAr: [
              'وزّع المتبقي على الأيام القادمة بحذر لتفادي التجاوز.',
            ],
            severity: AdviceSeverity.warning,
            bucket: meta.bucket,
          ),
        );
      }
    }

    // Rent > 40% check (exact prompt scenario!)
    if (profile.isRenting && (profile.rentAmount / income) > 0.40) {
      final rentPct = ((profile.rentAmount / income) * 100).round();
      advice.add(
        SmartAdviceItem(
          id: 'high_rent',
          titleAr: 'إيجارك فوق 40% من راتبك ($rentPct%)، إليك طرق لتخفيفه',
          bodyAr:
              'توصي القواعد المالية بألا يتجاوز السكن 30%-35% من الدخل. ارتفاع الإيجار يضغط على ادخارك.',
          actionStepsAr: [
            'تفاوض على عقد سنوي بسعر ثابت أو دفعات أقل مقابل الالتزام.',
            'فكّر بالسكن المشترك أو حيّ أقرب للعمل لتوفير المواصلات معاً.',
            'خفّض بند الاحتياجات المرنة مؤقتاً بنسبة 10% لتعويض فرق الإيجار.',
          ],
          severity: AdviceSeverity.warning,
          bucket: BudgetBucket.necessities,
        ),
      );
    }

    // Debt rule check
    if (plan.debtsPct >= 20 || profile.totalDebtAndInstallments > 0) {
      advice.add(
        SmartAdviceItem(
          id: 'debt_strategy',
          titleAr: 'خطة تسريع سداد الديون أولاً',
          bodyAr:
              'أنت تدفع ${ArabicFormatters.formatMoney(plan.amountFor(BudgetBucket.debts), currency)} شهرياً للديون والأقساط من أصل راتبك.',
          actionStepsAr: [
            'ابدأ بسداد أصغر دين أولاً حتى تختفي الأقساط الصغيرة بسرعة.',
            'أو ركّز على الدين الأعلى فائدة لتوفير أكبر مبلغ ممكن.',
            'جرّب حاسبة الديون بالأسفل لترى متى تنتهي منها بالكامل.',
          ],
          severity: plan.debtsPct > 30
              ? AdviceSeverity.warning
              : AdviceSeverity.info,
          bucket: BudgetBucket.debts,
        ),
      );
    }

    // Emergency fund check
    if (!profile.hasCurrentSavings ||
        profile.currentSavings < plan.amountFor(BudgetBucket.necessities) * 3) {
      final target3Months = plan.amountFor(BudgetBucket.necessities) * 3;
      advice.add(
        SmartAdviceItem(
          id: 'emergency_fund',
          titleAr: 'ابنِ صندوق طوارئ يحميك من المفاجآت',
          bodyAr:
              'الهدف الآمن لصندوق الطوارئ هو تجميع ما يكفي مصاريف 3 أشهر (${ArabicFormatters.formatMoney(target3Months, currency)}).',
          actionStepsAr: [
            'خبّي ${ArabicFormatters.formatMoney(plan.amountFor(BudgetBucket.emergency), currency)} كل شهر للطوارئ فقط.',
            'احتفظ بمبلغ الطوارئ منفصلاً عن مصروفك اليومي.',
          ],
          severity: AdviceSeverity.info,
          bucket: BudgetBucket.emergency,
        ),
      );
    }

    // Positive reinforcement when savings + emergency >= 20%
    if (plan.savingsPct + plan.emergencyPct >= 20) {
      final totalMonthlySaved =
          plan.amountFor(BudgetBucket.savings) + plan.amountFor(BudgetBucket.emergency);
      advice.add(
        SmartAdviceItem(
          id: 'strong_saving',
          titleAr: 'مبلغ التوفير الشهري عندك ممتاز 🎯',
          bodyAr:
              'أنت تحفظ ${ArabicFormatters.formatMoney(totalMonthlySaved, currency)} كل شهر للتوفير والطوارئ — يعني ${ArabicFormatters.formatMoney(plan.annualSavingsPotential, currency)} خلال سنة كاملة!',
          actionStepsAr: [
            'اربط هذا المبلغ بهدف واضح في قسم الأهداف حتى تظل متحمساً.',
            'استمر بالتزامك لتحصد الشارات الذهبية.',
          ],
          severity: AdviceSeverity.positive,
          bucket: BudgetBucket.savings,
        ),
      );
    }

    // Family dependents advice
    if (profile.dependents >= 2) {
      advice.add(
        SmartAdviceItem(
          id: 'family_plan',
          titleAr: 'تنظيم ميزانية العائلة (${profile.familyMembers} أفراد)',
          bodyAr:
              'إعالة ${profile.dependents} أشخاص تتطلب تخطيطاً مسبقاً للمواد الأساسية والطوارئ الصحية.',
          actionStepsAr: [
            'اشترِ الاحتياجات الشهرية غير القابلة للتلف بالجملة مرة واحدة.',
            'خصّص حصة أسبوعية ثابتة للبقالة تمنع تجاوز نهاية الشهر.',
          ],
          severity: AdviceSeverity.info,
        ),
      );
    }

    return advice;
  }

  static DebtSimulationResult simulateDebtPayoff({
    required List<DebtItem> debts,
    required double extraMonthlyPayment,
    required DebtStrategy strategy,
  }) {
    final active = debts
        .where((d) => d.remainingBalance > 0)
        .map(
          (d) => _SimDebt(
            item: d,
            balance: d.remainingBalance,
            monthlyMin: math.max(d.minimumMonthly, 1.0),
          ),
        )
        .toList();

    if (active.isEmpty) {
      return DebtSimulationResult(
        strategy: strategy,
        totalMonths: 0,
        totalInterestPaid: 0,
        steps: const [],
      );
    }

    active.sort((a, b) {
      if (strategy == DebtStrategy.snowball) {
        return a.balance.compareTo(b.balance);
      }
      final cmp = b.item.interestRatePercent.compareTo(a.item.interestRatePercent);
      return cmp != 0 ? cmp : a.balance.compareTo(b.balance);
    });

    int month = 0;
    double totalInterest = 0;
    double snowballPool = math.max(0.0, extraMonthlyPayment);
    final steps = <DebtPayoffStep>[];
    int order = 1;

    while (active.any((d) => d.balance > 0.01) && month < 360) {
      month++;
      // Apply monthly interest
      for (final d in active) {
        if (d.balance <= 0) continue;
        final monthlyRate = (d.item.interestRatePercent / 100.0) / 12.0;
        final interest = d.balance * monthlyRate;
        d.balance += interest;
        d.interestPaid += interest;
        totalInterest += interest;
      }

      double availableExtra = snowballPool;

      // Pay minimums first
      for (final d in active) {
        if (d.balance <= 0) continue;
        final pay = math.min(d.balance, d.monthlyMin);
        d.balance -= pay;
        if (d.balance <= 0.01) {
          d.balance = 0;
          snowballPool += d.monthlyMin;
          steps.add(
            DebtPayoffStep(
              debt: d.item,
              payoffMonth: month,
              orderIndex: order++,
              interestPaid: d.interestPaid,
            ),
          );
        }
      }

      // Direct extra budget to priority target debt
      for (final d in active) {
        if (d.balance <= 0 || availableExtra <= 0) continue;
        final pay = math.min(d.balance, availableExtra);
        d.balance -= pay;
        availableExtra -= pay;
        if (d.balance <= 0.01) {
          d.balance = 0;
          snowballPool += d.monthlyMin;
          steps.add(
            DebtPayoffStep(
              debt: d.item,
              payoffMonth: month,
              orderIndex: order++,
              interestPaid: d.interestPaid,
            ),
          );
        }
      }
    }

    // Any remaining debts if horizon hit
    for (final d in active) {
      if (!steps.any((s) => s.debt.id == d.item.id)) {
        steps.add(
          DebtPayoffStep(
            debt: d.item,
            payoffMonth: month,
            orderIndex: order++,
            interestPaid: d.interestPaid,
          ),
        );
      }
    }

    return DebtSimulationResult(
      strategy: strategy,
      totalMonths: month,
      totalInterestPaid: totalInterest,
      steps: steps,
    );
  }
}

class _SimDebt {
  _SimDebt({
    required this.item,
    required this.balance,
    required this.monthlyMin,
  });

  final DebtItem item;
  double balance;
  final double monthlyMin;
  double interestPaid = 0;
}
