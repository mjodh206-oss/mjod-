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
  static const int _totalSteps = 2;
  final PageController _pageCtrl = PageController();
  int _step = 0;
  bool _saving = false;

  late String _currencyCode;
  String _salaryDigits = '';
  bool _hasExtraIncome = false;
  final TextEditingController _extraCtrl = TextEditingController();
  bool _isRenting = false;
  final TextEditingController _rentCtrl = TextEditingController();
  bool _hasDebt = false;
  final TextEditingController _debtsCtrl = TextEditingController();
  int _familyMembers = 1;
  int _dependents = 0;

  final TextEditingController _utilitiesCtrl = TextEditingController();
  final TextEditingController _transportCtrl = TextEditingController();
  final TextEditingController _telecomCtrl = TextEditingController();
  final TextEditingController _installmentsCtrl = TextEditingController();

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
      _hasDebt = existing.totalDebtAndInstallments > 0;
      if (existing.debtsMonthlyPayment > 0) {
        _debtsCtrl.text = existing.debtsMonthlyPayment.toInt().toString();
      }
      if (existing.installmentsCost > 0) {
        _installmentsCtrl.text = existing.installmentsCost.toInt().toString();
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

  List<double> _quickSalaryPresets() {
    final s = _currency.sampleSalary;
    if (s >= 1000000) {
      return const [500000, 1000000, 1500000, 2000000, 3000000];
    }
    if (s >= 10000) {
      return const [5000, 10000, 15000, 25000, 40000];
    }
    if (s >= 3000) {
      return const [3000, 5000, 8000, 12000, 18000];
    }
    return const [300, 500, 1000, 1500, 2500];
  }

  void _nextStep() {
    FocusScope.of(context).unfocus();
    if (_step == 0) {
      if (_parsedSalary <= 0) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('أدخل راتبك الشهري أولاً لنجهّز خطتك')),
        );
        return;
      }
      setState(() => _step = 1);
      _pageCtrl.animateToPage(
        1,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOutCubic,
      );
    } else {
      if (_isRenting && _parseCtrl(_rentCtrl) <= 0) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('أدخل مبلغ الإيجار الشهري أو اختر بيت ملك')),
        );
        return;
      }
      _submit();
    }
  }

  void _prevStep() {
    FocusScope.of(context).unfocus();
    if (_step > 0) {
      setState(() => _step = 0);
      _pageCtrl.animateToPage(
        0,
        duration: const Duration(milliseconds: 260),
        curve: Curves.easeOutCubic,
      );
    } else if (widget.isEditing) {
      Navigator.of(context).pop();
    }
  }

  Future<void> _instantPlanFromStep1() async {
    FocusScope.of(context).unfocus();
    if (_parsedSalary <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('اكتب راتبك أولاً وسنحسب خطتك فوراً')),
      );
      return;
    }
    await _submit();
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
      installmentsCost: _hasDebt ? _parseCtrl(_installmentsCtrl) : 0,
      debtsMonthlyPayment: _hasDebt ? _parseCtrl(_debtsCtrl) : 0,
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
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 6),
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
                      height: 8,
                      width: double.infinity,
                      color: theme.colorScheme.primary.withValues(alpha: 0.14),
                      child: TweenAnimationBuilder<double>(
                        tween: Tween<double>(begin: 0, end: progress),
                        duration: const Duration(milliseconds: 300),
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

            Expanded(
              child: PageView(
                controller: _pageCtrl,
                physics: const NeverScrollableScrollPhysics(),
                children: [
                  _buildSalaryStep(theme),
                  _buildQuickOptionsStep(theme),
                ],
              ),
            ),

            Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(
                    height: 54,
                    child: FilledButton.icon(
                      onPressed: _saving ? null : _nextStep,
                      icon: _saving
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2.5,
                                color: Colors.white,
                              ),
                            )
                          : Icon(
                              _step == 1
                                  ? Icons.auto_awesome_rounded
                                  : Icons.arrow_back_rounded,
                            ),
                      label: Text(
                        _step == 1
                            ? 'اعرض خطتي الجاهزة الآن ✨'
                            : 'التالي: سؤالين سريعين',
                      ),
                    ),
                  ),
                  if (_step == 0 && !widget.isEditing) ...[
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: _saving ? null : _instantPlanFromStep1,
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.bolt_rounded, size: 19),
                          SizedBox(width: 6),
                          Flexible(
                            child: Text(
                              'احسب خطتي فوراً بدون أسئلة إضافية',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontFamily: 'Cairo',
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSalaryStep(ThemeData theme) {
    final presets = _quickSalaryPresets();

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 12),
      children: [
        Text(
          'كم راتبك الشهري؟',
          style: theme.textTheme.headlineLarge,
        ),
        const SizedBox(height: 4),
        Text(
          'اختر عملتك واضغط على الرقم التقريبي أو اكتبه بالأسفل',
          style: theme.textTheme.bodyMedium,
        ),
        const SizedBox(height: 14),

        // Currency Selector
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
                label: Text('${cur.symbol} · ${cur.nameAr}'),
                selected: selected,
                onSelected: (_) => setState(() => _currencyCode = cur.code),
              );
            },
          ),
        ),

        const SizedBox(height: 14),

        // Big Salary Display Box
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            gradient: const LinearGradient(
              colors: [Color(0xFF063C2B), Color(0xFF0C684A)],
            ),
            border: Border.all(
              color: const Color(0xFFD4AF37).withValues(alpha: 0.6),
              width: 1.5,
            ),
          ),
          child: Column(
            children: [
              Text(
                _parsedSalary > 0
                    ? ArabicFormatters.formatMoney(_parsedSalary, _currency)
                    : '0 ${_currency.symbol}',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontFamily: 'Cairo',
                  fontSize: 30,
                  fontWeight: FontWeight.w800,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                'عملة الخطة: ${_currency.nameAr}',
                style: const TextStyle(
                  fontFamily: 'Cairo',
                  fontSize: 12.5,
                  color: Color(0xFFF5C443),
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 12),

        // Quick preset salary chips
        Wrap(
          spacing: 8,
          runSpacing: 8,
          alignment: WrapAlignment.center,
          children: presets.map((amt) {
            final isSel = _parsedSalary.round() == amt.round();
            return ActionChip(
              avatar: isSel
                  ? const Icon(Icons.check_circle_rounded, size: 17)
                  : null,
              backgroundColor: isSel
                  ? theme.colorScheme.secondary.withValues(alpha: 0.25)
                  : null,
              label: Text(
                ArabicFormatters.formatCompactMoney(amt, _currency),
                style: const TextStyle(
                  fontFamily: 'Cairo',
                  fontWeight: FontWeight.w700,
                ),
              ),
              onPressed: () {
                setState(() => _salaryDigits = amt.toInt().toString());
              },
            );
          }).toList(),
        ),

        const SizedBox(height: 14),

        // Large Numeric Keypad
        _buildNumericKeypad(theme),
      ],
    );
  }

  Widget _buildNumericKeypad(ThemeData theme) {
    final keys = [
      '1', '2', '3',
      '4', '5', '6',
      '7', '8', '9',
      '000', '0', '⌫',
    ];

    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: keys.length,
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        mainAxisSpacing: 8,
        crossAxisSpacing: 8,
        childAspectRatio: 2.35,
      ),
      itemBuilder: (context, idx) {
        final k = keys[idx];
        final isBack = k == '⌫';
        return Material(
          color: isBack
              ? theme.colorScheme.error.withValues(alpha: 0.12)
              : theme.cardColor,
          borderRadius: BorderRadius.circular(14),
          child: InkWell(
            borderRadius: BorderRadius.circular(14),
            onTap: () {
              if (isBack) {
                _backspace();
              } else {
                _appendDigit(k);
              }
            },
            onLongPress: isBack ? () => setState(() => _salaryDigits = '') : null,
            child: Center(
              child: isBack
                  ? Icon(Icons.backspace_rounded, color: theme.colorScheme.error)
                  : Text(
                      k,
                      style: theme.textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                    ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildQuickOptionsStep(ThemeData theme) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 12),
      children: [
        Text(
          'سؤالين سريعين لتظبيط خطتك',
          style: theme.textTheme.headlineLarge,
        ),
        const SizedBox(height: 4),
        Text(
          'أجب بـ "نعم أو لا" لنوزع راتبك بالطريقة الأنسب لك',
          style: theme.textTheme.bodyMedium,
        ),
        const SizedBox(height: 16),

        // Rent Card
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  '1. هل تدفع إيجار بيت؟',
                  style: theme.textTheme.titleMedium,
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: ChoiceChip(
                        label: const Center(child: Text('لا، بيت ملك')),
                        selected: !_isRenting,
                        onSelected: (_) => setState(() => _isRenting = false),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: ChoiceChip(
                        label: const Center(child: Text('نعم، أدفع إيجار')),
                        selected: _isRenting,
                        onSelected: (_) => setState(() => _isRenting = true),
                      ),
                    ),
                  ],
                ),
                if (_isRenting) ...[
                  const SizedBox(height: 12),
                  _numberField(
                    controller: _rentCtrl,
                    label: 'كم قيمة الإيجار الشهري؟',
                    hint: 'مثال: 350000',
                    icon: Icons.apartment_rounded,
                  ),
                ],
              ],
            ),
          ),
        ),

        const SizedBox(height: 12),

        // Debt Card
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  '2. هل عندك أقساط أو ديون شهرية؟',
                  style: theme.textTheme.titleMedium,
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: ChoiceChip(
                        label: const Center(child: Text('لا يوجد')),
                        selected: !_hasDebt,
                        onSelected: (_) => setState(() => _hasDebt = false),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: ChoiceChip(
                        label: const Center(child: Text('نعم، عندي')),
                        selected: _hasDebt,
                        onSelected: (_) => setState(() => _hasDebt = true),
                      ),
                    ),
                  ],
                ),
                if (_hasDebt) ...[
                  const SizedBox(height: 12),
                  _numberField(
                    controller: _debtsCtrl,
                    label: 'كم تدفع للديون/الأقساط بالشهر؟',
                    hint: 'مثال: 150000',
                    icon: Icons.payments_rounded,
                  ),
                ],
              ],
            ),
          ),
        ),

        const SizedBox(height: 12),

        // Main Goal
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  '3. شو هدفك الأهم هذا الشهر؟',
                  style: theme.textTheme.titleMedium,
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: PrimaryFinancialGoal.values.map((g) {
                    final selected = _goal == g;
                    return ChoiceChip(
                      label: Text(g.labelAr),
                      selected: selected,
                      onSelected: (_) => setState(() => _goal = g),
                    );
                  }).toList(),
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 12),

        // Payday picker
        Card(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            child: Row(
              children: [
                Icon(Icons.event_available_rounded,
                    color: theme.colorScheme.primary),
                const SizedBox(width: 10),
                const Expanded(
                  child: Text(
                    'يوم نزول الراتب بالشهر',
                    style: TextStyle(
                      fontFamily: 'Cairo',
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                DropdownButton<int>(
                  value: _salaryDay,
                  underline: const SizedBox.shrink(),
                  items: List.generate(28, (i) => i + 1)
                      .map((d) => DropdownMenuItem(value: d, child: Text('يوم $d')))
                      .toList(),
                  onChanged: (v) {
                    if (v != null) setState(() => _salaryDay = v);
                  },
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 10),

        // Optional advanced details (hidden by default for simplicity!)
        Card(
          child: ExpansionTile(
            leading: const Icon(Icons.tune_rounded),
            title: const Text(
              'إضافة تفاصيل أخرى (اختياري)',
              style: TextStyle(
                fontFamily: 'Cairo',
                fontWeight: FontWeight.w700,
                fontSize: 14,
              ),
            ),
            subtitle: const Text(
              'دخل إضافي، فواتير، عائلة، مدخرات سابقة',
              style: TextStyle(fontFamily: 'Cairo', fontSize: 12),
            ),
            childrenPadding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
            children: [
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('عندي دخل إضافي شهري'),
                value: _hasExtraIncome,
                onChanged: (v) => setState(() => _hasExtraIncome = v),
              ),
              if (_hasExtraIncome) ...[
                _numberField(
                  controller: _extraCtrl,
                  label: 'قيمة الدخل الإضافي',
                  hint: '0',
                  icon: Icons.add_card_rounded,
                ),
                const SizedBox(height: 10),
              ],
              _numberField(
                controller: _utilitiesCtrl,
                label: 'فواتير الكهرباء والماء والغاز',
                hint: '0',
                icon: Icons.bolt_rounded,
              ),
              const SizedBox(height: 10),
              _numberField(
                controller: _transportCtrl,
                label: 'المواصلات الشهرية',
                hint: '0',
                icon: Icons.directions_car_rounded,
              ),
              const SizedBox(height: 10),
              _numberField(
                controller: _telecomCtrl,
                label: 'الإنترنت والاتصالات',
                hint: '0',
                icon: Icons.wifi_rounded,
              ),
              const SizedBox(height: 10),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('عندي مدخرات حالية'),
                value: _hasCurrentSavings,
                onChanged: (v) => setState(() => _hasCurrentSavings = v),
              ),
              if (_hasCurrentSavings)
                _numberField(
                  controller: _savingsCtrl,
                  label: 'قيمة مدخراتك الحالية',
                  hint: '0',
                  icon: Icons.savings_rounded,
                ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _numberField({
    required TextEditingController controller,
    required String label,
    required String hint,
    required IconData icon,
  }) {
    return TextField(
      controller: controller,
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      decoration: InputDecoration(
        labelText: label,
        hintText: hint,
        prefixIcon: Icon(icon),
        suffixText: _currency.symbol,
      ),
    );
  }
}
