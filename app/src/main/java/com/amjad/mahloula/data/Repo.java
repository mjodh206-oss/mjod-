package com.amjad.mahloula.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.amjad.mahloula.util.Fmt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** المستودع: كل عمليات القراءة/الكتابة + المجاميع التحليلية. */
public final class Repo {

    private static Repo sInstance;

    public static synchronized Repo get(Context c) {
        if (sInstance == null) sInstance = new Repo(c.getApplicationContext());
        return sInstance;
    }

    // ------- ناقل تغييرات بسيط (يحّدث الواجهات حيًّا) -------
    public interface OnChange { void onChanged(); }

    private final List<OnChange> listeners = new ArrayList<>();

    public void listen(OnChange l) { if (!listeners.contains(l)) listeners.add(l); }

    public void unlisten(OnChange l) { listeners.remove(l); }

    public void notifyChanged() {
        for (int i = listeners.size() - 1; i >= 0; i--) listeners.get(i).onChanged();
    }

    private final Db db;

    private Repo(Context c) { db = Db.get(c); }

    // ================= العمليات (مسحوبات / دخول) =================

    public long addTx(Models.Tx t) {
        ContentValues v = new ContentValues();
        v.put("kind", t.kind);
        v.put("amount", t.amount);
        v.put("cat", t.cat);
        v.put("wallet", t.wallet);
        v.put("note", t.note);
        v.put("method", t.method);
        v.put("ts", t.ts);
        long id = db.getWritableDatabase().insert("tx", null, v);
        notifyChanged();
        return id;
    }

    public void updateTx(Models.Tx t) {
        ContentValues v = new ContentValues();
        v.put("kind", t.kind);
        v.put("amount", t.amount);
        v.put("cat", t.cat);
        v.put("wallet", t.wallet);
        v.put("note", t.note);
        v.put("method", t.method);
        v.put("ts", t.ts);
        db.getWritableDatabase().update("tx", v, "id=?", new String[]{String.valueOf(t.id)});
        notifyChanged();
    }

    public void deleteTx(long id) {
        db.getWritableDatabase().delete("tx", "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    public List<Models.Tx> txRange(long from, long to, Integer kind, Integer cat, String query) {
        List<Models.Tx> out = new ArrayList<>();
        StringBuilder q = new StringBuilder("ts BETWEEN ? AND ?");
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(from));
        args.add(String.valueOf(to));
        if (kind != null) { q.append(" AND kind=?"); args.add(String.valueOf(kind)); }
        if (cat != null) { q.append(" AND cat=?"); args.add(String.valueOf(cat)); }
        if (query != null && !query.trim().isEmpty()) {
            q.append(" AND note LIKE ?");
            args.add("%" + query.trim() + "%");
        }
        Cursor c = db.getReadableDatabase().query("tx", null, q.toString(),
                args.toArray(new String[0]), null, null, "ts DESC");
        while (c.moveToNext()) out.add(readTx(c));
        c.close();
        return out;
    }

    public List<Models.Tx> allTx() {
        return txRange(0, Long.MAX_VALUE, null, null, null);
    }

    private Models.Tx readTx(Cursor c) {
        Models.Tx t = new Models.Tx();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.kind = c.getInt(c.getColumnIndexOrThrow("kind"));
        t.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        t.cat = c.getInt(c.getColumnIndexOrThrow("cat"));
        t.wallet = c.getLong(c.getColumnIndexOrThrow("wallet"));
        t.note = c.getString(c.getColumnIndexOrThrow("note"));
        t.method = c.getString(c.getColumnIndexOrThrow("method"));
        t.ts = c.getLong(c.getColumnIndexOrThrow("ts"));
        return t;
    }

    // ================= المجاميع =================

    public Models.MonthTotals monthTotals(Calendar month) {
        long from = Fmt.startOfMonth(month).getTimeInMillis();
        long to = Fmt.endOfMonth(month).getTimeInMillis();
        Models.MonthTotals mt = new Models.MonthTotals();
        for (Models.Tx t : txRange(from, to, null, null, null)) {
            if (t.isExpense()) {
                mt.expense += t.amount;
                if (t.cat == 10) mt.saved += t.amount;
                if (t.cat == 11) mt.debtPaid += t.amount;
            } else mt.income += t.amount;
        }
        return mt;
    }

    public double totalExpense(long from, long to) {
        double s = 0;
        for (Models.Tx t : txRange(from, to, 0, null, null)) s += t.amount;
        return s;
    }

    public double totalIncome(long from, long to) {
        double s = 0;
        for (Models.Tx t : txRange(from, to, 1, null, null)) s += t.amount;
        return s;
    }

    public List<Models.CatTotal> catTotals(long from, long to, int kind) {
        Map<Integer, Models.CatTotal> map = new HashMap<>();
        for (Models.Tx t : txRange(from, to, kind, null, null)) {
            Models.CatTotal ct = map.get(t.cat);
            if (ct == null) { ct = new Models.CatTotal(t.cat, 0, 0); map.put(t.cat, ct); }
            ct.total += t.amount;
            ct.count++;
        }
        List<Models.CatTotal> out = new ArrayList<>(map.values());
        Collections.sort(out, (a, b) -> Double.compare(b.total, a.total));
        return out;
    }

    /** سلسلة مصروف يومية لشهر كامل. */
    public List<Models.DayPoint> dailySeries(Calendar month) {
        Calendar s = Fmt.startOfMonth(month);
        Calendar e = Fmt.endOfMonth(month);
        Map<Long, Double> byDay = new HashMap<>();
        for (Models.Tx t : txRange(s.getTimeInMillis(), e.getTimeInMillis(), 0, null, null)) {
            long k = Fmt.dayKey(t.ts);
            Double cur = byDay.get(k);
            byDay.put(k, (cur == null ? 0 : cur) + t.amount);
        }
        List<Models.DayPoint> out = new ArrayList<>();
        Calendar cur = (Calendar) s.clone();
        while (!cur.after(e)) {
            long k = cur.getTimeInMillis();
            Double v = byDay.get(k);
            out.add(new Models.DayPoint(k, v == null ? 0 : v));
            cur.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    /** متوسط الإنفاق اليومي خلال فترة. */
    public double avgDaily(long from, long to) {
        double total = totalExpense(from, to);
        long days = Math.max(1, (to - from) / 86400000L);
        return total / days;
    }

    // ================= المحافظ =================

    public List<Models.Wallet> wallets() {
        List<Models.Wallet> out = new ArrayList<>();
        Cursor c = db.getReadableDatabase().query("wallets", null, null, null, null, null, "def DESC, id ASC");
        while (c.moveToNext()) {
            Models.Wallet w = new Models.Wallet();
            w.id = c.getLong(c.getColumnIndexOrThrow("id"));
            w.name = c.getString(c.getColumnIndexOrThrow("name"));
            w.kind = c.getInt(c.getColumnIndexOrThrow("kind"));
            w.initial = c.getDouble(c.getColumnIndexOrThrow("initial"));
            w.def = c.getInt(c.getColumnIndexOrThrow("def")) == 1;
            out.add(w);
        }
        c.close();
        return out;
    }

    public Models.Wallet defaultWallet() {
        List<Models.Wallet> ws = wallets();
        for (Models.Wallet w : ws) if (w.def) return w;
        return ws.isEmpty() ? null : ws.get(0);
    }

    public long addWallet(Models.Wallet w) {
        ContentValues v = new ContentValues();
        v.put("name", w.name);
        v.put("kind", w.kind);
        v.put("initial", w.initial);
        v.put("def", w.def ? 1 : 0);
        long id = db.getWritableDatabase().insert("wallets", null, v);
        notifyChanged();
        return id;
    }

    public void updateWallet(Models.Wallet w) {
        ContentValues v = new ContentValues();
        v.put("name", w.name);
        v.put("kind", w.kind);
        v.put("initial", w.initial);
        v.put("def", w.def ? 1 : 0);
        db.getWritableDatabase().update("wallets", v, "id=?", new String[]{String.valueOf(w.id)});
        notifyChanged();
    }

    public void deleteWallet(long id) {
        if (wallets().size() <= 1) return; // لا نحذف آخر محفظة
        SQLiteDatabase w = db.getWritableDatabase();
        w.delete("wallets", "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    public double walletBalance(long walletId) {
        double bal = 0;
        Cursor c = db.getReadableDatabase().query("wallets", new String[]{"initial"},
                "id=?", new String[]{String.valueOf(walletId)}, null, null, null);
        if (c.moveToFirst()) bal = c.getDouble(0);
        c.close();
        Cursor t = db.getReadableDatabase().query("tx", new String[]{"kind", "amount"},
                "wallet=?", new String[]{String.valueOf(walletId)}, null, null, null);
        while (t.moveToNext()) {
            if (t.getInt(0) == 1) bal += t.getDouble(1);
            else bal -= t.getDouble(1);
        }
        t.close();
        return bal;
    }

    public double totalBalance() {
        double s = 0;
        for (Models.Wallet w : wallets()) s += walletBalance(w.id);
        return s;
    }

    // ================= الديون =================

    public List<Models.Debt> debts() {
        List<Models.Debt> out = new ArrayList<>();
        Cursor c = db.getReadableDatabase().query("debts", null, null, null, null, null, "kind ASC, due ASC, id DESC");
        while (c.moveToNext()) {
            Models.Debt d = new Models.Debt();
            d.id = c.getLong(c.getColumnIndexOrThrow("id"));
            d.kind = c.getInt(c.getColumnIndexOrThrow("kind"));
            d.person = c.getString(c.getColumnIndexOrThrow("person"));
            d.title = c.getString(c.getColumnIndexOrThrow("title"));
            d.total = c.getDouble(c.getColumnIndexOrThrow("total"));
            d.due = c.getLong(c.getColumnIndexOrThrow("due"));
            d.note = c.getString(c.getColumnIndexOrThrow("note"));
            d.created = c.getLong(c.getColumnIndexOrThrow("created"));
            out.add(d);
        }
        c.close();
        return out;
    }

    public long addDebt(Models.Debt d) {
        ContentValues v = new ContentValues();
        v.put("kind", d.kind);
        v.put("person", d.person);
        v.put("title", d.title);
        v.put("total", d.total);
        v.put("due", d.due);
        v.put("note", d.note);
        v.put("created", System.currentTimeMillis());
        long id = db.getWritableDatabase().insert("debts", null, v);
        notifyChanged();
        return id;
    }

    public void updateDebt(Models.Debt d) {
        ContentValues v = new ContentValues();
        v.put("kind", d.kind);
        v.put("person", d.person);
        v.put("title", d.title);
        v.put("total", d.total);
        v.put("due", d.due);
        v.put("note", d.note);
        db.getWritableDatabase().update("debts", v, "id=?", new String[]{String.valueOf(d.id)});
        notifyChanged();
    }

    public void deleteDebt(long id) {
        SQLiteDatabase w = db.getWritableDatabase();
        w.delete("debt_pays", "debt=?", new String[]{String.valueOf(id)});
        w.delete("debts", "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    public double debtPaid(long debtId) {
        double s = 0;
        Cursor c = db.getReadableDatabase().query("debt_pays", new String[]{"amount"},
                "debt=?", new String[]{String.valueOf(debtId)}, null, null, null);
        while (c.moveToNext()) s += c.getDouble(0);
        c.close();
        return s;
    }

    public List<Models.DebtPay> debtPays(long debtId) {
        List<Models.DebtPay> out = new ArrayList<>();
        Cursor c = db.getReadableDatabase().query("debt_pays", null,
                "debt=?", new String[]{String.valueOf(debtId)}, null, null, "ts DESC");
        while (c.moveToNext()) {
            Models.DebtPay p = new Models.DebtPay();
            p.id = c.getLong(c.getColumnIndexOrThrow("id"));
            p.debt = c.getLong(c.getColumnIndexOrThrow("debt"));
            p.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
            p.ts = c.getLong(c.getColumnIndexOrThrow("ts"));
            p.note = c.getString(c.getColumnIndexOrThrow("note"));
            out.add(p);
        }
        c.close();
        return out;
    }

    public void addDebtPay(long debtId, double amount, String note) {
        ContentValues v = new ContentValues();
        v.put("debt", debtId);
        v.put("amount", amount);
        v.put("ts", System.currentTimeMillis());
        v.put("note", note == null ? "" : note);
        db.getWritableDatabase().insert("debt_pays", null, v);
        notifyChanged();
    }

    public void deleteDebtPay(long id) {
        db.getWritableDatabase().delete("debt_pays", "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    public double debtsOwedByMe() {
        double s = 0;
        for (Models.Debt d : debts()) if (!d.isMine()) s += Math.max(0, d.total - debtPaid(d.id));
        return s;
    }

    public double debtsOwedToMe() {
        double s = 0;
        for (Models.Debt d : debts()) if (d.isMine()) s += Math.max(0, d.total - debtPaid(d.id));
        return s;
    }

    // ================= الملاحظات =================

    public List<Models.Note> notes(String query) {
        List<Models.Note> out = new ArrayList<>();
        String sel = null;
        String[] args = null;
        if (query != null && !query.trim().isEmpty()) {
            sel = "title LIKE ? OR body LIKE ?";
            args = new String[]{"%" + query.trim() + "%", "%" + query.trim() + "%"};
        }
        Cursor c = db.getReadableDatabase().query("notes", null, sel, args, null, null, "pinned DESC, upd DESC");
        while (c.moveToNext()) {
            Models.Note n = new Models.Note();
            n.id = c.getLong(c.getColumnIndexOrThrow("id"));
            n.title = c.getString(c.getColumnIndexOrThrow("title"));
            n.body = c.getString(c.getColumnIndexOrThrow("body"));
            n.color = c.getInt(c.getColumnIndexOrThrow("color"));
            n.pinned = c.getInt(c.getColumnIndexOrThrow("pinned")) == 1;
            n.ts = c.getLong(c.getColumnIndexOrThrow("ts"));
            n.upd = c.getLong(c.getColumnIndexOrThrow("upd"));
            out.add(n);
        }
        c.close();
        return out;
    }

    public long addNote(Models.Note n) {
        ContentValues v = new ContentValues();
        v.put("title", n.title);
        v.put("body", n.body);
        v.put("color", n.color);
        v.put("pinned", n.pinned ? 1 : 0);
        v.put("ts", System.currentTimeMillis());
        v.put("upd", System.currentTimeMillis());
        long id = db.getWritableDatabase().insert("notes", null, v);
        notifyChanged();
        return id;
    }

    public void updateNote(Models.Note n) {
        ContentValues v = new ContentValues();
        v.put("title", n.title);
        v.put("body", n.body);
        v.put("color", n.color);
        v.put("pinned", n.pinned ? 1 : 0);
        v.put("upd", System.currentTimeMillis());
        db.getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(n.id)});
        notifyChanged();
    }

    public void deleteNote(long id) {
        db.getWritableDatabase().delete("notes", "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    // ================= الأهداف =================

    public List<Models.Goal> goals() {
        List<Models.Goal> out = new ArrayList<>();
        Cursor c = db.getReadableDatabase().query("goals", null, null, null, null, null, "id ASC");
        while (c.moveToNext()) {
            Models.Goal g = new Models.Goal();
            g.id = c.getLong(c.getColumnIndexOrThrow("id"));
            g.title = c.getString(c.getColumnIndexOrThrow("title"));
            g.target = c.getDouble(c.getColumnIndexOrThrow("target"));
            g.saved = c.getDouble(c.getColumnIndexOrThrow("saved"));
            g.due = c.getLong(c.getColumnIndexOrThrow("due"));
            out.add(g);
        }
        c.close();
        return out;
    }

    public long addGoal(Models.Goal g) {
        ContentValues v = new ContentValues();
        v.put("title", g.title);
        v.put("target", g.target);
        v.put("saved", g.saved);
        v.put("due", g.due);
        long id = db.getWritableDatabase().insert("goals", null, v);
        notifyChanged();
        return id;
    }

    public void updateGoal(Models.Goal g) {
        ContentValues v = new ContentValues();
        v.put("title", g.title);
        v.put("target", g.target);
        v.put("saved", g.saved);
        v.put("due", g.due);
        db.getWritableDatabase().update("goals", v, "id=?", new String[]{String.valueOf(g.id)});
        notifyChanged();
    }

    public void deleteGoal(long id) {
        db.getWritableDatabase().delete("goals", "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    // ================= إحصاءات للمستشار المالي =================

    /** أعلى فئة إنفاق هذا الشهر. */
    public Models.CatTotal topCatThisMonth() {
        List<Models.CatTotal> l = catTotals(
                Fmt.startOfMonth(Calendar.getInstance()).getTimeInMillis(),
                Fmt.endOfMonth(Calendar.getInstance()).getTimeInMillis(), 0);
        return l.isEmpty() ? null : l.get(0);
    }

    /** أكبر عملية خلال فترة. */
    public Models.Tx biggestTx(long from, long to) {
        Models.Tx best = null;
        for (Models.Tx t : txRange(from, to, 0, null, null)) {
            if (best == null || t.amount > best.amount) best = t;
        }
        return best;
    }

    /** عمليات متكررة بنفس المبلغ (اشتراكات/التزامات ثابتة). */
    public List<Models.Tx> recurringTxs(long from, long to) {
        Map<String, List<Models.Tx>> groups = new HashMap<>();
        for (Models.Tx t : txRange(from, to, 0, null, null)) {
            String key = t.cat + "|" + Math.round(t.amount);
            List<Models.Tx> g = groups.get(key);
            if (g == null) { g = new ArrayList<>(); groups.put(key, g); }
            g.add(t);
        }
        List<Models.Tx> out = new ArrayList<>();
        for (List<Models.Tx> g : groups.values()) if (g.size() >= 3) out.addAll(g);
        Collections.sort(out, (a, b) -> Double.compare(b.amount, a.amount));
        return out;
    }

    public int txCountThisMonth() {
        return txRange(Fmt.startOfMonth(Calendar.getInstance()).getTimeInMillis(),
                Fmt.endOfMonth(Calendar.getInstance()).getTimeInMillis(), null, null, null).size();
    }

    public boolean hasAnyData() {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tx", null);
        boolean any = c.moveToFirst() && c.getInt(0) > 0;
        c.close();
        return any;
    }
}
