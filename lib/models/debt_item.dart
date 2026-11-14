enum DebtStrategy {
  snowball,
  avalanche,
}

class DebtItem {
  const DebtItem({
    required this.id,
    required this.name,
    required this.remainingBalance,
    required this.minimumMonthly,
    this.interestRatePercent = 0,
    this.dueDay = 10,
  });

  final String id;
  final String name;
  final double remainingBalance;
  final double minimumMonthly;
  final double interestRatePercent;
  final int dueDay;

  DebtItem copyWith({
    String? id,
    String? name,
    double? remainingBalance,
    double? minimumMonthly,
    double? interestRatePercent,
    int? dueDay,
  }) {
    return DebtItem(
      id: id ?? this.id,
      name: name ?? this.name,
      remainingBalance: remainingBalance ?? this.remainingBalance,
      minimumMonthly: minimumMonthly ?? this.minimumMonthly,
      interestRatePercent: interestRatePercent ?? this.interestRatePercent,
      dueDay: dueDay ?? this.dueDay,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'remainingBalance': remainingBalance,
        'minimumMonthly': minimumMonthly,
        'interestRatePercent': interestRatePercent,
        'dueDay': dueDay,
      };

  factory DebtItem.fromJson(Map<String, dynamic> json) {
    return DebtItem(
      id: (json['id'] as String?) ?? '',
      name: (json['name'] as String?) ?? 'دين',
      remainingBalance: (json['remainingBalance'] as num?)?.toDouble() ?? 0,
      minimumMonthly: (json['minimumMonthly'] as num?)?.toDouble() ?? 0,
      interestRatePercent:
          (json['interestRatePercent'] as num?)?.toDouble() ?? 0,
      dueDay: ((json['dueDay'] as num?)?.toInt() ?? 10).clamp(1, 31),
    );
  }
}
