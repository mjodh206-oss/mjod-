import 'package:flutter/material.dart';

import '../../core/utils/page_transitions.dart';
import '../../state/app_controller.dart';
import '../../widgets/rattib_logo.dart';
import '../home/main_shell.dart';

class LockScreen extends StatefulWidget {
  const LockScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<LockScreen> createState() => _LockScreenState();
}

class _LockScreenState extends State<LockScreen> {
  String _enteredPin = '';
  String? _errorMsg;
  bool _checkingBio = false;

  @override
  void initState() {
    super.initState();
    if (widget.controller.settings.biometricEnabled) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _tryBiometric());
    }
  }

  Future<void> _tryBiometric() async {
    if (_checkingBio) return;
    setState(() {
      _checkingBio = true;
      _errorMsg = null;
    });
    final ok = await widget.controller.security.authenticateWithBiometrics();
    if (!mounted) return;
    setState(() => _checkingBio = false);
    if (ok) {
      _unlockAndGo();
    } else if ((widget.controller.settings.pinCode ?? '').isEmpty) {
      setState(() => _errorMsg = 'اضغط على زر البصمة للمحاولة مجدداً');
    }
  }

  void _unlockAndGo() {
    widget.controller.unlockApp();
    Navigator.of(context).pushReplacement(
      FadePageRoute(builder: (_) => MainShell(controller: widget.controller)),
    );
  }

  void _pressDigit(String digit) {
    final targetPin = widget.controller.settings.pinCode;
    if (targetPin == null || targetPin.isEmpty) return;
    if (_enteredPin.length >= 4) return;

    setState(() {
      _errorMsg = null;
      _enteredPin += digit;
    });

    if (_enteredPin.length == 4) {
      if (_enteredPin == targetPin) {
        _unlockAndGo();
      } else {
        setState(() {
          _errorMsg = 'الرمز غير صحيح، حاول مرة أخرى';
          _enteredPin = '';
        });
      }
    }
  }

  void _backspace() {
    if (_enteredPin.isEmpty) return;
    setState(() {
      _enteredPin = _enteredPin.substring(0, _enteredPin.length - 1);
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasPin = (widget.controller.settings.pinCode ?? '').isNotEmpty;
    final hasBio = widget.controller.settings.biometricEnabled;

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const RattibLogo(size: 86),
              const SizedBox(height: 18),
              Text(
                'تطبيق رَتِّبْ مقفل للخصوصية',
                style: theme.textTheme.headlineMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 6),
              Text(
                hasPin ? 'أدخل رمز الحماية (4 أرقام) أو استخدم البصمة' : 'افتح بالبصمة',
                style: theme.textTheme.bodyMedium,
              ),
              if (_errorMsg != null) ...[
                const SizedBox(height: 10),
                Text(
                  _errorMsg!,
                  style: const TextStyle(
                    fontFamily: 'Cairo',
                    color: Color(0xFFEF4444),
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
              if (hasPin) ...[
                const SizedBox(height: 24),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: List.generate(4, (i) {
                    final filled = i < _enteredPin.length;
                    return AnimatedContainer(
                      duration: const Duration(milliseconds: 160),
                      margin: const EdgeInsets.symmetric(horizontal: 10),
                      width: 18,
                      height: 18,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: filled
                            ? theme.colorScheme.secondary
                            : Colors.transparent,
                        border: Border.all(
                          color: theme.colorScheme.primary,
                          width: 2,
                        ),
                      ),
                    );
                  }),
                ),
                const SizedBox(height: 24),
                Directionality(
                  textDirection: TextDirection.ltr,
                  child: SizedBox(
                    width: 280,
                    child: GridView.count(
                      crossAxisCount: 3,
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      mainAxisSpacing: 12,
                      crossAxisSpacing: 12,
                      childAspectRatio: 1.35,
                      children: [
                        ...['1', '2', '3', '4', '5', '6', '7', '8', '9'].map(
                          (d) => OutlinedButton(
                            onPressed: () => _pressDigit(d),
                            child: Text(
                              d,
                              style: const TextStyle(
                                fontFamily: 'Cairo',
                                fontSize: 22,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                        ),
                        hasBio
                            ? OutlinedButton(
                                onPressed: _tryBiometric,
                                child: const Icon(Icons.fingerprint_rounded, size: 28),
                              )
                            : const SizedBox.shrink(),
                        OutlinedButton(
                          onPressed: () => _pressDigit('0'),
                          child: const Text(
                            '0',
                            style: TextStyle(
                              fontFamily: 'Cairo',
                              fontSize: 22,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                        OutlinedButton(
                          onPressed: _backspace,
                          child: const Icon(Icons.backspace_rounded),
                        ),
                      ],
                    ),
                  ),
                ),
              ] else if (hasBio) ...[
                const SizedBox(height: 36),
                FilledButton.icon(
                  onPressed: _tryBiometric,
                  icon: const Icon(Icons.fingerprint_rounded, size: 28),
                  label: const Text('فتح بالبصمة'),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
