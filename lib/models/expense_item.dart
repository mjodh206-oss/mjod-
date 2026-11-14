import '../core/constants/categories.dart';

class ExpenseItem {
  const ExpenseItem({
    required this.id,
    required this.amount,
    required this.categoryId,
    required this.bucket,
    required this.note,
    required this.createdAt,
  });

  final String id;
  final double amount;
  final String categoryId;
  final BudgetBucket bucket;
  final String note;
  final DateTime createdAt;

  Map<String, dynamic> toJson() => {
        'id': id,
        'amount': amount,
        'categoryId': categoryId,
        'bucket': bucket.name,
        'note': note,
        'createdAt': createdAt.toIso8601String(),
      };

  factory ExpenseItem.fromJson(Map<String, dynamic> json) {
    final categoryId = (json['categoryId'] as String?) ?? 'food';
    final cat = AppCategories.categoryById(categoryId);
    return ExpenseItem(
      id: (json['id'] as String?) ?? '',
      amount: (json['amount'] as num?)?.toDouble() ?? 0,
      categoryId: categoryId,
      bucket: BudgetBucket.values.firstWhere(
        (b) => b.name == json['bucket'],
        orElse: () => cat.bucket,
      ),
      note: (json['note'] as String?) ?? '',
      createdAt: DateTime.tryParse((json['createdAt'] as String?) ?? '') ??
          DateTime.now(),
    );
  }
}
