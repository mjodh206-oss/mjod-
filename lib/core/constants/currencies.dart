class AppCurrency {
  const AppCurrency({
    required this.code,
    required this.nameAr,
    required this.symbol,
    required this.sampleSalary,
    required this.quickAmounts,
    this.allowDecimals = false,
  });

  final String code;
  final String nameAr;
  final String symbol;
  final double sampleSalary;
  final List<double> quickAmounts;
  final bool allowDecimals;
}

class AppCurrencies {
  AppCurrencies._();

  static const List<AppCurrency> all = [
    AppCurrency(
      code: 'SYP',
      nameAr: 'ليرة سورية',
      symbol: 'ل.س',
      sampleSalary: 1500000,
      quickAmounts: [500000, 1000000, 2000000, 3500000],
    ),
    AppCurrency(
      code: 'USD',
      nameAr: 'دولار أمريكي',
      symbol: '\$',
      sampleSalary: 800,
      quickAmounts: [400, 800, 1500, 2500],
      allowDecimals: true,
    ),
    AppCurrency(
      code: 'EUR',
      nameAr: 'يورو',
      symbol: '€',
      sampleSalary: 1500,
      quickAmounts: [800, 1500, 2500, 4000],
      allowDecimals: true,
    ),
    AppCurrency(
      code: 'SAR',
      nameAr: 'ريال سعودي',
      symbol: 'ر.س',
      sampleSalary: 6000,
      quickAmounts: [3000, 6000, 10000, 15000],
    ),
    AppCurrency(
      code: 'AED',
      nameAr: 'درهم إماراتي',
      symbol: 'د.إ',
      sampleSalary: 7000,
      quickAmounts: [4000, 7000, 12000, 20000],
    ),
    AppCurrency(
      code: 'JOD',
      nameAr: 'دينار أردني',
      symbol: 'د.أ',
      sampleSalary: 600,
      quickAmounts: [350, 600, 1000, 1500],
      allowDecimals: true,
    ),
    AppCurrency(
      code: 'EGP',
      nameAr: 'جنيه مصري',
      symbol: 'ج.م',
      sampleSalary: 12000,
      quickAmounts: [6000, 12000, 20000, 35000],
    ),
    AppCurrency(
      code: 'TRY',
      nameAr: 'ليرة تركية',
      symbol: '₺',
      sampleSalary: 28000,
      quickAmounts: [18000, 28000, 45000, 70000],
    ),
    AppCurrency(
      code: 'IQD',
      nameAr: 'دينار عراقي',
      symbol: 'د.ع',
      sampleSalary: 900000,
      quickAmounts: [500000, 900000, 1500000, 2500000],
    ),
    AppCurrency(
      code: 'LBP',
      nameAr: 'ليرة لبنانية',
      symbol: 'ل.ل',
      sampleSalary: 30000000,
      quickAmounts: [15000000, 30000000, 60000000, 90000000],
    ),
  ];

  static AppCurrency byCode(String code) {
    return all.firstWhere(
      (c) => c.code == code,
      orElse: () => all.first,
    );
  }
}
