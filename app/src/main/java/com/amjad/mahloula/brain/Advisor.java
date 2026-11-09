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

    /** حارس لحظي: فحوص فورية تُنفذ مباشرة بعد حفظ أي مصروف. */
    public static List<Insight> guardTx(Context c, Models.Tx t) {
        List<Insight> out = new ArrayList<>();
        if (t == null || !t.isExpense()) return out;
        Repo r = Repo.get(c);
        Prefs p = Prefs.get(c);
        boolean ar = !p.language().equals("en");
        Calendar now = Calendar.getInstance();
        Models.MonthTotals mt = r.monthTotals(now);
        double budget = p.monthlyBudget();
        if (budget > 0) {
            if (mt.expense > budget) {
                out.add(new Insight(2, "alert",
                        ar ? "تجاوزت ميزانية الشهر!" : "Monthly budget exceeded!",
                        ar ? "صار صرفك " + Fmt.money(c, mt.expense) + " من سقف " + Fmt.money(c, budget)
                           + ". أوقف الصرف غير الضروري اليوم، وراجع خطتك في خانة تعلّم."
                           : "Spent " + Fmt.money(c, mt.expense) + " of a " + Fmt.money(c, budget)
                           + " cap. Pause non-essentials today.",
                        ar ? "عرض الخطة" : "See plan", TAB_GUIDE));
            } else if (mt.expense >= budget * 0.8) {
                out.add(new Insight(1, "trend_down",
                        ar ? "اقتربت من سقف الميزانية" : "Approaching budget cap",
                        ar ? "استهلكت " + Fmt.percent(c, mt.expense / budget * 100) + " من ميزانيتك، والمتبقي "
                           + Fmt.money(c, budget - mt.expense) + " — وزّعها بحذر على الأيام الباقية."
                           : "Used " + Fmt.percent(c, mt.expense / budget * 100) + " of budget; spend the rest carefully.",
                        null, TAB_DASH));
            }
            if (t.amount > budget * 0.25) {
                out.add(new Insight(2, "pie",
                        ar ? "مصروف ضخم دفعة واحدة" : "Large single expense",
                        ar ? "هذه العملية وحدها " + Fmt.percent(c, t.amount / budget * 100)
                           + " من ميزانية شهر كامل. إن لم تكن ضرورة قصوى، قسّطها أو أجّلها."
                           : "This single entry is " + Fmt.percent(c, t.amount / budget * 100)
                           + " of a whole month's budget.",
                        null, TAB_TX));
            }
        }
        int day = now.get(Calendar.DAY_OF_MONTH);
        double avgDay = day > 1 ? mt.expense / day : mt.expense;
        if (avgDay > 0 && mt.expense - t.amount > 0 && t.amount > avgDay * 3) {
            out.add(new Insight(2, "trend_down",
                    ar ? "مصروف شاذ عن نمطك" : "Anomaly vs your pattern",
                    ar ? "متوسط صرفك اليومي هذا الشهر " + Fmt.money(c, avgDay) + "، وهذه العملية تعادل "
                       + Fmt.digits(c, String.valueOf((int) Math.round(t.amount / avgDay))) + " أضعافه."
                       : "Your daily average is " + Fmt.money(c, avgDay) + "; this entry is several times that.",
                    null, TAB_TX));
        }
        if (out.size() > 2) out = new ArrayList<>(out.subList(0, 2));
        return out;
    }

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
        
        // ================= الدفعة المدرّبة: تحليل أنماط عميق =================
        double incRef = mt.income > 0 ? mt.income : p.monthlyIncome();

        // 1) توقع وتيرة نهاية الشهر
        if (budget > 0 && mt.expense > 0 && dayOfMonth >= 3) {
            double proj = forecastMonth(c);
            if (proj > budget * 1.05) out.add(new Insight(2, "trend_down",
                    ar ? "توقع الوتيرة: تجاوز بنهاية الشهر" : "Pace forecast: month-end overrun",
                    ar ? "على إيقاعك الحالي ستُنهي الشهر عند " + Fmt.money(c, proj) + " مقابل سقف " + Fmt.money(c, budget)
                       + ". خفّف الآن لا في الثامن والعشرين."
                       : "Projected " + Fmt.money(c, proj) + " vs cap " + Fmt.money(c, budget) + ". Slow down now.",
                    ar ? "عرض الشهر" : "View month", TAB_TX));
            else if (proj > budget * 0.9) out.add(new Insight(1, "clock",
                    ar ? "وتيرتك تقترب من السقف" : "Pace nearing the cap",
                    ar ? "التوقع الحالي " + Fmt.money(c, proj) + " — أي نفخة صغيرة تكسر السقف هذا الشهر."
                       : "Projection " + Fmt.money(c, proj) + " — any bump breaks the cap.",
                    null, TAB_DASH));
        }

        // 2) الاشتراكات والمدفوعات المتكررة
        for (String[] sub : detectSubscriptions(c)) out.add(new Insight(1, "refresh",
                ar ? "دفعة متكررة: " + sub[0] : "Recurring: " + sub[0],
                ar ? "تتكرر شهريًا حول " + sub[1] + ". القاعدة الفصلية: كل اشتراك لم يُستخدم شهرًا يُلغى فورًا."
                   : "Repeats monthly around " + sub[1] + ". Quarterly rule: unused a month = cancel.",
                ar ? "راجعها" : "Review", TAB_TX));

        // 3) طفرة فئة مقابل الشهر الماضي
        Calendar prevM = (Calendar) now.clone(); prevM.add(Calendar.MONTH, -1);
        List<Models.CatTotal> cur = r.catTotals(ms.getTimeInMillis(), me.getTimeInMillis(), 0);
        List<Models.CatTotal> prv = r.catTotals(Fmt.startOfMonth(prevM).getTimeInMillis(), Fmt.endOfMonth(prevM).getTimeInMillis(), 0);
        for (Models.CatTotal ct : cur) {
            double pv = 0;
            for (Models.CatTotal q : prv) if (q.cat == ct.cat) pv = q.total;
            if (pv > 0 && ct.total >= pv * 1.5 && mt.expense > 0 && ct.total / mt.expense >= 0.12) {
                out.add(new Insight(1, "pie",
                        ar ? "طفرة فئة: " + Models.cat(ct.cat).ar : "Surge: " + Models.cat(ct.cat).en,
                        ar ? "ارتفعت " + Fmt.percent(c, (ct.total - pv) / pv * 100) + " عن الشهر الماضي ("
                           + Fmt.money(c, pv) + " ← " + Fmt.money(c, ct.total) + "). حاجة دائمة أم موجة عابرة؟"
                           : "Up " + Fmt.percent(c, (ct.total - pv) / pv * 100) + " vs last month. Permanent need or passing wave?",
                        null, TAB_TX));
                break;
            }
        }

        // 4) تركّز الصرف
        if (!cur.isEmpty() && mt.expense > 0 && cur.get(0).total / mt.expense > 0.5) {
            out.add(new Insight(1, "pie",
                    ar ? "تركّز خطِر في الصرف" : "Spending concentration",
                    ar ? "«" + Models.cat(cur.get(0).cat).ar + "» وحدها " + Fmt.percent(c, cur.get(0).total / mt.expense * 100)
                       + " من مصروفك. التنويع يحمي ميزانيتك من أي صدمة في هذه الفئة."
                       : "One category takes " + Fmt.percent(c, cur.get(0).total / mt.expense * 100) + " of spending.",
                    null, TAB_TX));
        }

        // 5) نمط الصرف الليلي
        int night = 0; double nightSum = 0;
        for (Models.Tx t : r.txRange(ms.getTimeInMillis(), me.getTimeInMillis(), 0, null, null)) {
            Calendar tc = Calendar.getInstance(); tc.setTimeInMillis(t.ts);
            if (tc.get(Calendar.HOUR_OF_DAY) < 5) { night++; nightSum += t.amount; }
        }
        if (night >= 3) out.add(new Insight(1, "clock",
                ar ? "نمط صرف ليلي" : "Late-night spending",
                ar ? Fmt.digits(c, String.valueOf(night)) + " عمليات بين منتصف الليل والخامسة فجرًا بمجموع "
                   + Fmt.money(c, nightSum) + ". قرارات الليل الأكثر اندفاعًا — أجّل سلة الشراء للصباح."
                   : night + " late-night operations totaling " + Fmt.money(c, nightSum) + ".",
                null, TAB_TX));

        // 6) الموت بألف مصروف صغير
        if (budget > 0 && mt.expense > 0) {
            int small = 0; double smallSum = 0;
            for (Models.Tx t : r.txRange(ms.getTimeInMillis(), me.getTimeInMillis(), 0, null, null)) {
                if (t.amount < budget * 0.02) { small++; smallSum += t.amount; }
            }
            if (small >= 12 && smallSum / mt.expense >= 0.15) out.add(new Insight(1, "alert",
                    ar ? "الموت بألف مصروف صغير" : "Death by a thousand cuts",
                    ar ? Fmt.digits(c, String.valueOf(small)) + " عملية صغيرة (دون " + Fmt.money(c, budget * 0.02)
                       + ") تشكل " + Fmt.percent(c, smallSum / mt.expense * 100) + " من مصروفك. خصّص مبلغًا نقديًا أسبوعيًا للصغائر."
                       : small + " tiny ops form " + Fmt.percent(c, smallSum / mt.expense * 100) + " of spending.",
                    null, TAB_TX));
        }

        // 7) وسادة الأمان بالأيام
        double avg90 = r.avgDaily(now.getTimeInMillis() - 90L * 86400000L, now.getTimeInMillis());
        if (avg90 > 0) {
            double days = r.totalBalance() / avg90;
            if (days < 7) out.add(new Insight(2, "shield",
                    ar ? "وسادتك تغطي " + Fmt.digits(c, String.valueOf((int) days)) + " أيام فقط" : "Cushion: " + (int) days + " days only",
                    ar ? "رصيدك الحالي يكفي " + Fmt.digits(c, String.valueOf((int) days)) + " أيام من مصروفك. الهدف العالمي: 90 يومًا. ابدأ بتحويل يومي صغير."
                       : "Balance covers " + (int) days + " days of spend. Global target: 90.",
                    ar ? "خطة الادخار" : "Savings plan", TAB_GUIDE));
            else if (days < 30) out.add(new Insight(1, "shield",
                    ar ? "وسادة الأمان قيد البناء" : "Cushion building",
                    ar ? "تغطي " + Fmt.digits(c, String.valueOf((int) days)) + " يومًا — جيدة، لكن الثلاثين يومًا هي حدّ الأمان الأول."
                       : (int) days + " days covered; 30 is the first safety line.",
                    null, TAB_GUIDE));
            else out.add(new Insight(0, "shield",
                    ar ? "وسادة أمان قوية 💪" : "Strong cushion 💪",
                    ar ? "رصيدك يغطي نحو " + Fmt.digits(c, String.valueOf((int) days)) + " يومًا من مصروفك — أفضل من أغلب الناس."
                       : "Covers ~" + (int) days + " days of spend.",
                    null, -1));
        }

        // 8) عبء الديون الشهري
        double paidM = monthDebtPays(c, now);
        if (incRef > 0 && paidM > 0) {
            double br = paidM / incRef;
            if (br > 0.4) out.add(new Insight(2, "handshake",
                    ar ? "عبء ديون مرتفع" : "Heavy debt burden",
                    ar ? "تسديدات هذا الشهر " + Fmt.money(c, paidM) + " = " + Fmt.percent(c, br * 100)
                       + " من دخلك. السقف العالمي الآمن: 35٪. أعد الهيكلة بالأقل فائدة أولًا."
                       : "Debt payments are " + Fmt.percent(c, br * 100) + " of income; safe cap 35%.",
                    ar ? "خطة السداد" : "Payoff plan", TAB_DEBT));
            else if (br > 0.25) out.add(new Insight(1, "handshake",
                    ar ? "عبء الديون يقترب من الحد" : "Debt burden nearing limit",
                    ar ? Fmt.percent(c, br * 100) + " من دخلك يذهب للتسديد. لا تلتزم بأي دين جديد قبل خفضه."
                       : Fmt.percent(c, br * 100) + " of income goes to repayments.",
                    null, TAB_DEBT));
        }

        // 9) سلسلة الالتزام بالتسجيل
        int streak = loggingStreak(c);
        if (streak >= 7) out.add(new Insight(0, "check_circle",
                ar ? "🔥 سلسلة " + Fmt.digits(c, String.valueOf(streak)) + " أيام تسجيل" : "🔥 " + streak + "-day logging streak",
                ar ? "الانتظام أغلى عادة في المالية الشخصية — سلسلتك الحالية تمنح بياناتك دقة المستشار كاملة."
                   : "Consistency keeps your advisor sharp.",
                null, -1));

        // 10) اتجاه معدل الادخار
        if (incRef > 0) {
            double srNow = mt.saved / incRef;
            double srPrev = r.monthTotals(prevM).saved / incRef;
            if (srNow - srPrev > 0.03) out.add(new Insight(0, "trend_up",
                    ar ? "معدل ادخارك يتحسن 📈" : "Savings rate rising 📈",
                    ar ? "من " + Fmt.percent(c, srPrev * 100) + " الشهر الماضي إلى " + Fmt.percent(c, srNow * 100) + " هذا الشهر. استمر."
                       : "From " + Fmt.percent(c, srPrev * 100) + " to " + Fmt.percent(c, srNow * 100) + ".",
                    null, -1));
            else if (srPrev - srNow > 0.05 && dayOfMonth > 7) out.add(new Insight(1, "trend_down",
                    ar ? "معدل الادخار يتراجع" : "Savings rate slipping",
                    ar ? "هبط من " + Fmt.percent(c, srPrev * 100) + " إلى " + Fmt.percent(c, srNow * 100) + ". أعد تحويل يوم الراتب أولًا."
                       : "Dropped from " + Fmt.percent(c, srPrev * 100) + " to " + Fmt.percent(c, srNow * 100) + ".",
                    null, TAB_DASH));
        }

        // 11) وتيرة الأهداف مقابل مواعيدها
        for (Models.Goal g : r.goals()) {
            if (g.due > System.currentTimeMillis() && g.target > g.saved) {
                double months = (g.due - System.currentTimeMillis()) / (30.44 * 86400000.0);
                if (months >= 0.5 && incRef > 0) {
                    double needM = (g.target - g.saved) / months;
                    if (needM > incRef * 0.25) out.add(new Insight(1, "target",
                            ar ? "وتيرة هدف: " + g.title : "Goal pace: " + g.title,
                            ar ? "بلوغه في موعده يتطلب " + Fmt.money(c, needM) + " شهريًا — فوق ربع دخلك. مدّد الموعد أو خفّض الهدف."
                               : "Needs " + Fmt.money(c, needM) + "/month — over a quarter of income.",
                            null, TAB_DASH));
                }
            }
        }


        // ================= الدفعة الثالثة: ذكاء شخصي أعمق =================
        // 12) تضخم يوم الراتب
        int payday = detectPayday(c);
        if (payday > 0 && mt.expense > 0) {
            double near = 0;
            for (Models.Tx t : r.txRange(ms.getTimeInMillis(), me.getTimeInMillis(), 0, null, null)) {
                Calendar tc = Calendar.getInstance(); tc.setTimeInMillis(t.ts);
                int d = tc.get(Calendar.DAY_OF_MONTH);
                if (d >= payday && d <= payday + 2) near += t.amount;
            }
            if (near / mt.expense > 0.4) out.add(new Insight(1, "trend_up",
                    ar ? "تضخم يوم الراتب" : "Payday inflation",
                    ar ? Fmt.percent(c, near / mt.expense * 100) + " من مصروف الشهر يُصرف في أيام راتبك الثلاثة الأولى ("
                       + Fmt.digits(c, String.valueOf(payday)) + " من كل شهر). الفرحة الأولى أغلى فرحة — أجّل أكبر شراء لليوم الخامس."
                       : Fmt.percent(c, near / mt.expense * 100) + " of monthly spend lands in the first 3 days after payday.",
                    null, TAB_TX));
        }

        // 13) خرق المغلفات المقترحة مبكرًا
        int dayNow = now.get(Calendar.DAY_OF_MONTH);
        for (Env e : envelopes(c)) {
            if (e.spent > e.cap && dayNow <= 20) {
                out.add(new Insight(1, "pie",
                        ar ? "مغلف «" + Models.cat(e.cat).ar + "» تجاوز اقتراحه" : "Envelope '" + Models.cat(e.cat).en + "' over",
                        ar ? "صرفت " + Fmt.money(c, e.spent) + " مقابل مغلف مقترح " + Fmt.money(c, e.cap) + " (مبني على نمطك في 90 يومًا × 0.9)."
                           : "Spent " + Fmt.money(c, e.spent) + " vs suggested " + Fmt.money(c, e.cap) + ".",
                        ar ? "عرض المغلفات" : "Envelopes", TAB_GUIDE));
                break;
            }
        }

        // 14) شذوذ شخصي مقابل وسيط كل فئة
        {
            java.util.HashMap<Integer, java.util.ArrayList<Double>> hist = new java.util.HashMap<>();
            for (Models.Tx t : r.allTx()) if (t.isExpense()) {
                java.util.ArrayList<Double> l = hist.get(t.cat);
                if (l == null) { l = new java.util.ArrayList<>(); hist.put(t.cat, l); }
                l.add(t.amount);
            }
            for (Models.Tx t : r.txRange(ms.getTimeInMillis(), me.getTimeInMillis(), 0, null, null)) {
                java.util.ArrayList<Double> l = hist.get(t.cat);
                if (l == null || l.size() < 5) continue;
                java.util.Collections.sort(l);
                double med = l.get(l.size() / 2);
                if (med > 0 && t.amount > med * 4) {
                    out.add(new Insight(1, "alert",
                            ar ? "عملية غير معتادة منك" : "Unusual for you",
                            ar ? "«" + (t.note == null || t.note.isEmpty() ? Models.cat(t.cat).ar : t.note) + "» بمبلغ "
                               + Fmt.money(c, t.amount) + " بينما وسيطك في هذه الفئة " + Fmt.money(c, med) + ". استثناء؟ سجّل سببه في الملاحظات."
                               : "This entry is 4x your category median.",
                            null, TAB_NOTES));
                    break;
                }
            }
        }

        // 15) تنويع مصادر الدخل
        if (mt.income > 0) {
            java.util.HashSet<String> src = new java.util.HashSet<>();
            for (Models.Tx t : r.txRange(ms.getTimeInMillis(), me.getTimeInMillis(), 1, null, null))
                src.add(t.note == null || t.note.isEmpty() ? "-" : t.note);
            if (src.size() == 1) out.add(new Insight(0, "trend_up",
                    ar ? "دخل الشهر من مصدر واحد" : "Single income source",
                    ar ? "كل بيض الشهر في سلة واحدة. درس الأكاديمية: الدخل الجانبي الآمن يبدأ بمهارة تملكها وساعتين مساءً."
                       : "All income from one source this month.",
                    ar ? "درس الدخل الجانبي" : "Side-income lesson", TAB_GUIDE));
        }

        // 16) حصة نهاية الأسبوع
        if (mt.expense > 0) {
            double we = 0;
            for (Models.Tx t : r.txRange(ms.getTimeInMillis(), me.getTimeInMillis(), 0, null, null)) {
                Calendar tc = Calendar.getInstance(); tc.setTimeInMillis(t.ts);
                int dw = tc.get(Calendar.DAY_OF_WEEK);
                if (dw == Calendar.FRIDAY || dw == Calendar.SATURDAY) we += t.amount;
            }
            if (we / mt.expense > 0.45) out.add(new Insight(0, "pie",
                    ar ? "نهايتك الأسبوعية مكلفة" : "Pricey weekends",
                    ar ? Fmt.percent(c, we / mt.expense * 100) + " من مصروفك يقع في الخميس-السبت. خطّط متعة مجانية واحدة كل أسبوع لتستمتع بلا ندم."
                       : Fmt.percent(c, we / mt.expense * 100) + " of spend lands on weekends.",
                    null, TAB_GUIDE));
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
        // الانتظام بالتسجيل
        int streak = loggingStreak(c);
        if (streak >= 7) score += 10;
        else if (streak >= 3) score += 5;
        // عبء الديون الشهري
        double paidM = monthDebtPays(c, now);
        if (incomeRef > 0 && paidM > 0) {
            double br = paidM / incomeRef;
            if (br <= 0.25) score += 5;
            else if (br > 0.4) score -= 8;
        }
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
        double proj = forecastMonth(c);
        if (budget > 0 && proj > budget * 1.05) {
            plan.add(new PlanStep(
                    ar ? "6) أغلق فجوة الوتيرة: " + Fmt.money(c, proj - budget) : "6) Close the pace gap: " + Fmt.money(c, proj - budget),
                    ar ? "توقع نهاية الشهر يتجاوز سقفك بهذا المقدار. وزّع الخفض على أكبر ثلاث فئات هذا الأسبوع، لا على الكل."
                       : "Projection exceeds cap by this. Spread the cut over your top 3 categories this week."));
        }
        List<String[]> subs = detectSubscriptions(c);
        if (!subs.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (String[] q : subs) names.append(q[0]).append("، ");
            plan.add(new PlanStep(
                    ar ? "7) دقّق " + Fmt.digits(c, String.valueOf(subs.size())) + " دفعات متكررة" : "7) Audit " + subs.size() + " recurring payments",
                    ar ? "رصدنا: " + names.toString() + ". كل بند لم تستفد منه شهرًا كاملًا يُلغى اليوم."
                       : "Detected: " + names.toString() + ". Cancel anything unused for a month."));
        }
        plan.add(new PlanStep(
                ar ? "8) اجتماع الـ20 دقيقة نهاية الشهر" : "8) The 20-minute month-end meeting",
                ar ? "راجع لوحة التحكم والفئات والديون، واكتب 3 قرارات فقط للشهر القادم في الملاحظات. القرارات القليلة تُنفَّذ."
                   : "Review dashboard, categories, debts; write exactly 3 decisions for next month in Notes."));
        return plan;
    }

    /** سلسلة الأيام المتتالية المسجلة حتى اليوم. */
    public static int loggingStreak(Context c) {
        java.util.HashSet<Long> days = new java.util.HashSet<>();
        for (Models.Tx t : Repo.get(c).allTx()) days.add(Fmt.dayKey(t.ts));
        int streak = 0;
        Calendar d = Calendar.getInstance();
        while (days.contains(Fmt.dayKey(d.getTimeInMillis()))) { streak++; d.add(Calendar.DAY_OF_MONTH, -1); }
        return streak;
    }

    /** مجموع تسديدات الديون خلال شهر معين. */
    public static double monthDebtPays(Context c, Calendar month) {
        Repo r = Repo.get(c);
        double sum = 0;
        long s0 = Fmt.startOfMonth(month).getTimeInMillis(), e0 = Fmt.endOfMonth(month).getTimeInMillis();
        for (Models.Debt d : r.debts())
            for (Models.DebtPay pay : r.debtPays(d.id))
                if (pay.ts >= s0 && pay.ts <= e0) sum += pay.amount;
        return sum;
    }

    /** توقع مصروف نهاية الشهر على الوتيرة الحالية. */
    public static double forecastMonth(Context c) {
        Repo r = Repo.get(c);
        Calendar now = Calendar.getInstance();
        Models.MonthTotals mt = r.monthTotals(now);
        int day = now.get(Calendar.DAY_OF_MONTH);
        int dim = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        return day > 0 ? mt.expense / day * dim : mt.expense;
    }

    /** يكتشف المدفوعات الشهرية المتكررة (تكراران بفواصل ~30 يومًا). */
    public static List<String[]> detectSubscriptions(Context c) {
        List<String[]> out = new ArrayList<>();
        List<Models.Tx> all = Repo.get(c).allTx();
        for (int i = 0; i < all.size() && out.size() < 3; i++) {
            Models.Tx a = all.get(i);
            if (!a.isExpense()) continue;
            int hits = 1; double sum = a.amount;
            for (Models.Tx b : all) {
                if (b.id == a.id || !b.isExpense() || b.cat != a.cat) continue;
                if (Math.abs(b.amount - a.amount) > Math.max(1, a.amount * 0.05)) continue;
                double gap = Math.abs(b.ts - a.ts) / 86400000.0;
                if (gap >= 24 && gap <= 37) { hits++; sum += b.amount; }
            }
            if (hits >= 2) {
                String label = a.note != null && !a.note.isEmpty() ? a.note : Models.cat(a.cat).ar;
                boolean dup = false;
                for (String[] q : out) if (q[0].equals(label)) dup = true;
                if (!dup) out.add(new String[]{label, Fmt.money(c, sum / hits)});
            }
        }
        return out;
    }

    /** يوم الراتب الأكثر تكرارًا (0 إن لم يُكتشف). */
    public static int detectPayday(Context c) {
        int[] freq = new int[32];
        for (Models.Tx t : Repo.get(c).allTx()) if (!t.isExpense()) {
            Calendar d = Calendar.getInstance(); d.setTimeInMillis(t.ts);
            freq[d.get(Calendar.DAY_OF_MONTH)]++;
        }
        int best = 0, bd = 0;
        for (int i = 1; i <= 31; i++) if (freq[i] > best) { best = freq[i]; bd = i; }
        return best >= 2 ? bd : 0;
    }

    /** مغلف مقترح لكل فئة: متوسط 90 يومًا × 0.9 مع صرف الشهر الحالي. */
    public static final class Env {
        public int cat; public double cap; public double spent;
    }

    public static List<Env> envelopes(Context c) {
        Repo r = Repo.get(c);
        Calendar now = Calendar.getInstance();
        long to = now.getTimeInMillis();
        long from = to - 90L * 86400000L;
        List<Models.CatTotal> base = r.catTotals(from, to, 0);
        List<Models.CatTotal> cur = r.catTotals(Fmt.startOfMonth(now).getTimeInMillis(), Fmt.endOfMonth(now).getTimeInMillis(), 0);
        List<Env> out = new ArrayList<>();
        for (Models.CatTotal ct : base) {
            double cap = ct.total / 3.0 * 0.9;
            if (cap <= 0) continue;
            double spent = 0;
            for (Models.CatTotal q : cur) if (q.cat == ct.cat) spent = q.total;
            Env e = new Env(); e.cat = ct.cat; e.cap = cap; e.spent = spent;
            out.add(e);
        }
        java.util.Collections.sort(out, (a, b) -> Double.compare(b.cap, a.cap));
        if (out.size() > 5) out = new ArrayList<>(out.subList(0, 5));
        return out;
    }
}
