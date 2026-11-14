enum PrimaryFinancialGoal {
  saving,
  debtPayoff,
  purchase,
  emergencyFund,
}

class SalaryProfile {
  const SalaryProfile({
    required this.monthlySalary,
    required this.currencyCode,
    this.extraIncome = 0,
    this.isRenting = false,
    this.rentAmount = 0,
    this.familyMembers = 1,
    this.dependents = 0,
    this.utilitiesCost = 0,
    this.transportCost = 0,
    this.telecomCost = 0,
    this.installmentsCost = 0,
    this.debtsMonthlyPayment = 0,
    this.hasCurrentSavings = false,
    this.currentSavings = 0,
    this.primaryGoal = PrimaryFinancialGoal.saving,
    this.salaryDay = 1,
    this.billDueDay = 5,
  });

  final double monthlySalary;
  final String currencyCode;
  final double extraIncome;
  final bool isRenting;
  final double rentAmount;
  final int familyMembers;
  final int dependents;
  final double utilitiesCost;
  final double transportCost;
  final double telecomCost;
  final double installmentsCost;
  final double debtsMonthlyPayment;
  final bool hasCurrentSavings;
  final double currentSavings;
  final PrimaryFinancialGoal primaryGoal;
  final int salaryDay;
  final int billDueDay;

  double get totalMonthlyIncome => monthlySalary + extraIncome;

  double get totalFixedNonDebt =>
      (isRenting ? rentAmount : 0) +
      utilitiesCost +
      transportCost +
      telecomCost;

  double get totalDebtAndInstallments => installmentsCost + debtsMonthlyPayment;

  SalaryProfile copyWith({
    double? monthlySalary,
    String? currencyCode,
    double? extraIncome,
    bool? isRenting,
    double? rentAmount,
    int? familyMembers,
    int? dependents,
    double? utilitiesCost,
    double? transportCost,
    double? telecomCost,
    double? installmentsCost,
    double? debtsMonthlyPayment,
    bool? hasCurrentSavings,
    double? currentSavings,
    PrimaryFinancialGoal? primaryGoal,
    int? salaryDay,
    int? billDueDay,
  }) {
    return SalaryProfile(
      monthlySalary: monthlySalary ?? this.monthlySalary,
      currencyCode: currencyCode ?? this.currencyCode,
      extraIncome: extraIncome ?? this.extraIncome,
      isRenting: isRenting ?? this.isRenting,
      rentAmount: rentAmount ?? this.rentAmount,
      familyMembers: familyMembers ?? this.familyMembers,
      dependents: dependents ?? this.dependents,
      utilitiesCost: utilitiesCost ?? this.utilitiesCost,
      transportCost: transportCost ?? this.transportCost,
      telecomCost: telecomCost ?? this.telecomCost,
      installmentsCost: installmentsCost ?? this.installmentsCost,
      debtsMonthlyPayment: debtsMonthlyPayment ?? this.debtsMonthlyPayment,
      hasCurrentSavings: hasCurrentSavings ?? this.hasCurrentSavings,
      currentSavings: currentSavings ?? this.currentSavings,
      primaryGoal: primaryGoal ?? this.primaryGoal,
      salaryDay: salaryDay ?? this.salaryDay,
      billDueDay: billDueDay ?? this.billDueDay,
    );
  }

  Map<String, dynamic> toJson() => {
        'monthlySalary': monthlySalary,
        'currencyCode': currencyCode,
        'extraIncome': extraIncome,
        'isRenting': isRenting,
        'rentAmount': rentAmount,
        'familyMembers': familyMembers,
        'dependents': dependents,
        'utilitiesCost': utilitiesCost,
        'transportCost': transportCost,
        'telecomCost': telecomCost,
        'installmentsCost': installmentsCost,
        'debtsMonthlyPayment': debtsMonthlyPayment,
        'hasCurrentSavings': hasCurrentSavings,
        'currentSavings': currentSavings,
        'primaryGoal': primaryGoal.name,
        'salaryDay': salaryDay,
        'billDueDay': billDueDay,
      };

  factory SalaryProfile.fromJson(Map<String, dynamic> json) {
    return SalaryProfile(
      monthlySalary: (json['monthlySalary'] as num?)?.toDouble() ?? 0,
      currencyCode: (json['currencyCode'] as String?) ?? 'SYP',
      extraIncome: (json['extraIncome'] as num?)?.toDouble() ?? 0,
      isRenting: (json['isRenting'] as bool?) ?? false,
      rentAmount: (json['rentAmount'] as num?)?.toDouble() ?? 0,
      familyMembers: (json['familyMembers'] as num?)?.toInt() ?? 1,
      dependents: (json['dependents'] as num?)?.toInt() ?? 0,
      utilitiesCost: (json['utilitiesCost'] as num?)?.toDouble() ?? 0,
      transportCost: (json['transportCost'] as num?)?.toDouble() ?? 0,
      telecomCost: (json['telecomCost'] as num?)?.toDouble() ?? 0,
      installmentsCost: (json['installmentsCost'] as num?)?.toDouble() ?? 0,
      debtsMonthlyPayment: (json['debtsMonthlyPayment'] as num?)?.toDouble() ?? 0,
      hasCurrentSavings: (json['hasCurrentSavings'] as bool?) ?? false,
      currentSavings: (json['currentSavings'] as num?)?.toDouble() ?? 0,
      primaryGoal: PrimaryFinancialGoal.values.firstWhere(
        (g) => g.name == json['primaryGoal'],
        orElse: () => PrimaryFinancialGoal.saving,
      ),
      salaryDay: ((json['salaryDay'] as num?)?.toInt() ?? 1).clamp(1, 31),
      billDueDay: ((json['billDueDay'] as num?)?.toInt() ?? 5).clamp(1, 31),
    );
  }
}
