import 'package:flutter/material.dart';

class ChallengeDay {
  const ChallengeDay({
    required this.day,
    required this.titleAr,
    required this.actionAr,
    required this.estimatedSavePercent,
  });

  final int day;
  final String titleAr;
  final String actionAr;
  final double estimatedSavePercent;
}

class BadgeDefinition {
  const BadgeDefinition({
    required this.id,
    required this.titleAr,
    required this.descriptionAr,
    required this.icon,
    required this.color,
  });

  final String id;
  final String titleAr;
  final String descriptionAr;
  final IconData icon;
  final Color color;
}

class FinancialTipsData {
  FinancialTipsData._();

  static const List<String> dailyTips = [
    'قاعدة الـ 24 ساعة: قبل شراء أي غرض غير ضروري، انتظر يوماً كاملاً؛ ستكتشف أن 70% من الرغبات اللحظية تزول.',
    'حوّل مبلغ الادخار فوراً في يوم استلام الراتب قبل أن تبدأ بالصرف، وليس ما يتبقى آخر الشهر.',
    'مراجعة الاشتراكات الشهرية (إنترنت، تطبيقات، باقات) توفر عادة 5% إلى 8% من المصاريف دون أي تأثير على حياتك.',
    'التسوق بقائمة مكتوبة وبعد تناول وجبة يقلل فواتير البقالة بنسبة تصل إلى 25%.',
    'صندوق الطوارئ هو درعك ضد الديون الجديدة؛ اجعل هدفك الأول تغطية شهر كامل من الأساسيات ثم 3 أشهر.',
    'إذا زاد دخلك الإضافي هذا الشهر، خصّص 70% منه للادخار أو سداد دين، و30% فقط كمكافأة لنفسك.',
    'تتبّع المصاريف الصغيرة اليومية (القهوة، التوصيل، السناكس)؛ تراكمها الشهري يفوق أحياناً قسطاً كاملاً.',
    'عند وجود عدة ديون، ركّز كل فائض الشهر على دين واحد فقط مع دفع الحد الأدنى للباقي لتشعر بالإنجاز سريعاً.',
    'قسّم مصروف الأسبوع في محفظة أو حساب فرعي منفصل؛ رؤية الحد الأسبوعي تمنع استنزاف الراتب مبكراً.',
    'تحضير وجبة أو مشروب في البيت 3 أيام أسبوعياً يوفّر مبلغاً كافياً لهدفك السنوي.',
    'قارن سعر المنتج بعدد ساعات عملك لتقيس قيمته الحقيقية قبل الدفع.',
    'لا تقترض لتغطية رغبة استهلاكية؛ اجعل القرض أو القسط فقط لما يرفع دخلك أو يغطي ضرورة قصوى.',
    'اجعل 5 دقائق كل مساء في رَتِّبْ لتسجيل مصاريف اليوم؛ الاستمرارية تكشف أين يضيع الراتب.',
    'الالتزام بميزانية الأساسيات دون تجاوز يرفع مؤشر صحة راتبك ويمنحك راحة نفسية طوال الشهر.',
    'احتفل بإنجازاتك الصغيرة! كل هدف تدخر له يقربك من الحرية المالية.',
    'استخدم قاعدة 50/30/20 كمرجع مرن: إذا ارتفع الإيجار، خفّض بند الاحتياجات المرنة مؤقتاً لتوازن الخطة.',
    'فواتير الكهرباء والماء تنخفض بـ 15% بخطوات بسيطة مثل إطفاء الأجهزة غير المستخدمة وتوقيت السخان.',
    'اشترِ بالجملة المواد غير القابلة للتلف التي تستهلكها شهرياً بانتظام لتخفيض التكلفة للوحدة.',
    'احتفظ بـ 10% من أي مبلغ أو هدية نقدية غير متوقعة داخل صندوق الطوارئ مباشرة.',
    'وضع اسم واضح لهدفك (مثل: سيارة العائلة أو سفرة الصيف) يضاعف التزامك بالادخار ثلاث مرات.',
    'تجنّب الشراء تحت ضغط العروض المؤقتة إلا إذا كان الغرض في قائمتك المسبقة أصلاً.',
    'استعمل المواصلات الأقل كلفة مرتين أسبوعياً ووجّه الفرق مباشرة لهدفك في التطبيق.',
    'كل دين تغلقه اليوم يعني زيادة فورية في راتبك الصافي للأشهر القادمة للأبد.',
    'راقب نسبة السكن: عندما يتجاوز الإيجار 35%-40% من الدخل، ابحث عن بدائل أو مصدر دخل مساند.',
    'خصص ميزانية ثابتة للهدايا والمناسبات شهرياً حتى لا تفاجئك وتكسر خطة الشهر.',
    'الادخار لا يعني الحرمان؛ يعني أن تختار بوعي أين تذهب نقودك لتخدم ما يهمك فعلاً.',
    'قارن أسعار التأمين والإنترنت سنوياً، فالتفاوض أو تغيير الباقة يوفّر مبلغاً جيداً.',
    'اصنع تحدي «يوم بلا صرف» مرة في الأسبوع واجمع ما وفرته في حصالة الهدف.',
    'راجع خطتك في رَتِّبْ منتصف كل شهر وعدّل المنزلقات حسب أولوياتك الجديدة.',
    'أنت تستطيع السيطرة على راتبك مهما كان حجمه؛ التخطيط هو الفارق الحقيقي.',
  ];

  static String tipOfToday([DateTime? now]) {
    final d = now ?? DateTime.now();
    final dayOfYear = d.difference(DateTime(d.year, 1, 1)).inDays;
    return dailyTips[dayOfYear % dailyTips.length];
  }

  static const List<ChallengeDay> thirtyDayChallenge = [
    ChallengeDay(day: 1, titleAr: 'يوم الجرد الأول', actionAr: 'سجّل كل مصروفاتك اليوم مهما كانت صغيرة.', estimatedSavePercent: 0.5),
    ChallengeDay(day: 2, titleAr: 'يوم بلا توصيل', actionAr: 'تجنّب طلب الطعام بالتوصيل وحضّر وجبتك في البيت.', estimatedSavePercent: 1.0),
    ChallengeDay(day: 3, titleAr: 'مراجعة الاشتراكات', actionAr: 'ألغِ أي اشتراك أو باقة إنترنت/تطبيقات لا تستخدمها.', estimatedSavePercent: 1.5),
    ChallengeDay(day: 4, titleAr: 'قهوة البيت', actionAr: 'حضّر قهوتك أو مشروبك في البيت قبل الخروج.', estimatedSavePercent: 0.7),
    ChallengeDay(day: 5, titleAr: 'قاعدة القائمة', actionAr: 'لا تدخل أي سوق أو بقالة اليوم بدون قائمة مشتريات محددة.', estimatedSavePercent: 1.2),
    ChallengeDay(day: 6, titleAr: 'يوم بلا صرف', actionAr: 'جرّب يوماً كاملاً بدون إنفاق أي مبلغ غير ضروري.', estimatedSavePercent: 1.8),
    ChallengeDay(day: 7, titleAr: 'حصاد الأسبوع الأول', actionAr: 'احسب ما وفرته هذا الأسبوع وحوّله لهدفك في رَتِّبْ.', estimatedSavePercent: 1.0),
    ChallengeDay(day: 8, titleAr: 'توفير الكهرباء', actionAr: 'افصل الشواحن والأجهزة غير المستخدمة واضبط الإضاءة.', estimatedSavePercent: 0.8),
    ChallengeDay(day: 9, titleAr: 'بديل المواصلات', actionAr: 'استخدم المشي أو النقل الأوفر لمشوار واحد اليوم.', estimatedSavePercent: 0.9),
    ChallengeDay(day: 10, titleAr: 'قاعدة 24 ساعة', actionAr: 'أجّل أي شراء غير طارئ اليوم لمدة 24 ساعة كاملة.', estimatedSavePercent: 1.5),
    ChallengeDay(day: 11, titleAr: 'وجبة من الموجود', actionAr: 'ابتكر وجبة لذيذة من مكونات مطبخك الحالية دون شراء.', estimatedSavePercent: 1.2),
    ChallengeDay(day: 12, titleAr: 'إلغاء الإغراءات', actionAr: 'كتم إشعارات متاجر التسوق وتطبيقات العروض.', estimatedSavePercent: 1.0),
    ChallengeDay(day: 13, titleAr: 'مقارنة الأسعار', actionAr: 'قارن سعر 3 منتجات أساسية قبل شرائها من مكان أرخص.', estimatedSavePercent: 0.9),
    ChallengeDay(day: 14, titleAr: 'مراجعة منتصف التحدي', actionAr: 'تفقّد رسم المصاريف مقابل الخطة وعدّل أي بند زاد.', estimatedSavePercent: 1.0),
    ChallengeDay(day: 15, titleAr: 'نصف شهر نظيف', actionAr: 'يوم ثانٍ بلا صرف كماليات؛ كافئ نفسك بمتعة مجانية.', estimatedSavePercent: 1.6),
    ChallengeDay(day: 16, titleAr: 'صيانة وقائية', actionAr: 'تفقّد تسريبات المياه أو ضغط إطارات السيارة لتوفير الهدر.', estimatedSavePercent: 1.0),
    ChallengeDay(day: 17, titleAr: 'بيع غرض مركون', actionAr: 'ابحث عن غرض لا تحتاجه في البيت يمكن بيعه أو الاستفادة منه.', estimatedSavePercent: 2.0),
    ChallengeDay(day: 18, titleAr: 'غداء العمل المنزلي', actionAr: 'خذ غداءك وسناك خفيف معك للعمل أو الجامعة.', estimatedSavePercent: 1.2),
    ChallengeDay(day: 19, titleAr: 'مفاوضة ذكية', actionAr: 'اسأل عن باقة اتصالات أو مزود أرخص يناسب استهلاكك الفعلي.', estimatedSavePercent: 1.3),
    ChallengeDay(day: 20, titleAr: 'تدقيق الفكة', actionAr: 'اجمع النقود المعدنية أو الفكة اليومية في صندوق الطوارئ.', estimatedSavePercent: 0.8),
    ChallengeDay(day: 21, titleAr: 'إنجاز 3 أسابيع', actionAr: 'راجع قائمة ديونك واختر طريقة السداد (كرة الثلج أو الانهيار).', estimatedSavePercent: 1.0),
    ChallengeDay(day: 22, titleAr: 'بدائل مجانية', actionAr: 'اختر نزهة أو نشاط عائلي مجاني وممتع بدلاً من المولات.', estimatedSavePercent: 1.5),
    ChallengeDay(day: 23, titleAr: 'شراء الجملة الذكي', actionAr: 'اشترِ السلع الأساسية طويلة الأجل بسعر أقل للقطعة.', estimatedSavePercent: 1.1),
    ChallengeDay(day: 24, titleAr: 'تحكم بالبطاقات', actionAr: 'ادفع نقداً اليوم لتحس بقيمة كل ورقة تخرج من محفظتك.', estimatedSavePercent: 1.2),
    ChallengeDay(day: 25, titleAr: 'مراجعة الفواتير', actionAr: 'دقّق فواتير الشهر بحثاً عن أي رسوم خفية أو زائدة.', estimatedSavePercent: 1.0),
    ChallengeDay(day: 26, titleAr: 'يوم بلا مطاعم', actionAr: 'اجعل كل وجبات اليوم منزلية وصحية وموفرة.', estimatedSavePercent: 1.4),
    ChallengeDay(day: 27, titleAr: 'تحديث الأهداف', actionAr: 'زِد مساهمتك في أحد أهدافك بما وفرته خلال الأيام السابقة.', estimatedSavePercent: 1.0),
    ChallengeDay(day: 28, titleAr: 'خطة الشهر القادم', actionAr: 'جهّز أولوياتك قبل نزول الراتب القادم.', estimatedSavePercent: 1.0),
    ChallengeDay(day: 29, titleAr: 'يوم التحدي الأخير', actionAr: 'يوم كامل بلا أي مصروف غير أساسي!', estimatedSavePercent: 1.8),
    ChallengeDay(day: 30, titleAr: 'تتويج البطل 🎉', actionAr: 'احسب إجمالي ما وفرته في 30 يوماً واحتفل بالنتيجة!', estimatedSavePercent: 2.0),
  ];

  static const List<BadgeDefinition> badges = [
    BadgeDefinition(
      id: 'first_plan',
      titleAr: 'المخطط الذكي',
      descriptionAr: 'أنشأت أول خطة راتب ذكية خاصة بك.',
      icon: Icons.pie_chart_rounded,
      color: Color(0xFF10B981),
    ),
    BadgeDefinition(
      id: 'first_saving_100k',
      titleAr: 'أول خطوة ادخار',
      descriptionAr: 'وصلت مدخراتك الإجمالية إلى أول 100 ألف أو حققت هدف الادخار الأول.',
      icon: Icons.savings_rounded,
      color: Color(0xFFD4AF37),
    ),
    BadgeDefinition(
      id: 'seven_days_streak',
      titleAr: '7 أيام التزام',
      descriptionAr: 'تابعت وسجّلت مصاريفك والتزمت بالتحدي لمدة 7 أيام.',
      icon: Icons.local_fire_department_rounded,
      color: Color(0xFFF59E0B),
    ),
    BadgeDefinition(
      id: 'challenge_30_master',
      titleAr: 'بطل تحدي الـ 30 يوم',
      descriptionAr: 'أتممت خطة تحدي الـ 30 يوم لتقليل المصاريف بالكامل.',
      icon: Icons.emoji_events_rounded,
      color: Color(0xFFEAB308),
    ),
    BadgeDefinition(
      id: 'goal_achiever',
      titleAr: 'صائد الأهداف',
      descriptionAr: 'وصلت لـ 100% من أحد أهدافك المالية.',
      icon: Icons.flag_rounded,
      color: Color(0xFF0EA5E9),
    ),
    BadgeDefinition(
      id: 'debt_warrior',
      titleAr: 'محارب الديون',
      descriptionAr: 'أضفت خطة سداد الديون وبدأت تقليصها بانتظام.',
      icon: Icons.shield_rounded,
      color: Color(0xFFEF4444),
    ),
    BadgeDefinition(
      id: 'healthy_salary',
      titleAr: 'راتب صحي ممتاز',
      descriptionAr: 'وصل مؤشر صحة راتبك إلى 80 من 100 أو أكثر.',
      icon: Icons.favorite_rounded,
      color: Color(0xFF14B8A6),
    ),
  ];
}
