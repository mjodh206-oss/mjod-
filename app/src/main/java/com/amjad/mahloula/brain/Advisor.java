package com.amjad.mahloula.brain;

import android.content.Context;

import com.amjad.mahloula.R;
import com.amjad.mahloula.data.Models;
import com.amjad.mahloula.data.Repo;
import com.amjad.mahloula.util.Fmt;
import com.amjad.mahloula.util.Prefs;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * المستشار المالي في مَحلولة: يفحص بياناتك الحقيقية ويولّد
 * (1) تنبيهات فورية عند وجود خلل في الصرف،
 * (2) خطط عمل مبنية على أفضل الممارسات العالمية،
 * (3) مؤشر صحة مالية من 100.
 */
public final class Advisor {

    private Advisor() {}

    public static final class Insight {
        public int level;        // 0 معلومة، 1 تنبيه، 2 خطر
        public String title;
        public String body;
        public String action;    // نص زر الإجراء (اختياري)
        public int actionTab = -1;
        public String icon;

        Insight(int level, String icon, String title, String body, String action, int tab) {
            this.level = level; this.icon = icon; this.title = title; this.body = body;
            this.action = action; this.actionTab = tab;
        }
    }

    public static final int TAB_DASH = 0, TAB_TX = 1, TAB_DEBT = 2, TAB_NOTES = 3, TAB_GUIDE = 4;

    /** فحص شامل يولّد قائمة التنبيهات مرتبة بالأهمية. */
    public static List<Insight> scan(Context c) {
        List<Insight> out = new ArrayList<>();
        Repo r = Repo.get(c);
        Prefs p = Prefs.get(c);
        boolean ar = !p.language().equals("en");
        Calendar now = Calendar.getInstance();
        Calendar ms = Fmt.startOfMonth(now);
        Calendar me = Fmt.endOfMonth(now);
        Models.MonthTotals mt = r.monthTotals(now);
        double budget = p.monthlyBudget();
        int dayOfMonth = now.get(Calendar.DAY_OF_MONTH);
        int daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH);

        if (!r.hasAnyData()) {
            out.add(new Insight(0, "sparkle",
                    ar ? "أهلًا بك في مَحلولة 👋" : "Welcome to Mahloula 👋",
                    ar ? "سجّل أول مصروف أو دخل لك، وبعدها يبدأ المستشار المالي بالعمل تلقائيًا: تنبيهات، خطط، ومؤشر صحة مالية."
                       : "Log your first expense or income; the advisor then starts working automatically: alerts, plans, and a financial health score.",
                    ar ? "سجّل أول عملية" : "Log first entry", TAB_TX));
            return out;
        }

        // 1) تجاوز الميزانية -------------------------------------------------
        if (budget > 0 && mt.expense > 0) {
            double ratio = mt.expense / budget;
            double expected = (double) dayOfMonth / daysInMonth;
            if (ratio >= 1.0) {
                out.add(new Insight(2, "alert",
                        ar ? "تجاوزت ميزانية الشهر!" : "Budget exceeded!",
                        ar ? "أنفقت " + Fmt.money(c, mt.expense) + " من أصل " + Fmt.money(c, budget)
                           + " (" + Fmt.percent(c, ratio * 100) + "). القاعدة العالمية: أوقف الإنفاق الاختياري فورًا وحوّل باقي الشهر لوضع الطوارئ."
                           : "You spent " + Fmt.money(c, mt.expense) + " of " + Fmt.money(c, budget) + ". Stop discretionary spending now.",
                        ar ? "أين تسرّب المال؟" : "Where did it go?", TAB_TX));
            } else if (ratio > expected + 0.15) {
                out.add(new Insight(1, "trend_down",
                        ar ? "إيقاع الصرف أسرع من الشهر" : "Spending outpaces the month",
                        ar ? "استهلكت " + Fmt.percent(c, ratio * 100) + " من ميزانيتك رغم مرور " + Fmt.percent(c, expected * 100)
                           + " فقط من أيام الشهر. بهذا الإيقاع ستصل نهاية الشهر عند " + Fmt.money(c, mt.expense / expected)
                           + ". خفّض البندين الأكبر هذا الأسبوع."
                           : "You used " + Fmt.percent(c, ratio * 100) + " of budget with only " + Fmt.percent(c, expected * 100) + " of days gone.",
                        ar ? "راجع الفئات" : "Review categories", TAB_TX));
            } else if (ratio >= 0.8) {
                out.add(new Insight(1, "bell",
                        ar ? "اقتربت من سقف ميزانيتك (80٪+)" : "Near your budget ceiling (80%+)",
                        ar ? "تبقّى لك " + Fmt.money(c, budget - mt.expense) + " لبقية الشهر. وزّعها على الأسابيع المتبقية يوميًا: "
                           + Fmt.money(c, (budget - mt.expense) / Math.max(1, daysInMonth - dayOfMonth)) + " يوميًا كحد أقصى."
                           : "Remaining: " + Fmt.money(c, budget - mt.expense) + ". Spread it over remaining days.",
                        null, -1));
            }
        } else if (budget <= 0 && mt.expense > 0) {
            out.add(new Insight(0, "target",
                    ar ? "حدّد ميزانية شهرية ليحميك المستشار" : "Set a monthly budget",
                    ar ? "بدون سقف محدد لا يمكن تنبيهك عند الاقتراب من الخطر. حدد مبلغًا واقعيًا من الإعدادات."
                       : "Without a ceiling, alerts can't protect you. Set a realistic amount in Settings.",
                    ar ? "ضبط الميزانية" : "Set budget", TAB_DASH));
        }

        // 2) لا ادخار هذا الشهر ------------------------------------------------
        double incomeRef = mt.income > 0 ? mt.income : p.monthlyIncome();
        if (incomeRef > 0 && mt.saved < incomeRef * 0.05) {
            out.add(new Insight(1, "piggy",
                    ar ? "لم تدّخر شيئًا يُذكر هذا الشهر" : "Almost no savings this month",
                    ar ? "الادخار الحالي " + Fmt.money(c, mt.saved) + " من دخل " + Fmt.money(c, incomeRef)
                       + ". القاعدة العالمية «ادفع لنفسك أولًا»: حوّل 10–20٪ لحظة نزول الدخل، لا آخر الشهر."
                       : "Savings " + Fmt.money(c, mt.saved) + " of income " + Fmt.money(c, incomeRef) + ". Pay yourself first.",
                    ar ? "افتح هدف ادخار" : "Open a goal", TAB_DASH));
        }

        // 3) فئة ملتهبة ---------------------------------------------------------
        Models.CatTotal top = r.topCatThisMonth();
        if (top != null && mt.expense > 0 && top.total / mt.expense > 0.4 && top.count >= 3) {
            Models.Cat cat = Models.cat(top.cat);
            out.add(new Insight(1, "pie",
                    ar ? "«" + cat.ar + "» يلتهم " + Fmt.percent(c, top.total / mt.expense * 100) + " من مصروفك"
                       : "'" + cat.en + "' eats " + Fmt.percent(c, top.total / mt.expense * 100) + " of spending",
                    ar ? "مجموع " + top.count + " عملية بمبلغ " + Fmt.money(c, top.total)
                       + ". النسبة الصحية لأي فئة استهلاكية أقل من 30٪. جرّب بديلًا أرخص لأكبر عمليتين فيها هذا الشهر."
                       : top.count + " transactions totaling " + Fmt.money(c, top.total) + ". Healthy cap per category: <30%.",
                    ar ? "عرض العمليات" : "View transactions", TAB_TX));
        }

        // 4) عملية شاذة ---------------------------------------------------------
        Models.Tx big = r.biggestTx(ms.getTimeInMillis(), me.getTimeInMillis());
        if (big != null) {
            double avg = r.avgDaily(ms.getTimeInMillis(), me.getTimeInMillis());
            if (avg > 0 && big.amount > avg * 10 && big.amount > mt.expense * 0.3) {
                out.add(new Insight(2, "alert",
                        ar ? "مصروف شاذ يحتاج قرارًا: " + Fmt.money(c, big.amount)
                           : "Anomalous expense: " + Fmt.money(c, big.amount),
                        ar ? "عملية «" + (big.note.isEmpty() ? Models.cat(big.cat).ar : big.note) + "» تساوي "
                           + Fmt.digits(c, String.valueOf(Math.round(big.amount / avg))) + " أضعاف متوسط يومك. إن لم تكن ضرورية، فهي أول مرشح للاسترداد أو التأجيل."
                           : "This single transaction equals " + Math.round(big.amount / avg) + "x your daily average.",
                        null, -1));
            }
        }

        // 5) تسريبات متكررة -----------------------------------------------------
        List<Models.Tx> rec = r.recurringTxs(ms.getTimeInMillis() - 60L * 86400000L, me.getTimeInMillis());
        if (!rec.isEmpty()) {
            double sum = 0;
            String label = "";
            int seen = 0;
            for (Models.Tx t : rec) {
                if (seen == 0) label = Models.cat(t.cat).ar;
                sum += t.amount;
                seen++;
            }
            out.add(new Insight(1, "refresh",
                    ar ? "التزامات متكررة تستحق مراجعة" : "Recurring charges worth auditing",
                    ar ? "رصدنا " + Fmt.digits(c, String.valueOf(seen)) + " عملية متشابهة (مثل «" + label + "») بمجموع "
                       + Fmt.money(c, sum) + " خلال شهرين. الاشتراكات المتكررة هي أسهل مال تستعيده: ألغِ ما لا تستخدمه أسبوعيًا."
                       : "We spotted " + seen + " similar charges totaling " + Fmt.money(c, sum) + " in two months.",
                    null, -1));
        }

        // 6) الديون -------------------------------------------------------------
        double owedByMe = r.debtsOwedByMe();
        double owedToMe = r.debtsOwedToMe();
        if (owedByMe > 0 && incomeRef > 0 && owedByMe > incomeRef * 3) {
            out.add(new Insight(2, "handshake",
                    ar ? "ديونك تعادل " + Fmt.digits(c, String.valueOf(Math.round(owedByMe / incomeRef))) + " أضعاف دخلك الشهري"
                       : "Debts equal " + Math.round(owedByMe / incomeRef) + "x monthly income",
                    ar ? "المتبقي عليك " + Fmt.money(c, owedByMe) + ". المعيار العالمي الصحي: ديون استهلاكية أقل من 6 أضعاف الدخل الشهري، والأفضل 3. استخدم خطة كرة الثلج في الدليل."
                       : "Remaining " + Fmt.money(c, owedByMe) + ". Healthy benchmark: under 6x monthly income.",
                    ar ? "خطة سداد الديون" : "Debt payoff plan", TAB_GUIDE));
        } else if (owedByMe > 0) {
            // ديون متأخرة
            for (Models.Debt d : r.debts()) {
                if (d.isMine() || d.due == 0) continue;
                double left = d.total - r.debtPaid(d.id);
                if (left <= 0) continue;
                long days = (d.due - System.currentTimeMillis()) / 86400000L;
                if (days < 0) {
                    out.add(new Insight(2, "clock",
                            ar ? "دين متأخر: " + d.person : "Overdue debt: " + d.person,
                            ar ? "فات موعد السداد قبل " + Fmt.digits(c, String.valueOf(-days)) + " يوم والمتبقي " + Fmt.money(c, left)
                               + ". التأخير يكسر الثقة ويضيف غرامات — سجّل تسديدًا اليوم ولو جزئيًا."
                               : "Past due by " + (-days) + " days; remaining " + Fmt.money(c, left) + ".",
                            ar ? "سجّل تسديدًا" : "Log a payment", TAB_DEBT));
                } else if (days <= 7) {
                    out.add(new Insight(1, "clock",
                            ar ? "يستحق خلال " + Fmt.digits(c, String.valueOf(days)) + " يوم: " + d.person
                               : "Due in " + days + " days: " + d.person,
                            ar ? "المتبقي " + Fmt.money(c, left) + ". جهّز المبلغ الآن من بند غير ضروري هذا الأسبوع."
                               : "Remaining " + Fmt.money(c, left) + ".",
                            ar ? "فتح الديون" : "Open debts", TAB_DEBT));
                }
            }
        }
        if (owedToMe > 0) {
            out.add(new Insight(0, "swap",
                    ar ? "لك مال عند الآخرين: " + Fmt.money(c, owedToMe) : "Others owe you: " + Fmt.money(c, owedToMe),
                    ar ? "تذكير لطيف: المطالبة المهذبة حق لك. سجّل أي دفعة تستلمها ليحدّث مَحلولة رصيدك تلقائيًا."
                       : "A gentle reminder: polite collection is your right. Log incoming payments.",
                    ar ? "عرض الديون لي" : "View debts to me", TAB_DEBT));
        }

        // 7) أيام بلا تسجيل ----------------------------------------------------
        long lastTs = 0;
        for (Models.Tx t : r.txRange(ms.getTimeInMillis(), me.getTimeInMillis(), null, null, null)) {
            lastTs = Math.max(lastTs, t.ts);
        }
        if (lastTs > 0) {
            long gap = (System.currentTimeMillis() - lastTs) / 86400000L;
            if (gap >= 4) {
                out.add(new Insight(1, "edit",
                        ar ? "مرّت " + Fmt.digits(c, String.valueOf(gap)) + " أيام بلا تسجيل" : gap + " days without logging",
                        ar ? "ما لا يُقاس لا يُدار: الفجوات تصنع ميزانية عمياء. خصص دقيقة الآن لتسجيل ما تذكره."
                           : "Gaps create a blind budget. Take a minute to log what you remember.",
                        ar ? "سجّل الآن" : "Log now", TAB_TX));
            }
        }

        // 8) صندوق الطوارئ ------------------------------------------------------
        double bal = r.totalBalance();
        double monthlyNeed = budget > 0 ? budget : mt.expense;
        if (monthlyNeed > 0 && bal < monthlyNeed * 3 && mt.expense > 0) {
            out.add(new Insight(0, "shield",
                    ar ? "وسادتك النقدية دون 3 أشهر" : "Cash cushion under 3 months",
                    ar ? "رصيد محافظك " + Fmt.money(c, bal) + " مقابل مصروف شهري " + Fmt.money(c, monthlyNeed)
                       + ". المعيار العالمي: 3–6 أشهر. كل شهر، حوّل فرقًا صغيرًا وثابتًا لصندوق الطوارئ."
                       : "Balances " + Fmt.money(c, bal) + " vs monthly spend " + Fmt.money(c, monthlyNeed) + ". Target 3–6 months.",
                    null, -1));
        }

        // 9) رسالة إيجابية عند الانضباط ------------------------------------------
        if (out.isEmpty()) {
            out.add(new Insight(0, "check_circle",
                    ar ? "أداؤك المالي منضبط هذا الشهر 🌟" : "Your money is on track this month 🌟",
                    ar ? "لا تجاوزات ولا تسريبات واضحة. استمر بالتتبع اليومي وحوّل الفائض لهدف ادخار — الانضباط مركّب مثل الفائدة."
                       : "No overspend, no obvious leaks. Keep tracking and move surplus to savings.",
                    null, -1));
        }
        return out;
    }

    // ---------------- مؤشر الصحة المالية ----------------

    public static int healthScore(Context c) {
        Repo r = Repo.get(c);
        Prefs p = Prefs.get(c);
        Calendar now = Calendar.getInstance();
        Models.MonthTotals mt = r.monthTotals(now);
        double incomeRef = mt.income > 0 ? mt.income : p.monthlyIncome();
        int score = 40; // أساس: وجود تتبع

        if (r.hasAnyData()) score += 10;
        // نسبة الادخار
        if (incomeRef > 0) {
            double sr = mt.saved / incomeRef;
            if (sr >= 0.2) score += 20;
            else if (sr >= 0.1) score += 14;
            else if (sr >= 0.05) score += 8;
            // نسبة المصروف للدخل
            double er = mt.expense / incomeRef;
            if (er <= 0.8) score += 10;
            else if (er <= 1.0) score += 4;
        } else if (mt.expense > 0 && p.monthlyBudget() > 0 && mt.expense <= p.monthlyBudget()) {
            score += 12;
        }
        // الديون
        double owed = r.debtsOwedByMe();
        if (owed == 0) score += 12;
        else if (incomeRef > 0 && owed < incomeRef * 3) score += 6;
        // صندوق الطوارئ
        double bal = r.totalBalance();
        double need = p.monthlyBudget() > 0 ? p.monthlyBudget() : mt.expense;
        if (need > 0 && bal >= need * 3) score += 8;
        else if (need > 0 && bal >= need) score += 4;
        return Math.max(5, Math.min(100, score));
    }

    public static String healthLabel(Context c, int score) {
        boolean ar = !Prefs.get(c).language().equals("en");
        if (score >= 80) return ar ? "ممتاز — استمر" : "Excellent — keep going";
        if (score >= 60) return ar ? "جيد — فرص تحسين واضحة" : "Good — clear room to improve";
        if (score >= 40) return ar ? "متوسط — يحتاج انتباهًا" : "Average — needs attention";
        return ar ? "حرج — خطة إنقاذ فورية" : "Critical — act now";
    }

    // ---------------- خطة عمل شخصية ----------------

    public static final class PlanStep {
        public String title, body;

        PlanStep(String t, String b) { title = t; body = b; }
    }

    public static List<PlanStep> personalPlan(Context c) {
        List<PlanStep> plan = new ArrayList<>();
        Repo r = Repo.get(c);
        Prefs p = Prefs.get(c);
        boolean ar = !p.language().equals("en");
        Calendar now = Calendar.getInstance();
        Models.MonthTotals mt = r.monthTotals(now);
        double incomeRef = mt.income > 0 ? mt.income : p.monthlyIncome();
        double budget = p.monthlyBudget();

        if (budget <= 0) {
            double suggested = incomeRef > 0 ? incomeRef * 0.85 : Math.max(mt.expense, 1) * 1.0;
            plan.add(new PlanStep(
                    ar ? "1) ضع سقفًا شهريًا = " + Fmt.money(c, suggested) : "1) Set a monthly cap = " + Fmt.money(c, suggested),
                    ar ? "اقترحناه عند 85٪ من دخلك (أو مصروفك الحالي). السقف هو خط الدفاع الأول ضد الزحف."
                       : "Suggested at 85% of income. The cap is your first defense line."));
        }
        if (incomeRef > 0 && mt.saved < incomeRef * 0.1) {
            double save = Math.max(incomeRef * 0.1, 1);
            plan.add(new PlanStep(
                    ar ? "2) حوّل " + Fmt.money(c, save) + " للادخار يوم الراتب" : "2) Move " + Fmt.money(c, save) + " to savings on payday",
                    ar ? "10٪ كحد أدنى هذا الشهر، تُزاد 1٪ كل شهرين حتى 20٪. سجّلها في مَحلولة ضمن «ادخار واستثمار»."
                       : "Minimum 10% now; add 1% every two months up to 20%."));
        }
        Models.CatTotal top = r.topCatThisMonth();
        if (top != null && mt.expense > 0 && top.total / mt.expense > 0.3) {
            double cut = top.total * 0.2;
            plan.add(new PlanStep(
                    ar ? "3) اخفض «" + Models.cat(top.cat).ar + "» بمقدار " + Fmt.money(c, cut) : "3) Cut '" + Models.cat(top.cat).en + "' by " + Fmt.money(c, cut),
                    ar ? "أكبر فئة لديك هذا الشهر. خفض 20٪ منها لا يشعر به أحد تقريبًا ويوفر لك هذا المبلغ شهريًا."
                       : "Your biggest category. A 20% cut is nearly painless and saves this monthly."));
        }
        double owed = r.debtsOwedByMe();
        if (owed > 0) {
            plan.add(new PlanStep(
                    ar ? "4) خطة سداد: " + Fmt.money(c, owed) + " بطريقة كرة الثلج" : "4) Payoff plan: " + Fmt.money(c, owed) + " via snowball",
                    ar ? "رتّب ديونك بالأصغر مبلغًا، ووجّه أي فائض شهري له مع الحد الأدنى للبقية. كل دين يُغلق يحرر تدفقًا نقديًا للذي بعده."
                       : "Order debts smallest-first; point every surplus at it while paying minimums elsewhere."));
        }
        double bal = r.totalBalance();
        double need = budget > 0 ? budget : mt.expense;
        if (need > 0 && bal < need * 3) {
            plan.add(new PlanStep(
                    ar ? "5) ابنِ صندوق طوارئ حتى " + Fmt.money(c, need * 3) : "5) Build an emergency fund to " + Fmt.money(c, need * 3),
                    ar ? "ثلاثة أشهر من مصروفك في حساب سهل السحب منفصل. قبل أي استثمار أو شراء كبير — هذه وسادتك."
                       : "Three months of spend in a separate liquid account, before investing."));
        }
        plan.add(new PlanStep(
                ar ? "6) اجتماع الـ20 دقيقة نهاية الشهر" : "6) The 20-minute month-end meeting",
                ar ? "راجع لوحة التحكم والفئات والديون، واكتب 3 قرارات فقط للشهر القادم في الملاحظات. القرارات القليلة تُنفَّذ."
                   : "Review dashboard, categories, debts; write exactly 3 decisions for next month in Notes."));
        return plan;
    }
}
