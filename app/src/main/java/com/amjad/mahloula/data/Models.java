package com.amjad.mahloula.data;

import com.amjad.mahloula.R;

import java.util.ArrayList;
import java.util.List;

/** نماذج البيانات + التصنيفات الثابتة بهوية لونية وأيقونات. */
public final class Models {

    private Models() {}

    // ---------------- التصنيفات ----------------

    public static final class Cat {
        public final int id;
        public final String ar;
        public final String en;
        public final int icon;
        public final int color;

        Cat(int id, String ar, String en, int icon, int color) {
            this.id = id; this.ar = ar; this.en = en; this.icon = icon; this.color = color;
        }
    }

    public static final List<Cat> CATS = new ArrayList<>();

    static {
        CATS.add(new Cat(1, "طعام وتموين", "Food", R.drawable.ic_food, R.color.cat_food));
        CATS.add(new Cat(2, "مواصلات وسيارة", "Transport", R.drawable.ic_car, R.color.cat_transport));
        CATS.add(new Cat(3, "منزل وسكن", "Home", R.drawable.ic_home, R.color.cat_home));
        CATS.add(new Cat(4, "فواتير وخدمات", "Bills", R.drawable.ic_receipt, R.color.cat_bills));
        CATS.add(new Cat(5, "صحة", "Health", R.drawable.ic_health, R.color.cat_health));
        CATS.add(new Cat(6, "تعليم", "Education", R.drawable.ic_cap, R.color.cat_edu));
        CATS.add(new Cat(7, "تسوق وملابس", "Shopping", R.drawable.ic_bag, R.color.cat_shop));
        CATS.add(new Cat(8, "ترفيه", "Fun", R.drawable.ic_fun, R.color.cat_fun));
        CATS.add(new Cat(9, "عائلة وأولاد", "Family", R.drawable.ic_family, R.color.cat_family));
        CATS.add(new Cat(10, "ادخار واستثمار", "Savings", R.drawable.ic_piggy, R.color.cat_save));
        CATS.add(new Cat(11, "تسديد ديون", "Debts", R.drawable.ic_handshake, R.color.cat_debt));
        CATS.add(new Cat(12, "أخرى", "Other", R.drawable.ic_dots, R.color.cat_other));
    }

    public static Cat cat(int id) {
        for (Cat c : CATS) if (c.id == id) return c;
        return CATS.get(CATS.size() - 1);
    }

    public static final String[] INCOME_SOURCES_AR = {"راتب", "عمل حر", "مشروع خاص", "هدية", "استثمار", "أخرى"};
    public static final String[] INCOME_SOURCES_EN = {"Salary", "Freelance", "Business", "Gift", "Investment", "Other"};
    public static final String[] PAY_METHODS_AR = {"نقدًا", "بطاقة", "تحويل", "محفظة إلكترونية"};
    public static final String[] PAY_METHODS_EN = {"Cash", "Card", "Transfer", "E-wallet"};

    // ---------------- الجداول ----------------

    public static final class Tx {
        public long id;
        public int kind;          // 0 مصروف، 1 دخل
        public double amount;
        public int cat;
        public long wallet;
        public String note = "";
        public String method = "";
        public long ts;

        public boolean isExpense() { return kind == 0; }
    }

    public static final class Debt {
        public long id;
        public int kind;          // 0 عليّ (أدفع)، 1 لي (آخذ)
        public String person = "";
        public String title = "";
        public double total;
        public long due;          // 0 = بدون موعد
        public String note = "";
        public long created;

        public boolean isMine() { return kind == 1; }
    }

    public static final class DebtPay {
        public long id;
        public long debt;
        public double amount;
        public long ts;
        public String note = "";
    }

    public static final class Note {
        public long id;
        public String title = "";
        public String body = "";
        public int color;         // فهرس لوحة ألوان الملاحظات
        public boolean pinned;
        public long ts;
        public long upd;
    }

    public static final class Wallet {
        public long id;
        public String name = "";
        public int kind;          // 0 نقد، 1 بطاقة، 2 بنك، 3 محفظة إلكترونية
        public double initial;
        public boolean def;

        public int icon() {
            switch (kind) {
                case 1: return R.drawable.ic_wallet;
                case 2: return R.drawable.ic_bars;
                case 3: return R.drawable.ic_save_disk;
                default: return R.drawable.ic_coins;
            }
        }
    }

    public static final class Goal {
        public long id;
        public String title = "";
        public double target;
        public double saved;
        public long due;
    }

    /** مجموع شهري بسيط. */
    public static final class MonthTotals {
        public double income;
        public double expense;
        public double saved;      // تحويلات لتصنيف الادخار
        public double debtPaid;   // تسديدات الديون ضمن المصروف

        public double net() { return income - expense; }
    }

    public static final class DayPoint {
        public long ts;
        public double value;

        public DayPoint(long ts, double value) { this.ts = ts; this.value = value; }
    }

    public static final class CatTotal {
        public int cat;
        public double total;
        public int count;

        public CatTotal(int cat, double total, int count) { this.cat = cat; this.total = total; this.count = count; }
    }
}
