import '../core/constants/categories.dart';

class BudgetPlan {
  const BudgetPlan({
    required this.totalIncome,
    required this.necessitiesPct,
    required this.wantsPct,
    required this.savingsPct,
    required this.debtsPct,
    required this.emergencyPct,
    required this.healthScore,
    required this.healthLabelAr,
    required this.isCustomized,
  });

  final double totalIncome;
  final double necessitiesPct;
  final double wantsPct;
  final double savingsPct;
  final double debtsPct;
  final double emergencyPct;
  final int healthScore;
  final String healthLabelAr;
  final bool isCustomized;

  double percentageFor(BudgetBucket bucket) {
    switch (bucket) {
      case BudgetBucket.necessities:
        return necessitiesPct;
      case BudgetBucket.wants:
        return wantsPct;
      case BudgetBucket.savings:
        return savingsPct;
      case BudgetBucket.debts:
        return debtsPct;
      case BudgetBucket.emergency:
        return emergencyPct;
    }
  }

  double amountFor(BudgetBucket bucket) {
    return totalIncome * (percentageFor(bucket) / 100.0);
  }

  double get annualSavingsPotential =>
      (amountFor(BudgetBucket.savings) + amountFor(BudgetBucket.emergency)) * 12;

  BudgetPlan copyWith({
    double? totalIncome,
    double? necessitiesPct,
    double? wantsPct,
    double? savingsPct,
    double? debtsPct,
    double? emergencyPct,
    int? healthScore,
    String? healthLabelAr,
    bool? isCustomized,
  }) {
    return BudgetPlan(
      totalIncome: totalIncome ?? this.totalIncome,
      necessitiesPct: necessitiesPct ?? this.necessitiesPct,
      wantsPct: wantsPct ?? this.wantsPct,
      savingsPct: savingsPct ?? this.savingsPct,
      debtsPct: debtsPct ?? this.debtsPct,
      emergencyPct: emergencyPct ?? this.emergencyPct,
      healthScore: healthScore ?? this.healthScore,
      healthLabelAr: healthLabelAr ?? this.healthLabelAr,
      isCustomized: isCustomized ?? this.isCustomized,
    );
  }

  Map<String, dynamic> toJson() => {
        'totalIncome': totalIncome,
        'necessitiesPct': necessitiesPct,
        'wantsPct': wantsPct,
        'savingsPct': savingsPct,
        'debtsPct': debtsPct,
        'emergencyPct': emergencyPct,
        'healthScore': healthScore,
        'healthLabelAr': healthLabelAr,
        'isCustomized': isCustomized,
      };

  factory BudgetPlan.fromJson(Map<String, dynamic> json) {
    return BudgetPlan(
      totalIncome: (json['totalIncome'] as num?)?.toDouble() ?? 0,
      necessitiesPct: (json['necessitiesPct'] as num?)?.toDouble() ?? 50,
      wantsPct: (json['wantsPct'] as num?)?.toDouble() ?? 25,
      savingsPct: (json['savingsPct'] as num?)?.toDouble() ?? 10,
      debtsPct: (json['debtsPct'] as num?)?.toDouble() ?? 5,
      emergencyPct: (json['emergencyPct'] as num?)?.toDouble() ?? 10,
      healthScore: (json['healthScore'] as num?)?.toInt() ?? 75,
      healthLabelAr: (json['healthLabelAr'] as String?) ?? 'وضعك جيد',
      isCustomized: (json['isCustomized'] as bool?) ?? false,
    );
  }
}
