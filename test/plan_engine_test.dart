import 'package:flutter_test/flutter_test.dart';
import 'package:rattib/core/constants/categories.dart';
import 'package:rattib/core/constants/currencies.dart';
import 'package:rattib/core/utils/arabic_formatters.dart';
import 'package:rattib/models/debt_item.dart';
import 'package:rattib/models/salary_profile.dart';
import 'package:rattib/services/plan_engine.dart';

void main() {
  group('PlanEngine', () {
    test('generates balanced 100% plan for typical salary profile', () {
      const profile = SalaryProfile(
        monthlySalary: 1500000,
        currencyCode: 'SYP',
        isRenting: true,
        rentAmount: 350000,
        familyMembers: 3,
        dependents: 2,
        utilitiesCost: 120000,
        transportCost: 100000,
        telecomCost: 50000,
        hasCurrentSavings: false,
        primaryGoal: PrimaryFinancialGoal.saving,
      );

      final plan = PlanEngine.generatePlan(profile);
      final sum = plan.necessitiesPct +
          plan.wantsPct +
          plan.savingsPct +
          plan.debtsPct +
          plan.emergencyPct;

      expect(sum, closeTo(100.0, 0.2));
      expect(plan.healthScore, inInclusiveRange(15, 100));
      expect(plan.annualSavingsPotential, greaterThan(0));
    });

    test('slider rebalancing preserves 100% sum and updates live', () {
      const profile = SalaryProfile(
        monthlySalary: 1000,
        currencyCode: 'USD',
        primaryGoal: PrimaryFinancialGoal.saving,
      );
      final initial = PlanEngine.generatePlan(profile);
      final updated = PlanEngine.rebalanceFromSlider(
        profile: profile,
        current: initial,
        changedBucket: BudgetBucket.savings,
        newPercentage: 35,
      );

      final sum = updated.necessitiesPct +
          updated.wantsPct +
          updated.savingsPct +
          updated.debtsPct +
          updated.emergencyPct;
      expect(updated.savingsPct, closeTo(35.0, 0.2));
      expect(sum, closeTo(100.0, 0.2));
      expect(updated.isCustomized, isTrue);
    });

    test('emits >40% rent advice and over-budget critical alert', () {
      const profile = SalaryProfile(
        monthlySalary: 1000,
        currencyCode: 'USD',
        isRenting: true,
        rentAmount: 480,
      );
      final plan = PlanEngine.generatePlan(profile);
      final spent = <BudgetBucket, double>{
        BudgetBucket.wants: plan.amountFor(BudgetBucket.wants) + 120,
      };
      final advice = PlanEngine.generateSmartAdvice(
        profile: profile,
        plan: plan,
        spentByBucket: spent,
        currency: AppCurrencies.byCode('USD'),
      );

      expect(advice.any((a) => a.id == 'high_rent'), isTrue);
      expect(
        advice.any((a) => a.severity == AdviceSeverity.critical),
        isTrue,
      );
    });

    test('snowball vs avalanche orders debts properly and avalanche saves interest', () {
      const debts = [
        DebtItem(
          id: 'd1',
          name: 'بطاقة ائتمان',
          remainingBalance: 2000,
          minimumMonthly: 100,
          interestRatePercent: 24,
        ),
        DebtItem(
          id: 'd2',
          name: 'دين شخصي صغير',
          remainingBalance: 600,
          minimumMonthly: 80,
          interestRatePercent: 0,
        ),
      ];

      final snowball = PlanEngine.simulateDebtPayoff(
        debts: debts,
        extraMonthlyPayment: 150,
        strategy: DebtStrategy.snowball,
      );
      final avalanche = PlanEngine.simulateDebtPayoff(
        debts: debts,
        extraMonthlyPayment: 150,
        strategy: DebtStrategy.avalanche,
      );

      expect(snowball.steps.first.debt.id, 'd2');
      expect(avalanche.steps.first.debt.id, 'd1');
      expect(
        avalanche.totalInterestPaid,
        lessThanOrEqualTo(snowball.totalInterestPaid),
      );
    });

    test('ArabicFormatters calculates days until salary accurately', () {
      final days = ArabicFormatters.daysUntilSalary(
        15,
        DateTime(2026, 11, 10),
      );
      expect(days, 5);
    });
  });
}
