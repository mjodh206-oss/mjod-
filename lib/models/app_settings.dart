import '../models/debt_item.dart';

class AppSettings {
  const AppSettings({
    this.hasSeenOnboarding = false,
    this.hasCompletedSetup = false,
    this.isDarkMode = false,
    this.themePaletteKey = 'emerald_gold',
    this.soundEnabled = true,
    this.musicEnabled = false,
    this.pinCode,
    this.biometricEnabled = false,
    this.salaryDayReminder = true,
    this.eveningReminder = true,
    this.weeklyMotivation = true,
    this.billDueReminder = true,
    this.eveningHour = 20,
    this.eveningMinute = 30,
    this.debtStrategy = DebtStrategy.snowball,
    this.completedChallengeDays = const <int>[],
    this.unlockedBadgeIds = const <String>[],
  });

  final bool hasSeenOnboarding;
  final bool hasCompletedSetup;
  final bool isDarkMode;
  final String themePaletteKey;
  final bool soundEnabled;
  final bool musicEnabled;
  final String? pinCode;
  final bool biometricEnabled;
  final bool salaryDayReminder;
  final bool eveningReminder;
  final bool weeklyMotivation;
  final bool billDueReminder;
  final int eveningHour;
  final int eveningMinute;
  final DebtStrategy debtStrategy;
  final List<int> completedChallengeDays;
  final List<String> unlockedBadgeIds;

  bool get isLockEnabled =>
      (pinCode != null && pinCode!.isNotEmpty) || biometricEnabled;

  AppSettings copyWith({
    bool? hasSeenOnboarding,
    bool? hasCompletedSetup,
    bool? isDarkMode,
    String? themePaletteKey,
    bool? soundEnabled,
    bool? musicEnabled,
    String? pinCode,
    bool clearPin = false,
    bool? biometricEnabled,
    bool? salaryDayReminder,
    bool? eveningReminder,
    bool? weeklyMotivation,
    bool? billDueReminder,
    int? eveningHour,
    int? eveningMinute,
    DebtStrategy? debtStrategy,
    List<int>? completedChallengeDays,
    List<String>? unlockedBadgeIds,
  }) {
    return AppSettings(
      hasSeenOnboarding: hasSeenOnboarding ?? this.hasSeenOnboarding,
      hasCompletedSetup: hasCompletedSetup ?? this.hasCompletedSetup,
      isDarkMode: isDarkMode ?? this.isDarkMode,
      themePaletteKey: themePaletteKey ?? this.themePaletteKey,
      soundEnabled: soundEnabled ?? this.soundEnabled,
      musicEnabled: musicEnabled ?? this.musicEnabled,
      pinCode: clearPin ? null : (pinCode ?? this.pinCode),
      biometricEnabled: biometricEnabled ?? this.biometricEnabled,
      salaryDayReminder: salaryDayReminder ?? this.salaryDayReminder,
      eveningReminder: eveningReminder ?? this.eveningReminder,
      weeklyMotivation: weeklyMotivation ?? this.weeklyMotivation,
      billDueReminder: billDueReminder ?? this.billDueReminder,
      eveningHour: eveningHour ?? this.eveningHour,
      eveningMinute: eveningMinute ?? this.eveningMinute,
      debtStrategy: debtStrategy ?? this.debtStrategy,
      completedChallengeDays:
          completedChallengeDays ?? this.completedChallengeDays,
      unlockedBadgeIds: unlockedBadgeIds ?? this.unlockedBadgeIds,
    );
  }

  Map<String, dynamic> toJson() => {
        'hasSeenOnboarding': hasSeenOnboarding,
        'hasCompletedSetup': hasCompletedSetup,
        'isDarkMode': isDarkMode,
        'themePaletteKey': themePaletteKey,
        'soundEnabled': soundEnabled,
        'musicEnabled': musicEnabled,
        'pinCode': pinCode,
        'biometricEnabled': biometricEnabled,
        'salaryDayReminder': salaryDayReminder,
        'eveningReminder': eveningReminder,
        'weeklyMotivation': weeklyMotivation,
        'billDueReminder': billDueReminder,
        'eveningHour': eveningHour,
        'eveningMinute': eveningMinute,
        'debtStrategy': debtStrategy.name,
        'completedChallengeDays': completedChallengeDays,
        'unlockedBadgeIds': unlockedBadgeIds,
      };

  factory AppSettings.fromJson(Map<String, dynamic> json) {
    return AppSettings(
      hasSeenOnboarding: (json['hasSeenOnboarding'] as bool?) ?? false,
      hasCompletedSetup: (json['hasCompletedSetup'] as bool?) ?? false,
      isDarkMode: (json['isDarkMode'] as bool?) ?? false,
      themePaletteKey: (json['themePaletteKey'] as String?) ?? 'emerald_gold',
      soundEnabled: (json['soundEnabled'] as bool?) ?? true,
      musicEnabled: (json['musicEnabled'] as bool?) ?? false,
      pinCode: json['pinCode'] as String?,
      biometricEnabled: (json['biometricEnabled'] as bool?) ?? false,
      salaryDayReminder: (json['salaryDayReminder'] as bool?) ?? true,
      eveningReminder: (json['eveningReminder'] as bool?) ?? true,
      weeklyMotivation: (json['weeklyMotivation'] as bool?) ?? true,
      billDueReminder: (json['billDueReminder'] as bool?) ?? true,
      eveningHour: ((json['eveningHour'] as num?)?.toInt() ?? 20).clamp(0, 23),
      eveningMinute:
          ((json['eveningMinute'] as num?)?.toInt() ?? 30).clamp(0, 59),
      debtStrategy: DebtStrategy.values.firstWhere(
        (d) => d.name == json['debtStrategy'],
        orElse: () => DebtStrategy.snowball,
      ),
      completedChallengeDays: ((json['completedChallengeDays'] as List?) ?? [])
          .map((e) => (e as num).toInt())
          .toList(),
      unlockedBadgeIds: ((json['unlockedBadgeIds'] as List?) ?? [])
          .map((e) => e.toString())
          .toList(),
    );
  }
}
