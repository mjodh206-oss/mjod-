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
      titleAr: 'ضروريات',
      subtitleAr: 'السكن، الأكل، الفواتير، المواصلات',
      color: Color(0xFF0E9F6E),
      icon: Icons.home_rounded,
      ruleDescriptionAr:
          'القاعدة الذهبية توصي بألا تتجاوز الضروريات 50% إلى 55% من دخلك الشهري حتى يبقى لك هامش للادخار والراحة.',
    ),
    BucketMeta(
      bucket: BudgetBucket.wants,
      titleAr: 'احتياجات مرنة',
      subtitleAr: 'خروجات، هدايا، تسوق، ترفيه',
      color: Color(0xFFF59E0B),
      icon: Icons.shopping_bag_rounded,
      ruleDescriptionAr:
          'قاعدة 50/30/20 تخصص حتى 30% للرغبات والرفاهية، ويمكن تقليصها مؤقتاً لتسريع الادخار أو سداد الديون.',
    ),
    BucketMeta(
      bucket: BudgetBucket.savings,
      titleAr: 'ادخار',
      subtitleAr: 'استثمار، أهداف مستقبلية، شراء',
      color: Color(0xFF0EA5E9),
      icon: Icons.savings_rounded,
      ruleDescriptionAr:
          'ادفع لنفسك أولاً! تخصيص 10% على الأقل للادخار يصنع فارقاً ضخماً خلال سنة واحدة.',
    ),
    BucketMeta(
      bucket: BudgetBucket.debts,
      titleAr: 'ديون وأقساط',
      subtitleAr: 'سداد قروض، أقساط شهرية، ذمم',
      color: Color(0xFFEF4444),
      icon: Icons.credit_score_rounded,
      ruleDescriptionAr:
          'سداد الديون أولاً يرفع صحتك المالية ويحرّر راتبك. جرب طريقة كرة الثلج أو الانهيار الجليدي.',
    ),
    BucketMeta(
      bucket: BudgetBucket.emergency,
      titleAr: 'طوارئ',
      subtitleAr: 'صندوق أمان للأزمات والمفاجآت',
      color: Color(0xFFD4AF37),
      icon: Icons.shield_rounded,
      ruleDescriptionAr:
          'صندوق الطوارئ المثالي يغطي 3 إلى 6 أشهر من مصاريفك الأساسية ليحميك من أي ظرف طارئ.',
    ),
  ];

  static BucketMeta metaFor(BudgetBucket bucket) {
    return buckets.firstWhere((b) => b.bucket == bucket);
  }

  static const List<ExpenseCategory> expenseCategories = [
    ExpenseCategory(
      id: 'food',
      nameAr: 'طعام وبقالة',
      icon: Icons.restaurant_rounded,
      color: Color(0xFF10B981),
      bucket: BudgetBucket.necessities,
    ),
    ExpenseCategory(
      id: 'housing',
      nameAr: 'سكن وإيجار',
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
      nameAr: 'مواصلات وبنزين',
      icon: Icons.directions_car_rounded,
      color: Color(0xFF0D9488),
      bucket: BudgetBucket.necessities,
    ),
    ExpenseCategory(
      id: 'telecom',
      nameAr: 'اتصالات وإنترنت',
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
      nameAr: 'تسوق وترفيه',
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
      nameAr: 'قسط أو دين',
      icon: Icons.payments_rounded,
      color: Color(0xFFEF4444),
      bucket: BudgetBucket.debts,
    ),
    ExpenseCategory(
      id: 'emergency_save',
      nameAr: 'صندوق الطوارئ',
      icon: Icons.shield_rounded,
      color: Color(0xFFD4AF37),
      bucket: BudgetBucket.emergency,
    ),
  ];

  static ExpenseCategory categoryById(String id) {
    return expenseCategories.firstWhere(
      (c) => c.id == id,
      orElse: () => expenseCategories.first,
    );
  }
}
