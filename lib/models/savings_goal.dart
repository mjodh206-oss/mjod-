class SavingsGoal {
  const SavingsGoal({
    required this.id,
    required this.title,
    required this.iconKey,
    required this.targetAmount,
    required this.savedAmount,
    required this.durationMonths,
    required this.createdAt,
    this.celebrated = false,
  });

  final String id;
  final String title;
  final String iconKey;
  final double targetAmount;
  final double savedAmount;
  final int durationMonths;
  final DateTime createdAt;
  final bool celebrated;

  double get remainingAmount =>
      (targetAmount - savedAmount).clamp(0, double.infinity);

  double get monthlyRequired {
    if (durationMonths <= 0) return remainingAmount;
    return remainingAmount / durationMonths;
  }

  double get progress {
    if (targetAmount <= 0) return 0;
    return (savedAmount / targetAmount).clamp(0.0, 1.0);
  }

  bool get isCompleted => targetAmount > 0 && savedAmount >= targetAmount;

  SavingsGoal copyWith({
    String? id,
    String? title,
    String? iconKey,
    double? targetAmount,
    double? savedAmount,
    int? durationMonths,
    DateTime? createdAt,
    bool? celebrated,
  }) {
    return SavingsGoal(
      id: id ?? this.id,
      title: title ?? this.title,
      iconKey: iconKey ?? this.iconKey,
      targetAmount: targetAmount ?? this.targetAmount,
      savedAmount: savedAmount ?? this.savedAmount,
      durationMonths: durationMonths ?? this.durationMonths,
      createdAt: createdAt ?? this.createdAt,
      celebrated: celebrated ?? this.celebrated,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'iconKey': iconKey,
        'targetAmount': targetAmount,
        'savedAmount': savedAmount,
        'durationMonths': durationMonths,
        'createdAt': createdAt.toIso8601String(),
        'celebrated': celebrated,
      };

  factory SavingsGoal.fromJson(Map<String, dynamic> json) {
    return SavingsGoal(
      id: (json['id'] as String?) ?? '',
      title: (json['title'] as String?) ?? '',
      iconKey: (json['iconKey'] as String?) ?? 'target',
      targetAmount: (json['targetAmount'] as num?)?.toDouble() ?? 0,
      savedAmount: (json['savedAmount'] as num?)?.toDouble() ?? 0,
      durationMonths: (json['durationMonths'] as num?)?.toInt() ?? 12,
      createdAt: DateTime.tryParse((json['createdAt'] as String?) ?? '') ??
          DateTime.now(),
      celebrated: (json['celebrated'] as bool?) ?? false,
    );
  }
}
