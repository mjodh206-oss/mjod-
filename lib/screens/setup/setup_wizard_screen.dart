import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/constants/currencies.dart';
import '../../core/utils/arabic_formatters.dart';
import '../../core/utils/page_transitions.dart';
import '../../models/salary_profile.dart';
import '../../state/app_controller.dart';
import '../../widgets/animated_counter.dart';
import '../home/main_shell.dart';

class SetupWizardScreen extends StatefulWidget {
  const SetupWizardScreen({
    super.key,
    required this.controller,
    this.isEditing = false,
  });

  final AppController controller;
  final bool isEditing;

  @override
  State<SetupWizardScreen> createState() => _SetupWizardScreenState();
}

class _SetupWizardScreenState extends State<SetupWizardScreen> {
  static const int _totalSteps = 8;
  final PageController _pageCtrl = PageController();
  int _step = 0;
  bool _saving = false;

  late String _currencyCode;
  String _salaryDigits = '';
  bool _hasExtraIncome = false;
  final TextEditingController _extraCtrl = TextEditingController();
  bool _isRenting = false;
  final TextEditingController _rentCtrl = TextEditingController();
  int _familyMembers = 1;
  int _dependents = 0;

  final TextEditingController _utilitiesCtrl = TextEditingController();
  final TextEditingController _transportCtrl = TextEditingController();
  final TextEditingController _telecomCtrl = TextEditingController();
  final TextEditingController _installmentsCtrl = TextEditingController();
  final TextEditingController _debtsCtrl = TextEditingController();

  bool _hasCurrentSavings = false;
  final TextEditingController _savingsCtrl = TextEditingController();
  PrimaryFinancialGoal _goal = PrimaryFinancialGoal.saving;
  int _salaryDay = 1;
  int _billDueDay = 5;

  @override
  void initState() {
    super.initState();
    final existing = widget.controller.profile;
    if (existing != null) {
      _currencyCode = existing.currencyCode;
      _salaryDigits = existing.monthlySalary > 0
          ? existing.monthlySalary.toInt().toString()
          : '';
      _hasExtraIncome = existing.extraIncome > 0;
      if (_hasExtraIncome) {
        _extraCtrl.text = existing.extraIncome.toInt().toString();
      }
      _isRenting = existing.isRenting;
      if (existing.rentAmount > 0) {
        _rentCtrl.text = existing.rentAmount.toInt().toString();
      }
      _familyMembers = existing.familyMembers;
      _dependents = existing.dependents;
      if (existing.utilitiesCost > 0) {
        _utilitiesCtrl.text = existing.utilitiesCost.toInt().toString();
      }
      if (existing.transportCost > 0) {
        _transportCtrl.text = existing.transportCost.toInt().toString();
      }
      if (existing.telecomCost > 0) {
        _telecomCtrl.text = existing.telecomCost.toInt().toString();
      }
      if (existing.installmentsCost > 0) {
        _installmentsCtrl.text = existing.installmentsCost.toInt().toString();
      }
      if (existing.debtsMonthlyPayment > 0) {
        _debtsCtrl.text = existing.debtsMonthlyPayment.toInt().toString();
      }
      _hasCurrentSavings = existing.hasCurrentSavings;
      if (existing.currentSavings > 0) {
        _savingsCtrl.text = existing.currentSavings.toInt().toString();
      }
      _goal = existing.primaryGoal;
      _salaryDay = existing.salaryDay;
      _billDueDay = existing.billDueDay;
    } else {
      _currencyCode = 'SYP';
    }
  }

  @override
  void dispose() {
    _pageCtrl.dispose();
    _extraCtrl.dispose();
    _rentCtrl.dispose();
    _utilitiesCtrl.dispose();
    _transportCtrl.dispose();
    _telecomCtrl.dispose();
    _installmentsCtrl.dispose();
    _debtsCtrl.dispose();
    _savingsCtrl.dispose();
    super.dispose();
  }

  AppCurrency get _currency => AppCurrencies.byCode(_currencyCode);

  double get _parsedSalary => double.tryParse(_salaryDigits) ?? 0.0;

  double _parseCtrl(TextEditingController c) =>
      double.tryParse(c.text.replaceAll(',', '').trim()) ?? 0.0;

  void _appendDigit(String d) {
    if (_salaryDigits.length >= 11) return;
    setState(() {
      if (_salaryDigits == '0') {
        _salaryDigits = d == '000' ? '0' : d;
      } else {
        _salaryDigits = '$_salaryDigits$d';
      }
    });
  }

  void _backspace() {
    if (_salaryDigits.isEmpty) return;
    setState(() {
      _salaryDigits = _salaryDigits.substring(0, _salaryDigits.length - 1);
    });
  }

  void _nextStep() {
    FocusScope.of(context).unfocus();
    if (_step == 0 && _parsedSalary <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('أدخل راتبك الشهري أولاً لنبني لك الخطة')),
      );
      return;
    }
    if (_step == 2 && _isRenting && _parseCtrl(_rentCtrl) <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('أدخل مبلغ الإيجار الشهري')),
      );
      return;
    }

    if (_step < _totalSteps - 1) {
      setState(() => _step++);
      _pageCtrl.animateToPage(
        _step,
        duration: const Duration(milliseconds: 320),
        curve: Curves.easeOutCubic,
      );
    } else {
      _submit();
    }
  }

  void _prevStep() {
    FocusScope.of(context).unfocus();
    if (_step > 0) {
      setState(() => _step--);
      _pageCtrl.animateToPage(
        _step,
        duration: const Duration(milliseconds: 280),
        curve: Curves.easeOutCubic,
      );
    } else if (widget.isEditing) {
      Navigator.of(context).pop();
    }
  }

  Future<void> _submit() async {
    setState(() => _saving = true);
    final profile = SalaryProfile(
      monthlySalary: _parsedSalary,
      currencyCode: _currencyCode,
      extraIncome: _hasExtraIncome ? _parseCtrl(_extraCtrl) : 0,
      isRenting: _isRenting,
      rentAmount: _isRenting ? _parseCtrl(_rentCtrl) : 0,
      familyMembers: _familyMembers,
      dependents: _dependents,
      utilitiesCost: _parseCtrl(_utilitiesCtrl),
      transportCost: _parseCtrl(_transportCtrl),
      telecomCost: _parseCtrl(_telecomCtrl),
      installmentsCost: _parseCtrl(_installmentsCtrl),
      debtsMonthlyPayment: _parseCtrl(_debtsCtrl),
      hasCurrentSavings: _hasCurrentSavings,
      currentSavings: _hasCurrentSavings ? _parseCtrl(_savingsCtrl) : 0,
      primaryGoal: _goal,
      salaryDay: _salaryDay,
      billDueDay: _billDueDay,
    );

    await widget.controller.saveWizardProfile(profile);
    if (!mounted) return;

    if (widget.isEditing) {
      Navigator.of(context).pop();
    } else {
      Navigator.of(context).pushReplacement(
        SmoothPageRoute(
          builder: (_) => MainShell(controller: widget.controller),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final progress = (_step + 1) / _totalSteps;

    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            // Animated top progress bar + counter step number
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 8),
              child: Column(
                children: [
                  Row(
                    children: [
                      if (_step > 0 || widget.isEditing)
                        IconButton(
                          onPressed: _prevStep,
                          icon: const Icon(Icons.arrow_forward_rounded),
                          tooltip: 'رجوع',
                        )
                      else
                        const SizedBox(width: 48),
                      Expanded(
                        child: Center(
                          child: AnimatedStepNumber(
                            step: _step + 1,
                            total: _totalSteps,
                            style: theme.textTheme.titleMedium?.copyWith(
                              color: theme.colorScheme.primary,
                            ),
                          ),
                        ),
                      ),
                      Text(
                        '${(progress * 100).round()}%',
                        style: theme.textTheme.titleMedium?.copyWith(
                          color: theme.colorScheme.secondary,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: Container(
                      height: 10,
                      width: double.infinity,
                      color: theme.colorScheme.primary.withValues(alpha: 0.14),
                      child: TweenAnimationBuilder<double>(
                        tween: Tween<double>(begin: 0, end: progress),
                        duration: const Duration(milliseconds: 350),
                        curve: Curves.easeOutCubic,
                        builder: (context, val, _) {
                          return FractionallySizedBox(
                            alignment: AlignmentDirectional.centerStart,
                            widthFactor: val,
                            child: Container(
                              decoration: BoxDecoration(
                                gradient: LinearGradient(
                                  colors: [
                                    theme.colorScheme.primary,
                                    theme.colorScheme.secondary,
                                  ],
                                ),
                                borderRadius: BorderRadius.circular(10),
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // Single-question pages
            Expanded(
              child: PageView(
                controller: _pageCtrl,
                physics: const NeverScrollableScrollPhysics(),
                children: [
                  _buildSalaryStep(theme),
                  _buildExtraIncomeStep(theme),
                  _buildHousingStep(theme),
                  _buildFamilyStep(theme),
                  _buildObligationsStep(theme),
                  _buildSavingsStep(theme),
                  _buildGoalStep(theme),
                  _buildSalaryDayStep(theme),
                ],
              ),
            ),

            // Bottom CTA
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 18),
              child: FilledButton.icon(
                onPressed: _saving ? null : _nextStep,
                icon: Icon(
                  _step == _totalSteps - 1
                      ? Icons.auto_awesome_rounded
                      : Icons.arrow_back_rounded,
                ),
                label: Text(
                  _saving
                      ? 'جارٍ إعداد خطتك...'
                      : (_step == _totalSteps - 1
                          ? 'جهّز خطتي الآن'
                          : 'التالي'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _stepShell({
    required ThemeData theme,
    required String title,
    required String subtitle,
    required Widget child,
  }) {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(title, style: theme.textTheme.headlineMedium),
          const SizedBox(height: 6),
          Text(subtitle, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 18),
          child,
        ],
      ),
    );
  }

  // Step 1: Monthly Salary + Large Keypad + Currency selector
  Widget _buildSalaryStep(ThemeData theme) {
    final formatted = ArabicFormatters.formatNumber(
      _parsedSalary,
      allowDecimals: _currency.allowDecimals,
    );

    return _stepShell(
      theme: theme,
      title: 'كم راتبك الشهري؟',
      subtitle: 'اختر العملة وأدخل صافي الراتب باستخدام لوحة الأرقام.',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Currency selector
          SizedBox(
            height: 44,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: AppCurrencies.all.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, idx) {
                final cur = AppCurrencies.all[idx];
                final selected = cur.code == _currencyCode;
                return ChoiceChip(
                  label: Text(
                    '${cur.nameAr} (${cur.symbol})',
                    style: const TextStyle(
                      fontFamily: 'Cairo',
                      fontWeight: FontWeight.w700,
                      fontSize: 12.5,
                    ),
                  ),
                  selected: selected,
                  onSelected: (_) => setState(() => _currencyCode = cur.code),
                );
              },
            ),
          ),
          const SizedBox(height: 14),

          // Display box
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  theme.colorScheme.primary.withValues(alpha: 0.12),
                  theme.colorScheme.secondary.withValues(alpha: 0.10),
                ],
              ),
              borderRadius: BorderRadius.circular(22),
              border: Border.all(
                color: theme.colorScheme.primary.withValues(alpha: 0.25),
              ),
            ),
            child: Column(
              children: [
                Text(
                  '$formatted ${_currency.symbol}',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontFamily: 'Cairo',
                    fontSize: 32,
                    fontWeight: FontWeight.w800,
                    color: theme.colorScheme.primary,
                  ),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  alignment: WrapAlignment.center,
                  children: _currency.quickAmounts.map((q) {
                    return ActionChip(
                      label: Text(
                        ArabicFormatters.formatCompactMoney(q, _currency),
                        style: const TextStyle(
                          fontFamily: 'Cairo',
                          fontSize: 11.5,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      onPressed: () {
                        setState(() => _salaryDigits = q.toInt().toString());
                      },
                    );
                  }).toList(),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),

          // Large Numeric Keypad (RTL-safe layout forced LTR for calculator grid)
          Directionality(
            textDirection: TextDirection.ltr,
            child: GridView.count(
              crossAxisCount: 3,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              mainAxisSpacing: 8,
              crossAxisSpacing: 8,
              childAspectRatio: 2.15,
              children: [
                ...['1', '2', '3', '4', '5', '6', '7', '8', '9', '000', '0']
                    .map((keyLabel) {
                  return ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      elevation: 0,
                      backgroundColor: theme.cardColor,
                      foregroundColor: theme.textTheme.bodyLarge?.color,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                        side: BorderSide(
                          color: Colors.grey.withValues(alpha: 0.18),
                        ),
                      ),
                    ),
                    onPressed: () => _appendDigit(keyLabel),
                    child: Text(
                      keyLabel,
                      style: const TextStyle(
                        fontFamily: 'Cairo',
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  );
                }),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    elevation: 0,
                    backgroundColor: const Color(0xFFEF4444).withValues(alpha: 0.12),
                    foregroundColor: const Color(0xFFEF4444),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  onPressed: _backspace,
                  onLongPress: () => setState(() => _salaryDigits = ''),
                  child: const Icon(Icons.backspace_rounded, size: 22),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // Step 2: Extra Income
  Widget _buildExtraIncomeStep(ThemeData theme) {
    return _stepShell(
      theme: theme,
      title: 'هل عندك دخل إضافي؟',
      subtitle: 'اختياري — عمل حر، مكافأة، أو مصدر جانبي ثابت شهرياً.',
      child: Column(
        children: [
          _optionTile(
            theme: theme,
            selected: !_hasExtraIncome,
            icon: Icons.work_outline_rounded,
            title: 'لا، الراتب فقط',
            subtitle: 'تخطّ هذه الخطوة ببساطة',
            onTap: () => setState(() => _hasExtraIncome = false),
          ),
          const SizedBox(height: 12),
          _optionTile(
            theme: theme,
            selected: _hasExtraIncome,
            icon: Icons.add_chart_rounded,
            title: 'نعم، عندي دخل إضافي',
            subtitle: 'أدخل المعدل الشهري التقريبي',
            onTap: () => setState(() => _hasExtraIncome = true),
          ),
          if (_hasExtraIncome) ...[
            const SizedBox(height: 16),
            _moneyField(
              controller: _extraCtrl,
              label: 'مبلغ الدخل الإضافي (${_currency.symbol})',
              icon: Icons.attach_money_rounded,
            ),
          ],
        ],
      ),
    );
  }

  // Step 3: Housing
  Widget _buildHousingStep(ThemeData theme) {
    return _stepShell(
      theme: theme,
      title: 'ما نوع السكن؟',
      subtitle: 'يساعدنا معرفة ضغط السكن على راتبك لنعطيك نصائح أدق.',
      child: Column(
        children: [
          _optionTile(
            theme: theme,
            selected: !_isRenting,
            icon: Icons.home_rounded,
            title: 'سكن ملك / عائلي',
            subtitle: 'بدون إيجار شهري',
            onTap: () => setState(() => _isRenting = false),
          ),
          const SizedBox(height: 12),
          _optionTile(
            theme: theme,
            selected: _isRenting,
            icon: Icons.apartment_rounded,
            title: 'سكن بالإيجار',
            subtitle: 'أدخل قيمة الإيجار الشهري',
            onTap: () => setState(() => _isRenting = true),
          ),
          if (_isRenting) ...[
            const SizedBox(height: 16),
            _moneyField(
              controller: _rentCtrl,
              label: 'مبلغ الإيجار الشهري (${_currency.symbol})',
              icon: Icons.key_rounded,
            ),
          ],
        ],
      ),
    );
  }

  // Step 4: Family size & Dependents
  Widget _buildFamilyStep(ThemeData theme) {
    return _stepShell(
      theme: theme,
      title: 'عدد أفراد الأسرة ومن تعيل',
      subtitle: 'نحسب مصاريف المعيشة المناسبة لحجم أسرتك.',
      child: Column(
        children: [
          _counterCard(
            theme: theme,
            icon: Icons.groups_rounded,
            title: 'عدد أفراد الأسرة الكلي',
            subtitle: 'بمن فيهم أنت',
            value: _familyMembers,
            min: 1,
            max: 15,
            onChanged: (v) {
              setState(() {
                _familyMembers = v;
                if (_dependents >= v) _dependents = v - 1;
              });
            },
          ),
          const SizedBox(height: 14),
          _counterCard(
            theme: theme,
            icon: Icons.family_restroom_rounded,
            title: 'كم شخصاً تعيل مالياً؟',
            subtitle: 'أولاد، والدين، أو زوجة',
            value: _dependents,
            min: 0,
            max: 14,
            onChanged: (v) => setState(() => _dependents = v),
          ),
        ],
      ),
    );
  }

  // Step 5: Fixed Obligations
  Widget _buildObligationsStep(ThemeData theme) {
    return _stepShell(
      theme: theme,
      title: 'التزاماتك الشهرية الثابتة',
      subtitle: 'اترك أي حقل فارغاً أو صفراً إذا لم يكن ينطبق عليك.',
      child: Column(
        children: [
          _moneyField(
            controller: _utilitiesCtrl,
            label: 'فواتير (كهرباء، ماء، غاز) (${_currency.symbol})',
            icon: Icons.receipt_long_rounded,
          ),
          const SizedBox(height: 12),
          _moneyField(
            controller: _transportCtrl,
            label: 'مواصلات وبنزين (${_currency.symbol})',
            icon: Icons.directions_car_rounded,
          ),
          const SizedBox(height: 12),
          _moneyField(
            controller: _telecomCtrl,
            label: 'اتصالات وإنترنت (${_currency.symbol})',
            icon: Icons.wifi_rounded,
          ),
          const SizedBox(height: 12),
          _moneyField(
            controller: _installmentsCtrl,
            label: 'أقساط شهرية (${_currency.symbol})',
            icon: Icons.credit_card_rounded,
          ),
          const SizedBox(height: 12),
          _moneyField(
            controller: _debtsCtrl,
            label: 'سداد ديون شهرية (${_currency.symbol})',
            icon: Icons.payments_rounded,
          ),
        ],
      ),
    );
  }

  // Step 6: Current Savings
  Widget _buildSavingsStep(ThemeData theme) {
    return _stepShell(
      theme: theme,
      title: 'هل عندك مدخرات حالياً؟',
      subtitle: 'لتقييم صندوق الطوارئ الخاص بك بدقة.',
      child: Column(
        children: [
          _optionTile(
            theme: theme,
            selected: !_hasCurrentSavings,
            icon: Icons.hourglass_empty_rounded,
            title: 'لا توجد مدخرات حالياً',
            subtitle: 'سنبدأ ببناء احتياطي أمان من الصفر',
            onTap: () => setState(() => _hasCurrentSavings = false),
          ),
          const SizedBox(height: 12),
          _optionTile(
            theme: theme,
            selected: _hasCurrentSavings,
            icon: Icons.savings_rounded,
            title: 'نعم، عندي مبلغ مدّخر',
            subtitle: 'أدخل إجمالي مدخراتك الحالية',
            onTap: () => setState(() => _hasCurrentSavings = true),
          ),
          if (_hasCurrentSavings) ...[
            const SizedBox(height: 16),
            _moneyField(
              controller: _savingsCtrl,
              label: 'كم تبلغ مدخراتك الحالية؟ (${_currency.symbol})',
              icon: Icons.account_balance_wallet_rounded,
            ),
          ],
        ],
      ),
    );
  }

  // Step 7: Primary Goal
  Widget _buildGoalStep(ThemeData theme) {
    const goals = [
      (
        goal: PrimaryFinancialGoal.saving,
        icon: Icons.savings_rounded,
        title: 'ادخار وتنمية المال',
        sub: 'تعظيم الفائض الشهري وبناء ثروة تدريجية',
      ),
      (
        goal: PrimaryFinancialGoal.debtPayoff,
        icon: Icons.credit_score_rounded,
        title: 'سداد الديون أولاً',
        sub: 'توجيه فائض إضافي للتخلص من الأقساط أسرع',
      ),
      (
        goal: PrimaryFinancialGoal.purchase,
        icon: Icons.shopping_bag_rounded,
        title: 'شراء شيء مهم',
        sub: 'تجهيز مبلغ لسيارة أو زواج أو جهاز أو سفر',
      ),
      (
        goal: PrimaryFinancialGoal.emergencyFund,
        icon: Icons.shield_rounded,
        title: 'بناء صندوق طوارئ',
        sub: 'تأمين مصاريف 3 إلى 6 أشهر لراحة البال',
      ),
    ];

    return _stepShell(
      theme: theme,
      title: 'ما هدفك المالي الأساسي الآن؟',
      subtitle: 'سنكيّف توزيع راتبك فوراً ليخدم هدفك الأهم.',
      child: Column(
        children: goals.map((g) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: _optionTile(
              theme: theme,
              selected: _goal == g.goal,
              icon: g.icon,
              title: g.title,
              subtitle: g.sub,
              onTap: () => setState(() => _goal = g.goal),
            ),
          );
        }).toList(),
      ),
    );
  }

  // Step 8: Salary Date
  Widget _buildSalaryDayStep(ThemeData theme) {
    return _stepShell(
      theme: theme,
      title: 'متى تستلم راتبك كل شهر؟',
      subtitle: 'نضبط عليه تذكير «نزل الراتب!» وبداية دورتك الشهرية.',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'يوم استلام الراتب: اليوم $_salaryDay من كل شهر',
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: theme.colorScheme.primary,
                    ),
                  ),
                  const SizedBox(height: 12),
                  GridView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: 31,
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 7,
                      mainAxisSpacing: 6,
                      crossAxisSpacing: 6,
                    ),
                    itemBuilder: (context, index) {
                      final day = index + 1;
                      final selected = day == _salaryDay;
                      return InkWell(
                        onTap: () => setState(() => _salaryDay = day),
                        borderRadius: BorderRadius.circular(10),
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 180),
                          alignment: Alignment.center,
                          decoration: BoxDecoration(
                            color: selected
                                ? theme.colorScheme.primary
                                : theme.scaffoldBackgroundColor,
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(
                              color: selected
                                  ? theme.colorScheme.primary
                                  : Colors.grey.withValues(alpha: 0.2),
                            ),
                          ),
                          child: Text(
                            '$day',
                            style: TextStyle(
                              fontFamily: 'Cairo',
                              fontWeight: FontWeight.w700,
                              color: selected ? Colors.white : null,
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                children: [
                  Icon(
                    Icons.notifications_active_rounded,
                    color: theme.colorScheme.secondary,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'تذكير سداد الفواتير والأقساط كل شهر',
                      style: theme.textTheme.bodyLarge,
                    ),
                  ),
                  DropdownButton<int>(
                    value: _billDueDay,
                    underline: const SizedBox.shrink(),
                    items: List.generate(28, (i) => i + 1)
                        .map(
                          (d) => DropdownMenuItem(
                            value: d,
                            child: Text('يوم $d'),
                          ),
                        )
                        .toList(),
                    onChanged: (v) {
                      if (v != null) setState(() => _billDueDay = v);
                    },
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _optionTile({
    required ThemeData theme,
    required bool selected,
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(18),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: selected
              ? theme.colorScheme.primary.withValues(alpha: 0.12)
              : theme.cardColor,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: selected
                ? theme.colorScheme.primary
                : Colors.grey.withValues(alpha: 0.2),
            width: selected ? 2 : 1,
          ),
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: (selected
                        ? theme.colorScheme.primary
                        : theme.colorScheme.secondary)
                    .withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(
                icon,
                color: selected
                    ? theme.colorScheme.primary
                    : theme.colorScheme.secondary,
                size: 26,
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: theme.textTheme.titleMedium),
                  const SizedBox(height: 2),
                  Text(subtitle, style: theme.textTheme.bodyMedium),
                ],
              ),
            ),
            if (selected)
              Icon(
                Icons.check_circle_rounded,
                color: theme.colorScheme.primary,
                size: 24,
              ),
          ],
        ),
      ),
    );
  }

  Widget _moneyField({
    required TextEditingController controller,
    required String label,
    required IconData icon,
  }) {
    return TextField(
      controller: controller,
      keyboardType: TextInputType.numberWithOptions(
        decimal: _currency.allowDecimals,
      ),
      inputFormatters: [
        FilteringTextInputFormatter.allow(
          _currency.allowDecimals ? RegExp(r'[0-9.]') : RegExp(r'[0-9]'),
        ),
      ],
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon),
      ),
    );
  }

  Widget _counterCard({
    required ThemeData theme,
    required IconData icon,
    required String title,
    required String subtitle,
    required int value,
    required int min,
    required int max,
    required ValueChanged<int> onChanged,
  }) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(icon, color: theme.colorScheme.primary, size: 28),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: theme.textTheme.titleMedium),
                  Text(subtitle, style: theme.textTheme.bodyMedium),
                ],
              ),
            ),
            IconButton.filledTonal(
              onPressed: value > min ? () => onChanged(value - 1) : null,
              icon: const Icon(Icons.remove_rounded),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Text(
                '$value',
                style: theme.textTheme.headlineMedium,
              ),
            ),
            IconButton.filledTonal(
              onPressed: value < max ? () => onChanged(value + 1) : null,
              icon: const Icon(Icons.add_rounded),
            ),
          ],
        ),
      ),
    );
  }
}
