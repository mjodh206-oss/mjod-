package com.amjad.mahloula.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * الأكاديمية المالية في مَحلولة: دروس وقواعد عالمية مختارة من أفضل المصادر
 * (هارفارد بزنس ريفيو، جمعية المستشارين الماليين NFCC، منهج 50/30/20، ديف رامزي،
 * سلوكيات الاقتصاد السلوكي لـ Thaler وسولر وغيرهم) — بصياغة عربية عملية.
 */
public final class Content {

    private Content() {}

    public static final class Lesson {
        public final String titleAr, titleEn;
        public final String icon;      // اسم أيقونة
        public final String introAr, introEn;
        public final List<String> tipsAr, tipsEn;
        public final String actionAr, actionEn;
        public final String sourceAr, sourceEn;

        Lesson(String tA, String tE, String icon, String iA, String iE,
               String[] tipsA, String[] tipsE, String aA, String aE, String sA, String sE) {
            this.titleAr = tA; this.titleEn = tE; this.icon = icon;
            this.introAr = iA; this.introEn = iE;
            this.tipsAr = Arrays.asList(tipsA); this.tipsEn = Arrays.asList(tipsE);
            this.actionAr = aA; this.actionEn = aE;
            this.sourceAr = sA; this.sourceEn = sE;
        }
    }

    public static final List<Lesson> LESSONS = new ArrayList<>();

    static {
        LESSONS.add(new Lesson(
                "لماذا يتسرّب المال؟ تتبّع كل درهم",
                "Where does money leak? Track everything",
                "search",
                "أغلب الناس لا يعرفون أين يذهب راتبهم بعد أسبوعين من نزوله. الدراسات السلوكية تُظهر أننا نستهين بإنفاقنا الصغير بنسبة تصل إلى 30٪. أول خطوة في أي نظام مالي ناجح حول العالم هي الوعي: سجّل كل عملية، مهما صغرت، وخلال 30 يومًا ستكتشف «ثقوبًا» لم تكن تراها.",
                "Most people can't say where their salary went two weeks after payday. Behavioral studies show we underestimate small spending by up to 30%. The first step of every successful money system worldwide is awareness: log every transaction, however small, and within 30 days you'll see leaks you never noticed.",
                new String[]{
                        "سجّل المصروف لحظة حدوثه — لا تعتمد على الذاكرة.",
                        "صنّف كل عملية: طعام، مواصلات، فواتير… التصنيف هو ما يكشف النمط.",
                        "راجع نهاية كل أسبوع: أين ذهب أكبر مبلغ؟ وما الذي كان يمكن الاستغناء عنه؟",
                        "لا تحاكم نفسك في أول شهر؛ الهدف ملاحظة لا عقاب."
                },
                new String[]{
                        "Log spending the moment it happens — never rely on memory.",
                        "Categorize every transaction; categories reveal patterns.",
                        "Review weekly: what was the biggest outflow? what was avoidable?",
                        "Don't judge yourself in month one; the goal is observation, not punishment."
                },
                "سجّل الآن آخر 3 مصاريف تتذكرها في خانة المسحوبات.",
                "Log your last 3 remembered expenses in the Withdrawals tab now.",
                "مبدأ الوعي المالي — Economics of tracking (Thaler & Sunstein, Nudge)",
                "Tracking awareness — Thaler & Sunstein, Nudge"));

        LESSONS.add(new Lesson(
                "قاعدة 50/30/20: الميزانية التي تحتمل الحياة",
                "The 50/30/20 budget that survives real life",
                "pie",
                "أشهر قاعدة ميزانية في العالم، روّجت لها السيناتورة الأمريكية إليزابيث وارن في كتابها All Your Worth: نصف دخلك للاحتياجات (سكن، طعام، مواصلات، فواتير)، 30٪ للرغبات (ترفيه، تسوق، مطاعم)، و20٪ للادخار وسداد الديون. بساطتها سر قوتها: لا تقتل متعتك ولا تضيّع مستقبلك.",
                "The world's most famous budget rule, from Elizabeth Warren's All Your Worth: half your income for needs, 30% for wants, and 20% for savings and debt payoff. Its simplicity is its strength: it neither kills your joy nor wastes your future.",
                new String[]{
                        "احسب 20٪ من دخلك أول الشهر وحوّلها للادخار قبل أي إنفاق.",
                        "إذا تجاوزت الاحتياجات 50٪: المشكلة سكن أو سيارة — أكبر بندين قابلين للخفض.",
                        "الرغبات ليست عيبًا؛ حدّدها مسبقًا لتستمتع بلا ذنب.",
                        "في مَحلولة: قارن أرقام لوحة التحكم بنسب 50/30/20 كل شهر."
                },
                new String[]{
                        "Compute 20% of income at month start and move it to savings first.",
                        "If needs exceed 50%, housing or transport is the fixable giant.",
                        "Wants aren't a sin; pre-define them so you enjoy guilt-free.",
                        "In Mahloula: compare dashboard numbers with 50/30/20 monthly."
                },
                "اضبط ميزانيتك الشهرية في الإعدادات وراقب شريط التقدم في لوحة التحكم.",
                "Set your monthly budget in Settings and watch the progress bar on the dashboard.",
                "Elizabeth Warren, All Your Worth (50/30/20)",
                "Elizabeth Warren, All Your Worth (50/30/20)"));

        LESSONS.add(new Lesson(
                "ادفع لنفسك أولًا",
                "Pay yourself first",
                "piggy",
                "أقدم قاعدة ادخار في التاريخ المالي الحديث: عند نزول الدخل، حوّل نسبة الادخار فورًا إلى حساب منفصل، ثم عِش على الباقي — لا العكس. من يفعل ذلك يدّخر تلقائيًا حتى لو نسي، ومن ينتظر «الفاضي آخر الشهر» لا يجد فاضياً أبدًا.",
                "The oldest rule in modern personal finance: when income arrives, move your savings share to a separate account immediately, then live on the rest — not the reverse. Those who do this save automatically; those who wait for 'leftovers' never find any.",
                new String[]{
                        "اجعل التحويل تلقائيًا يوم الراتب (أو بنفس اليوم يدويًا عبر مَحلولة).",
                        "ابدأ بـ 5٪ إذا كان 20٪ صعبًا، ثم زد 1٪ كل شهرين.",
                        "حساب الادخار لا بطاقة صراف له — يصعب لمسه.",
                        "سجّل التحويل في مَحلولة ضمن تصنيف «ادخار واستثمار» ليظهر في تقاريرك."
                },
                new String[]{
                        "Automate the transfer on payday (or log it same-day in Mahloula).",
                        "Start at 5% if 20% is hard; add 1% every two months.",
                        "Keep the savings account card-less — hard to touch.",
                        "Log transfers under 'Savings' so your reports reflect them."
                },
                "أنشئ هدف ادخار في لوحة التحكم وحدّد مبلغًا شهريًا ثابتًا له.",
                "Create a savings goal on the dashboard with a fixed monthly amount.",
                "قاعدة Pay Yourself First — The Richest Man in Babylon",
                "Pay Yourself First — The Richest Man in Babylon"));

        LESSONS.add(new Lesson(
                "صندوق الطوارئ: وسادتك قبل الاستثمار",
                "Emergency fund: your cushion before investing",
                "shield",
                "كل الخطط المالية تنهار عند أول عطل مفاجئ إذا لم تكن لديك وسادة نقدية. التوصية العالمية: من 3 إلى 6 أشهر من مصاريفك الأساسية في حساب سريع السحب. هذا الصندوق هو ما يمنعك من الاقتراض بفائدة عندما تتعطل السيارة أو يتوقف الدخل.",
                "Every financial plan collapses at the first surprise if you lack a cash cushion. The global recommendation: 3–6 months of essential expenses in a liquid account. This fund is what keeps you from borrowing at interest when the car breaks or income stops.",
                new String[]{
                        "احسب مصروفك الشهري الأساسي × 3 = هدفك الأول.",
                        "ابنه تدريجيًا: مبلغ ثابت شهريًا مهما صغر.",
                        "ليس استثمارًا: لا تخاطر به، سيولته هي قيمته.",
                        "لمسته فقط لطوارئ حقيقية — وإلا فأعد تعبئته فورًا."
                },
                new String[]{
                        "Essential monthly expense × 3 = your first target.",
                        "Build it gradually with a fixed monthly amount.",
                        "It's not an investment: liquidity is its value.",
                        "Touch it only for true emergencies — else refill immediately."
                },
                "افتح هدفًا باسم «صندوق الطوارئ» وتابع تقدّمه شهريًا.",
                "Open a goal named 'Emergency Fund' and track it monthly.",
                "توصية NFCC ومستشاري CFP العالمية",
                "NFCC & CFP global guidance"));

        LESSONS.add(new Lesson(
                "كرة الثلج أم الانهيار الجليدي؟ سداد الديون",
                "Snowball vs avalanche: killing debt",
                "handshake",
                "طريقتان مثبتتان عالميًا: «الانهيار الجليدي» تبدأ بالدين الأعلى فائدة فتوفر مالًا أكثر، و«كرة الثلج» تبدأ بالأصغر مبلغًا فتمنحك انتصارات سريعة تبني الاستمرار. الأبحاث السلوكية (Duke University) أظهرت أن كرة الثلج أنجح في جعل الناس يلتزمون فعلًا حتى النهاية.",
                "Two proven methods: the avalanche starts with the highest-interest debt and saves more money; the snowball starts with the smallest balance and builds momentum. Behavioral research (Duke) shows snowball users are more likely to actually finish.",
                new String[]{
                        "سجّل كل ديونك في خانة الديون: لمن، وكم، ومتى.",
                        "رتّبها: إن كنت تنجز بسهولة اختر الانهيار الجليدي (الأعلى فائدة أولًا).",
                        "إن كنت تحتاج حافزًا: كرة الثلج — سدد الأصغر واحتفل بإغلاقه.",
                        "ادفع دائمًا أكثر من الحد الأدنى — الحد الأدنى مصمم ليطيل الدين."
                },
                new String[]{
                        "Log every debt: to whom, how much, by when.",
                        "If you're disciplined: avalanche (highest interest first).",
                        "If you need momentum: snowball (smallest first, celebrate closures).",
                        "Always pay above the minimum — minimums are designed to extend debt."
                },
                "أضف ديونك اليوم، وسجّل أول تسديد ولو صغيرًا.",
                "Add your debts today and log a first payment, however small.",
                "Duke University behavioral study on debt payoff (2016)",
                "Duke behavioral debt-payoff study (2016)"));

        LESSONS.add(new Lesson(
                "قاعدة 24 ساعة: قاتل الشراء الاندفاعي",
                "The 24-hour rule: impulse killer",
                "clock",
                "الشراء الاندفاعي مسؤول عن نسبة كبيرة من الهدر العالمي. القاعدة بسيطة: أي شراء غير مخطط فوق مبلغ تحدده أنت (مثل 50 وحدة نقدية) ينتظر 24 ساعة في «قائمة انتظار». بعد يوم، نصف الرغبات تختفي وحدها. المتاجر تعرف هذا وتحاربه بعروض «اليوم فقط» — وأنت تعرفه الآن أيضًا.",
                "Impulse buying drives a large share of global waste. The rule: any unplanned purchase above a threshold you set waits 24 hours in a wishlist. A day later, half the urges vanish. Retailers fight this with 'today only' deals — now you know why.",
                new String[]{
                        "حدّد عتبة الانتظار: مبلغ تشعر أنه «كبير نسبيًا» بالنسبة لك.",
                        "اكتب الرغبة في الملاحظات مع التاريخ بدل شرائها فورًا.",
                        "بعد 24 ساعة اسأل: هل سأشتريه لو كان بسعر كامل دون عرض؟",
                        "احذف اشتراكات المتاجر من بريدك — التسويق وقود الاندفاع."
                },
                new String[]{
                        "Set your waiting threshold amount.",
                        "Write the urge in Notes with a date instead of buying.",
                        "After 24h ask: would I buy it at full price, no deal?",
                        "Unsubscribe from store emails — marketing fuels impulses."
                },
                "أضف ملاحظة بعنوان «قائمة الانتظار» وضع فيها رغباتك قبل الشراء.",
                "Create a note titled 'Waiting list' for urges before buying.",
                "اقتصاد سلوكي — تقنيات فترة التهدئة",
                "Behavioral economics — cooling-off techniques"));

        LESSONS.add(new Lesson(
                "لا أسلوب الحياة: التضخم الذي لا تلاحظه",
                "Lifestyle creep: the inflation you never notice",
                "trend_up",
                "كلما زاد دخلك ارتفع إنفاقك تلقائيًا: سيارة أفخم، مقهى أغلى، اشتراكات أكثر. يسمى «زحف أسلوب الحياة» وهو السبب الأول لعدم شعور أصحاب الرواتب العالية بالأمان المالي. الحل العالمي: عند كل زيادة دخل، حوّل نصف الزيادة للادخار وعِش بالنصف الآخر — تتقدم دون أن تفقد السيطرة.",
                "As income rises, spending rises automatically: fancier car, pricier coffee, more subscriptions. 'Lifestyle creep' is the top reason high earners feel broke. The global fix: on every raise, bank half the increase and live on the other half — progress without losing control.",
                new String[]{
                        "زيادة راتب؟ حوّل 50٪ منها للادخار فورًا وقبل تعديل أسلوب حياتك.",
                        "راقب الاشتراكات الشهرية: كل 10 وحدات شهريًا = 120 سنويًا.",
                        "قارن مصروفك الشهري في مَحلولة شهرًا بشهر لاكتشاف الزحف.",
                        "الترقية الحقيقية هي ارتفاع صافي ثروتك، لا ارتفاع إنفاقك."
                },
                new String[]{
                        "Got a raise? Save 50% of it before upgrading your life.",
                        "Audit subscriptions: 10/month is 120/year.",
                        "Compare month-over-month spend in Mahloula to spot creep.",
                        "Real promotion = rising net worth, not rising spend."
                },
                "افتح تقرير الشهرين الماضيين وقارن إجمالي المصروف.",
                "Open the last two months' reports and compare totals.",
                "Harvard Business Review — lifestyle inflation articles",
                "Harvard Business Review — lifestyle inflation"));

        LESSONS.add(new Lesson(
                "الأرقام الصغيرة ليست صغيرة",
                "Small numbers aren't small",
                "coins",
                "قهوة يومية بمبلغ 3 وحدات = نحو 1095 سنويًا. لو ادّخرتها بعائد 7٪ سنويًا لعشرين سنة لصارت أكثر من 56 ألفًا (قوة الفائدة المركبة). لا تحتقر التسريب الصغير: الإصلاح ليس حرمانًا بل استبدالًا ذكيًا — بدائل أرخص لنفس المتعة.",
                "A daily 3-unit coffee ≈ 1,095 a year. Saved at 7% for 20 years it compounds past 56,000. Don't scorn small leaks: the fix isn't deprivation but smart substitution — cheaper routes to the same joy.",
                new String[]{
                        "اجمع مصاريفك المتكررة الصغيرة في تقرير الفئات بمَحلولة.",
                        "اختر تسريبًا واحدًا فقط هذا الشهر وبدّله ببديل أرخص.",
                        "حوّل المبلغ الموفّر فورًا إلى هدف ادخار باسمه.",
                        "قاعدة 72: اقسم 72 على نسبة العائد لتعرف سنوات تضاعف مالك."
                },
                new String[]{
                        "Group small recurring spend in Mahloula's category report.",
                        "Pick one leak this month and swap it for a cheaper alternative.",
                        "Move the saved amount into a goal named after it.",
                        "Rule of 72: divide 72 by return % to get doubling years."
                },
                "افتح رسم الفئات هذا الشهر واختر أكبر «تسريب صغير».",
                "Open this month's category chart and pick the biggest small leak.",
                "قوة الفائدة المركبة — Rule of 72",
                "Compound interest — Rule of 72"));

        LESSONS.add(new Lesson(
                "ميزانية المغلفات الرقمية",
                "Digital envelope budgeting",
                "wallet",
                "طريقة الجدّات بعقلية حديثة: قسّم ميزانية الشهر على «مغلفات» (فئات) بمبالغ محددة مسبقًا. عندما يفرغ مغلف الطعام، تتوقف مصاريف الطعام حتى الشهر التالي — قرار واضح لا جدال فيه. الدراسات تُظهر أن الدفع المحدد مسبقًا يخفض الإنفاق 12–18٪ لأنه يجعل الألم المالي مرئيًا.",
                "The grandmothers' method, modernized: split the month's budget into envelopes (categories) with pre-set amounts. When the food envelope empties, food spending stops until next month — a clear, argument-free decision. Pre-commitment cuts spending 12–18% by making monetary pain visible.",
                new String[]{
                        "وزّع ميزانيتك الشهرية على 4–6 فئات رئيسية فقط.",
                        "تابع كل فئة في مَحلولة عبر رسم الفئات وشريط الميزانية.",
                        "نفد مغلف؟ انقل من مغلف فائض بقرار واعٍ وسجّله — لا تسحب من المدخرات.",
                        "آخر الشهر: الفائض في أي مغلف يذهب للادخار تلقائيًا."
                },
                new String[]{
                        "Split your budget into only 4–6 main envelopes.",
                        "Track each via Mahloula's category chart and budget bar.",
                        "Envelope empty? Move from a surplus envelope consciously — never from savings.",
                        "Month end: any envelope surplus goes to savings automatically."
                },
                "حدّد سقفًا لكل فئة رئيسية وراقبه في لوحة التحكم.",
                "Set a cap per main category and watch it on the dashboard.",
                "Mental accounting — Richard Thaler",
                "Mental accounting — Richard Thaler"));

        LESSONS.add(new Lesson(
                "صافي الثروة: رقمك الحقيقي",
                "Net worth: your real score",
                "chart_line",
                "الدخل العالي لا يعني ثراءً؛ الثراء هو ما يتبقى. صافي الثروة = (ما تملك: نقد، مدخرات، استثمارات) − (ما عليك: ديون). تتبعه شهريًا هو بوصلة الأغنياء الحقيقية: قد ينخفض شهرًا بسبب طارئ، لكن اتجاهه السنوي الصاعد هو دليل أنك تبني لا تستهلك.",
                "High income isn't wealth; wealth is what remains. Net worth = assets (cash, savings, investments) − liabilities (debts). Tracking it monthly is the true compass of the rich: it may dip in a hard month, but a rising yearly trend proves you're building, not consuming.",
                new String[]{
                        "احسبه اليوم: مجموع أرصدة محافظك − مجموع ديونك في مَحلولة.",
                        "سجّله أول كل شهر ولاحظ الاتجاه لا الرقم.",
                        "هدف صحي: ارتفاع سنوي لا يقل عن 10٪ من دخلك.",
                        "كل تسديد دين يرفع صافي ثروتك فورًا — احتفل به."
                },
                new String[]{
                        "Compute it today: wallet balances − total debts in Mahloula.",
                        "Log it on the 1st of each month; watch the trend, not the number.",
                        "Healthy target: yearly rise ≥ 10% of income.",
                        "Every debt payment raises net worth instantly — celebrate it."
                },
                "اكتب صافي ثروتك الحالي في ملاحظة مثبّتة بعنوان «بوصلتي».",
                "Write your current net worth in a pinned note titled 'My compass'.",
                "منهجية تتبع صافي الثروة — المستشارون الماليون المعتمدون",
                "Net-worth tracking — certified financial planners"));

        LESSONS.add(new Lesson(
                "سيكولوجيا المال: لماذا نشتري ما لا نحتاج؟",
                "Money psychology: why we buy what we don't need",
                "bulb",
                "التسويق يلعب على انحيازات موثقة: «ألم الدفع» يخف بالبطاقة مقابل النقد، و«تأثير النهاية» يجعلنا نكمل وجبة كبيرة رغم الشبع، و«الإرساء» يجعل خصم 50٪ على سعر مبالغ فيه يبدو صفقة. معرفة الفخاخ نصف النجاة منها.",
                "Marketing plays documented biases: card payments dull the 'pain of paying' vs cash; the 'unit bias' makes us finish big portions; 'anchoring' makes 50% off an inflated price feel like a win. Knowing the traps is half the escape.",
                new String[]{
                        "للإنفاق الواعي: ادفع نقدًا في الفئات التي تتسرّب منها.",
                        "قبل أي شراء اسأل: كم ساعة عمل يكلفني هذا؟",
                        "قارن السعر بمنافسين لا بالسعر «قبل الخصم».",
                        "تسوق بقائمة مكتوبة ومعدة مسبقًا — القائمة لقاح ضد المتجر."
                },
                new String[]{
                        "For mindful spending: pay cash in your leaky categories.",
                        "Before buying ask: how many work-hours is this?",
                        "Compare prices to competitors, not to 'before' prices.",
                        "Shop with a written list — lists are vaccines against stores."
                },
                "اختر فئة تسريب واحدة وجرّب الدفع نقدًا لها شهرًا كاملًا.",
                "Pick one leaky category and pay cash for it for a full month.",
                "Dan Ariely, Predictably Irrational",
                "Dan Ariely, Predictably Irrational"));

        LESSONS.add(new Lesson(
                "الاجتماع المالي الشهري: 20 دقيقة تغيّر سنتك",
                "The monthly money meeting: 20 minutes that change your year",
                "calendar",
                "الشركات تراجع أرقامها شهريًا، والأفراد الناجحون ماليًا يفعلون الشيء نفسه. موعد ثابت نهاية كل شهر: ماذا أنفقنا؟ أين تجاوزنا؟ ما الدرس؟ وما خطة الشهر القادم؟ من يعقد هذا الاجتماع بانتظام يتحسن أداؤه المالي بوتيرة أسرع بكثير — لأن القرارات تصبح بيانات لا مشاعر.",
                "Companies review numbers monthly; financially successful individuals do the same. A fixed end-of-month appointment: what did we spend? where did we overshoot? what's the lesson? what's next month's plan? Regular reviewers improve far faster — because decisions become data, not emotions.",
                new String[]{
                        "ثبّت موعدًا: آخر يوم من الشهر، 20 دقيقة، بلا مقاطعات.",
                        "افتح مَحلولة: لوحة التحكم + الفئات + الديون أمامك.",
                        "اكتب 3 قرارات فقط للشهر القادم — الكثيرة لا تُنفذ.",
                        "كافئ نفسك على الالتزام: مكافأة صغيرة مخطط لها تبني العادة."
                },
                new String[]{
                        "Fix a slot: last day of month, 20 minutes, no interruptions.",
                        "Open Mahloula: dashboard + categories + debts in front of you.",
                        "Write only 3 decisions for next month — more never get executed.",
                        "Reward consistency: a small planned reward builds the habit."
                },
                "ضع تذكيرًا شهريًا من الإعدادات (التذكير اليومي موجود أيضًا).",
                "Set a monthly reminder from Settings (daily reminder also available).",
                "ممارسات التخطيط المالي الأسري العالمية",
                "Global family financial planning practice"));
    }

    // ---------------- القواعد العالمية الذهبية ----------------

    public static final class Rule {
        public final String nameAr, nameEn, bodyAr, bodyEn, tagAr;

        Rule(String nA, String nE, String bA, String bE, String tA) {
            this.nameAr = nA; this.nameEn = nE; this.bodyAr = bA; this.bodyEn = bE; this.tagAr = tA;
        }
    }

    public static final List<Rule> RULES = new ArrayList<>();

    static {
        RULES.add(new Rule("قاعدة 50/30/20", "50/30/20 rule",
                "50٪ احتياجات، 30٪ رغبات، 20٪ ادخار وسداد ديون. ابدأ بها إن لم تكن لك ميزانية.",
                "50% needs, 30% wants, 20% savings & debt. Start here if you have no budget.", "ميزانية"));
        RULES.add(new Rule("قاعدة 24 ساعة", "24-hour rule",
                "أي شراء اندفاعي فوق عتبة تحددها ينتظر يومًا كاملًا قبل القرار.",
                "Any impulse buy above your threshold waits a full day.", "إنفاق واعي"));
        RULES.add(new Rule("قاعدة 30 يومًا للمشتريات الكبيرة", "30-day rule",
                "فوق مبلغ كبير؟ انتظر 30 يومًا. إن بقيت الحاجة والرغبة معًا، اشترِ بثقة.",
                "Big ticket? Wait 30 days. If need and want both remain, buy confidently.", "إنفاق واعي"));
        RULES.add(new Rule("ادخر 10٪ كحد أدنى", "Save at least 10%",
                "الحد التاريخي الأدنى للادخار: عُشر الدخل. أقل منه يعني أنك تؤجل مشاكلك فقط.",
                "The historic savings floor: a tenth of income. Less means postponing problems.", "ادخار"));
        RULES.add(new Rule("صندوق طوارئ 3–6 أشهر", "3–6 month emergency fund",
                "وسادة نقدية سريعة السحب تغطّي مصاريفك الأساسية قبل أي استثمار.",
                "A liquid cushion covering essentials before any investing.", "حماية"));
        RULES.add(new Rule("قاعدة 72", "Rule of 72",
                "72 ÷ نسبة العائد السنوي = سنوات تضاعف مالك. عند 8٪ يتضاعف كل 9 سنوات.",
                "72 ÷ annual return % = years to double. At 8%, money doubles every 9 years.", "استثمار"));
        RULES.add(new Rule("قاعدة 20/4/10 للسيارات", "20/4/10 car rule",
                "20٪ دفعة أولى، تمويل 4 سنوات كحد أقصى، وقسط شهري لا يتجاوز 10٪ من دخلك.",
                "20% down, max 4-year loan, monthly payment ≤ 10% of income.", "قرارات كبيرة"));
        RULES.add(new Rule("سكن بأقل من 30٪", "Housing under 30%",
                "السكن (إيجار أو قسط) فوق ثلث الدخل هو أشهر سبب اختناق مالي عالميًا.",
                "Housing above a third of income is the world's most common money squeeze.", "ميزانية"));
        RULES.add(new Rule("كرة الثلج للديون", "Debt snowball",
                "سدّد الأصغر مبلغًا أولًا لبناء زخم نفسي، مع الحد الأدنى لبقية الديون.",
                "Pay smallest balances first for momentum; minimums on the rest.", "ديون"));
        RULES.add(new Rule("الانهيار الجليدي للديون", "Debt avalanche",
                "سدّد الأعلى فائدة أولًا لتقليل إجمالي الفوائد المدفوعة.",
                "Pay highest interest first to minimize total interest paid.", "ديون"));
        RULES.add(new Rule("لا دين استهلاكي", "No consumer debt",
                "الدين المقبول عالميًا: ما يبني أصلًا (سكن، تعليم، مشروع). دين الاستهلاك يستهلك مستقبلك.",
                "Globally acceptable debt builds assets (home, education, business). Consumer debt eats your future.", "ديون"));
        RULES.add(new Rule("ادفع لنفسك أولًا", "Pay yourself first",
                "حوّل نسبة الادخار لحظة نزول الدخل، قبل أن يبدأ الشهر بالتهامها.",
                "Transfer your savings share the moment income lands.", "ادخار"));
        RULES.add(new Rule("تتبع كل شيء", "Track everything",
                "ما لا يُقاس لا يُدار. التتبع وحده يخفض الإنفاق 5–10٪ فورًا.",
                "What gets measured gets managed. Tracking alone cuts spending 5–10% instantly.", "وعي"));
        RULES.add(new Rule("ميزانية صفرية", "Zero-based budget",
                "كل وحدة نقدية لها وظيفة محددة قبل بدء الشهر: دخل − تخصيصات = صفر.",
                "Every unit gets a job before month start: income − assignments = zero.", "ميزانية"));
        RULES.add(new Rule("ت自动化 القرار", "Automate decisions",
                "التحويل التلقائي للادخار وللفواتير يلغي الاعتماد على الإرادة — والإرادة مورد ناضب.",
                "Auto-transfers for savings and bills remove reliance on willpower — a depleting resource.", "أنظمة"));
        RULES.add(new Rule("راجع اشتراكاتك ربع سنويًا", "Quarterly subscription audit",
                "كل اشتراك لا تستخدمه أسبوعيًا على الأقل مرشح للإلغاء الفوري.",
                "Any subscription unused weekly is a cancellation candidate.", "إنفاق واعي"));
        RULES.add(new Rule("قائمة قبل المتجر", "List before store",
                "التسوق بقائمة مكتوبة ومعدة مسبقًا يخفض فاتورة السوبرماركت 15–20٪.",
                "Shopping with a pre-written list cuts grocery bills 15–20%.", "إنفاق واعي"));
        RULES.add(new Rule("اجتماع مالي شهري", "Monthly money meeting",
                "20 دقيقة نهاية الشهر لمراجعة الأرقام واتخاذ 3 قرارات فقط.",
                "20 minutes monthly to review numbers and make exactly 3 decisions.", "أنظمة"));
    }

    // ================= الدفعة الثانية: دروس إضافية =================
    static {
        LESSONS.add(new Lesson(
                "ميزانية الأسبوع: المظروف الأسبوعي",
                "The weekly envelope",
                "wallet",
                "الميزانية الشهرية تصمد أسبوعين فقط عند أغلب الناس. الحل الاحترافي: قسّم سقف الشهر على 4 أسابيع، وامنح كل أسبوع «مظروفًا» مستقلًا. انتهى مظروف الأسبوع؟ تنتظر الاثنين القادم — هكذا يتحول الانضباط من قرار شهري كبير إلى قرارات أسبوعية صغيرة يسهل الفوز بها.",
                "Monthly budgets collapse by week two for most people. The pro fix: split the month cap into 4 weekly envelopes. When one empties, you wait for Monday — discipline becomes small winnable decisions.",
                new String[]{
                        "اقسم ميزانيتك الشهرية ÷ 4.3 وضع ناتج الأسبوع في مَحلولة كسقف أسبوعي ذهني.",
                        "لا تستلف من أسبوع قادم إلا بقرار مسجل — الاستدانة الصامتة هي ثقب الميزانية.",
                        "الفائض الأسبوعي يُرحّل تلقائيًا للادخار، لا للأسبوع التالي.",
                        "بعد 4 أسابيع قارن: أي أسبوع ينهار دائمًا؟ هناك يكمن النمط."
                },
                new String[]{
                        "Divide your monthly cap by 4.3 and treat it as a weekly envelope.",
                        "Never borrow from next week silently — log any transfer as a decision.",
                        "Weekly surplus rolls to savings, not to next week.",
                        "After 4 weeks compare: which week always collapses? That's your pattern."
                },
                "احسب سقف أسبوعك الآن من ميزانيتك الشهرية واكتبه في ملاحظة مثبتة.",
                "Compute your weekly cap now and pin it in a note.",
                "Envelope budgeting — weekly cadence variant",
                "Envelope budgeting — weekly cadence"));

        LESSONS.add(new Lesson(
                "فاصل التكلفة بالاستخدام: السعر الحقيقي",
                "Cost per use: the real price",
                "chart_line",
                "حذاء بـ100 يُلبس 200 مرة أرخص من قميص بـ20 يُلبس مرتين. قاعدة «التكلفة لكل استخدام» تعيد تعريف الغالي والرخيص: اقسم السعر على عدد الاستخدامات المتوقعة؛ أقل من وحدة واحدة لكل استخدام = صفقة جيدة عادة. تستخدمها الشركات في تقييم المعدات، وتعمل تمامًا في خزانة ملابسك.",
                "A 100 shoe worn 200 times beats a 20 shirt worn twice. Cost-per-use redefines cheap vs expensive: price ÷ expected uses; under ~1 per use is usually a good buy.",
                new String[]{
                        "قبل شراء دائم: احسب التكلفة/استخدام خلال سنة.",
                        "الأدوات اليومية تستحق جودة أعلى؛ النادرة تستحق استئجارًا أو استعارة.",
                        "قارن بدائل نفس الغرض بالتكلفة/استخدام لا بالسعر الظاهر.",
                        "سجّل في الملاحظات: كم مرة استخدمت آخر 3 أشياء اشتريتها؟"
                },
                new String[]{
                        "Before durable purchases: compute cost/use over a year.",
                        "Daily tools deserve quality; rare-use items deserve renting or borrowing.",
                        "Compare alternatives by cost/use, not sticker price.",
                        "Log: how often did you use your last 3 purchases?"
                },
                "اختر آخر شراء كبير واحسب تكلفته لكل استخدام حتى اليوم.",
                "Pick your last big purchase and compute its cost per use so far.",
                "Cost-per-use — consumer economics practice",
                "Cost-per-use practice"));

        LESSONS.add(new Lesson(
                "دخل إضافي آمن: ابدأ صغيرًا وذكياً",
                "Safe side income: start small and smart",
                "trend_up",
                "توفير وحده له سقف؛ الدخل لا سقف له. القاعدة العالمية للأمان: لا تستدِن ولا تستثمر مدخراتك لتبدأ عملًا جانبيًا — ابدأ بمهارة تملكها ووقت ميت (ساعتان مساءً)، وبِع خدمة لا منتجًا أولًا (الخدمات بلا مخزون). كل دخل إضافي يوجّه 50٪ منه للديون/الادخار مباشرة حتى تتحقق وسادتك.",
                "Saving has a ceiling; income doesn't. Global safety rule: never borrow or invest savings to start a side hustle — begin with a skill you own and dead time, selling services first (no inventory). Route 50% of side income to debts/savings until your cushion is complete.",
                new String[]{
                        "عدد 3 مهارات يدفع الناس لك مقابلها اليوم.",
                        "اختبر الطلب بأصغر عرض ممكن قبل أي تجهيز أو شراء.",
                        "سجّل الدخل الجانبي في مَحلولة كمصدر مستقل لتراه بوضوح.",
                        "لا تخلط مال المشروع بالمال الشخصي — محفظة منفصلة."
                },
                new String[]{
                        "List 3 skills people would pay for today.",
                        "Test demand with the smallest offer before buying anything.",
                        "Log side income in Mahloula as its own source.",
                        "Never mix business money with personal money — separate wallet."
                },
                "أنشئ محفظة باسم «عملي الجانبي» في مَحلولة اليوم.",
                "Create a wallet named 'Side work' in Mahloula today.",
                "Lean startup principles — Eric Ries",
                "Lean startup — Eric Ries"));

        LESSONS.add(new Lesson(
                "أطفال ماليًا أذكياء: العادة تبدأ من البيت",
                "Money-smart kids: habits start at home",
                "family",
                "الأبحاث التربوية تؤكد أن عادات المال تتشكل قبل سن العاشرة. ثلاث ممارسات تصنع الفرق: مصروف أسبوعي ثابت بقرار الطفل (لا رقابة لصيقة)، تقسيمه لثلاثة أوعية (إنفاق/ادخار/عطاء)، واجتماع عائلي قصير يرى فيه الطفل كيف تخطط الأسرة ميزانيتها. الطفل الذي يقرر ويخطأ صغيرًا لا يخطئ كبيرًا.",
                "Research shows money habits form before age ten. Three practices: a fixed weekly allowance the child controls, three jars (spend/save/give), and a short family meeting where kids see real budgeting.",
                new String[]{
                        "مصروف ثابت أسبوعي = درس ميزانية مصغر.",
                        "ثلاثة أوعية مرئية: إنفاق، ادخار، عطاء.",
                        "دع الخطأ الصغير يحدث: إنه أرخص درس في حياتهم.",
                        "اشركهم في مقارنة سعرين بالسوبرماركت — لعبة تصنع مهارة."
                },
                new String[]{
                        "Fixed weekly allowance = a miniature budget lesson.",
                        "Three visible jars: spend, save, give.",
                        "Let small mistakes happen: the cheapest lesson ever.",
                        "Have them compare two prices at the store — a game that builds skill."
                },
                "افتح نقاشًا عائليًا هذا الأسبوع حول هدف ادخار مشترك.",
                "Start a family talk this week about a shared savings goal.",
                "Cambridge University — Money habits in childhood study",
                "Cambridge childhood money-habits study"));

        // ---------------- قواعد إضافية ----------------
        RULES.add(new Rule("قائمة واحد-داخل/واحد-خارج", "One in, one out",
                "كل غرض جديد يدخل البيت يخرج مقابله غرض قديم — يحجم الفوضى والمصروف معًا.",
                "Every new item in means one old item out — caps both clutter and spending.", "إنفاق واعي"));
        RULES.add(new Rule("التكلفة لكل استخدام", "Cost per use",
                "السعر ÷ عدد الاستخدامات المتوقعة = الحكم الحقيقي على الصفقة.",
                "Price ÷ expected uses = the true verdict on a deal.", "قرارات شراء"));
        RULES.add(new Rule("لا تسوّق وأنت جائع أو متعب", "Never shop hungry or tired",
                "الجوع والتعب يرفعان فاتورة السوبرماركت 10–20٪ وفق دراسات السلوك الاستهلاكي.",
                "Hunger and fatigue inflate grocery bills 10–20% per consumer studies.", "إنفاق واعي"));
        RULES.add(new Rule("هامش 10٪ في كل ميزانية", "10% budget buffer",
                "اضف 10٪ هامش طوارئ داخل الميزانية نفسها — الميزانية بدون هامش تنكسر أول أسبوع.",
                "Build a 10% buffer inside the budget itself — bufferless budgets break in week one.", "ميزانية"));
        RULES.add(new Rule("يوم التفاوض السنوي", "Annual negotiation day",
                "مرة سنويًا: فاوض على الإنترنت، التأمين، الاشتراكات، وحتى الإيجار. المتوسط العالمي للتوفير: 15–30٪ من الفاتورة.",
                "Once a year negotiate internet, insurance, subscriptions, even rent. Typical global savings: 15–30%.", "فواتير"));
        RULES.add(new Rule("قاعدة 48 ساعة للاشتراكات", "48-hour rule for subscriptions",
                "أي اشتراك جديد ينتظر 48 ساعة، ويُقيَّم كل 3 أشهر بالاستخدام الفعلي لا بالنوايا.",
                "New subscriptions wait 48 hours and get audited quarterly by actual use, not intent.", "اشتراكات"));
    }

    // ---------------- الأخطاء المالية الشائعة ----------------
    public static final class Mistake {
        public final String ar, en, fixAr, fixEn;

        Mistake(String a, String e, String fa, String fe) { ar = a; en = e; fixAr = fa; fixEn = fe; }
    }

    public static final java.util.List<Mistake> MISTAKES = new java.util.ArrayList<>();

    static {
        MISTAKES.add(new Mistake("دفع الفواتير آخرًا والادخار بالفاضل", "Paying bills last and saving the leftovers",
                "اعكس: ادّخر أولًا ثم ادفع ثم أنفق.", "Reverse it: save first, pay, then spend."));
        MISTAKES.add(new Mistake("الحد الأدنى لبطاقات الديون", "Paying only credit minimums",
                "الحد الأدنى يطيل عمر الدين سنوات ويضاعف الفوائد.", "Minimums stretch debt for years and double interest."));
        MISTAKES.add(new Mistake("ميزانية ورقية لا تُراجع", "A paper budget never reviewed",
                "ميزانية بلا مراجعة أسبوعية ديكورٌ رقمي.", "A budget without weekly review is digital décor."));
        MISTAKES.add(new Mistake("اشتراكات نائمة", "Zombie subscriptions",
                "افحص كشوفك الشهرية: كل اشتراك لم يُستخدم شهرًا يُلغى.", "Audit statements monthly: unused for a month = cancel."));
        MISTAKES.add(new Mistake("شراء المكانة لا المنفعة", "Buying status, not utility",
                "اسأل: لمن أشتري هذا؟ لي أم لنظرة الآخرين؟", "Ask: am I buying this for me or for their eyes?"));
        MISTAKES.add(new Mistake("لا صندوق طوارئ ثم استدانة عند أول طارئ", "No emergency fund, then borrowing at first shock",
                "الوسادة النقدية تسبق أي استثمار أو ترقية أسلوب حياة.", "Cash cushion before any investing or lifestyle upgrade."));
        MISTAKES.add(new Mistake("تقسيط كل شيء", "Installments on everything",
                "التقسيط المقبول: أصل معمر (سكن/سيارة عمل). لا تقسِّط استهلاكًا يوميًا.", "Finance assets that last; never daily consumption."));
        MISTAKES.add(new Mistake("مجاراة دخل الجيران", "Keeping up with the neighbors",
                "قارن نفسك بنسختك السابقة لا ببيت الجيران.", "Compare yourself to your past self, not the neighbors."));
        MISTAKES.add(new Mistake("أرقام تقريبية في الرأس", "Rough numbers in your head",
                "ما لا يُسجل لا يُدار — سجل خلال 24 ساعة كحد أقصى.", "Unlogged money is unmanaged — log within 24h."));
        MISTAKES.add(new Mistake("استثمار قبل سدّ الديون العالية", "Investing before killing high-interest debt",
                "عائد مضمون 25٪ (فائدة دين) لا يهزمه أي سوق.", "A guaranteed 25% return (debt interest) beats any market."));
    }
}
