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

class PlanEngine {
  PlanEngine._();

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
              'الديون والأقساط تستهلك ${plan.debtsPct.toStringAsFixed(1)}% من دخلك (${ArabicFormatters.formatMoney(plan.amountFor(BudgetBucket.debts), currency)} شهرياً).',
          actionStepsAr: [
            'استخدم «كرة الثلج» بسداد أصغر دين أولاً لتحصل على دافع سريع.',
            'أو اختر «الانهيار الجليدي» بسداد الدين الأعلى فائدة لتوفير أكثر.',
            'جرّب حاسبة سداد الديون بالأسفل لترى تاريخ تحررك من الديون!',
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
              'الهدف الآمن لصندوق الطوارئ هو تغطية 3 أشهر من الضروريات (${ArabicFormatters.formatMoney(target3Months, currency)}).',
          actionStepsAr: [
            'خصّص ${plan.emergencyPct.toStringAsFixed(0)}% شهرياً (${ArabicFormatters.formatMoney(plan.amountFor(BudgetBucket.emergency), currency)}) للطوارئ.',
            'احتفظ بمبلغ الطوارئ منفصلاً عن مصروف الجيب اليومي.',
          ],
          severity: AdviceSeverity.info,
          bucket: BudgetBucket.emergency,
        ),
      );
    }

    // Positive reinforcement when savings + emergency >= 20%
    if (plan.savingsPct + plan.emergencyPct >= 20) {
      advice.add(
        SmartAdviceItem(
          id: 'strong_saving',
          titleAr: 'معدل ادخارك قوي ومطابق لقاعدة 50/30/20 🎯',
          bodyAr:
              'أنت تدخر وتؤمّن مستقبلك بنسبة ${(plan.savingsPct + plan.emergencyPct).toStringAsFixed(0)}% من دخلك، أي ما يعادل ${ArabicFormatters.formatMoney(plan.annualSavingsPotential, currency)} سنوياً!',
          actionStepsAr: [
            'اربط جزءاً من هذا الادخار بهدف واضح داخل قسم الأهداف.',
            'استمر على الالتزام الأسبوعي لتحصد الشارات الذهبية.',
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
