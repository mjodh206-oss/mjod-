import 'package:flutter/material.dart';

enum BudgetBucket {
  necessities,
  wants,
  savings,
  debts,
  emergency,
}

class BucketMeta {
  const BucketMeta({
    required this.bucket,
    required this.titleAr,
    required this.subtitleAr,
    required this.color,
    required this.icon,
    required this.ruleDescriptionAr,
  });

  final BudgetBucket bucket;
  final String titleAr;
  final String subtitleAr;
  final Color color;
  final IconData icon;
  final String ruleDescriptionAr;
}

class ExpenseCategory {
  const ExpenseCategory({
    required this.id,
    required this.nameAr,
    required this.icon,
    required this.color,
    required this.bucket,
  });

  final String id;
  final String nameAr;
  final IconData icon;
  final Color color;
  final BudgetBucket bucket;
}

class AppCategories {
  AppCategories._();

  static const List<BucketMeta> buckets = [
    BucketMeta(
      bucket: BudgetBucket.necessities,
      titleAr: 'البيت والأساسيات',
      subtitleAr: 'الإيجار، الأكل، الفواتير والمواصلات',
      color: Color(0xFF0E9F6E),
      icon: Icons.home_rounded,
      ruleDescriptionAr:
          'هذا المبلغ مخصص للأساسيات اللي ما بتستغني عنها (سكن، أكل، كهرباء، مواصلات). يفضّل ما يتعدى 50% إلى 55% من راتبك.',
    ),
    BucketMeta(
      bucket: BudgetBucket.wants,
      titleAr: 'مصروفك الشخصي',
      subtitleAr: 'الطلبات، الطلعات، المشتريات والترفيه',
      color: Color(0xFFF59E0B),
      icon: Icons.shopping_bag_rounded,
      ruleDescriptionAr:
          'مبلغ تصرفه على نفسك وعائلتك بالراحة بدون ما يأثر على البيت أو التوفير. لو احتجت توفّر أكثر، هذا أول بند تقدر تخفف منه.',
    ),
    BucketMeta(
      bucket: BudgetBucket.savings,
      titleAr: 'التوفير والادخار',
      subtitleAr: 'مبلغ تحجزه لأهدافك ومستقبلك',
      color: Color(0xFF0EA5E9),
      icon: Icons.savings_rounded,
      ruleDescriptionAr:
          'أفضل عادة مالية: خذ مبلغ التوفير أول ما ينزل راتبك وحطه على جنب قبل ما تبدأ الصرف.',
    ),
    BucketMeta(
      bucket: BudgetBucket.debts,
      titleAr: 'الديون والأقساط',
      subtitleAr: 'سداد القروض والأقساط الشهرية',
      color: Color(0xFFEF4444),
      icon: Icons.credit_score_rounded,
      ruleDescriptionAr:
          'كل ما خلّصت دين أسرع، كل ما تحرّر راتبك وزاد المبلغ اللي بيبقى معك آخر الشهر.',
    ),
    BucketMeta(
      bucket: BudgetBucket.emergency,
      titleAr: 'صندوق الطوارئ',
      subtitleAr: 'احتياطي أمان لأي ظرف مفاجئ',
      color: Color(0xFFD4AF37),
      icon: Icons.shield_rounded,
      ruleDescriptionAr:
          'مبلغ احتياطي بتحفظه للطوارئ (مرض، تصليح مفاجئ، أو أي ظرف) حتى ما تضطر تستدين.',
    ),
  ];

  static BucketMeta metaFor(BudgetBucket bucket) {
    return buckets.firstWhere((b) => b.bucket == bucket);
  }

  static const List<ExpenseCategory> expenseCategories = [
    ExpenseCategory(
      id: 'food',
      nameAr: 'أكل وبقالة',
      icon: Icons.restaurant_rounded,
      color: Color(0xFF10B981),
      bucket: BudgetBucket.necessities,
    ),
    ExpenseCategory(
      id: 'housing',
      nameAr: 'إيجار وسكن',
      icon: Icons.apartment_rounded,
      color: Color(0xFF059669),
      bucket: BudgetBucket.necessities,
    ),
    ExpenseCategory(
      id: 'bills',
      nameAr: 'فواتير وكهرباء',
      icon: Icons.receipt_long_rounded,
      color: Color(0xFF14B8A6),
      bucket: BudgetBucket.necessities,
    ),
    ExpenseCategory(
      id: 'transport',
      nameAr: 'مواصلات',
      icon: Icons.directions_car_rounded,
      color: Color(0xFF0D9488),
      bucket: BudgetBucket.necessities,
    ),
    ExpenseCategory(
      id: 'telecom',
      nameAr: 'نت وموبايل',
      icon: Icons.wifi_rounded,
      color: Color(0xFF0891B2),
      bucket: BudgetBucket.necessities,
    ),
    ExpenseCategory(
      id: 'health',
      nameAr: 'صحة وعلاج',
      icon: Icons.medical_services_rounded,
      color: Color(0xFF22C55E),
      bucket: BudgetBucket.necessities,
    ),
    ExpenseCategory(
      id: 'shopping',
      nameAr: 'شخصي وطلعات',
      icon: Icons.shopping_bag_rounded,
      color: Color(0xFFF59E0B),
      bucket: BudgetBucket.wants,
    ),
    ExpenseCategory(
      id: 'family',
      nameAr: 'عائلة وهدايا',
      icon: Icons.family_restroom_rounded,
      color: Color(0xFFFB923C),
      bucket: BudgetBucket.wants,
    ),
    ExpenseCategory(
      id: 'debt',
      nameAr: 'دين أو قسط',
      icon: Icons.payments_rounded,
      color: Color(0xFFEF4444),
      bucket: BudgetBucket.debts,
    ),
    ExpenseCategory(
      id: 'savings_deposit',
      nameAr: 'تحويل للتوفير',
      icon: Icons.savings_rounded,
      color: Color(0xFF0EA5E9),
      bucket: BudgetBucket.savings,
    ),
  ];

  static ExpenseCategory categoryById(String id) {
    return expenseCategories.firstWhere(
      (c) => c.id == id,
      orElse: () => expenseCategories.first,
    );
  }
}
