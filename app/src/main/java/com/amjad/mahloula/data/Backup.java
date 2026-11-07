package com.amjad.mahloula.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** نسخ احتياطي واستعادة بصيغة JSON — ملف محلي على جهازك فقط. */
public final class Backup {

    private Backup() {}

    public static File backupsDir(Context c) {
        File d = new File(c.getFilesDir(), "backups");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static String stamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
    }

    /** ينشئ ملف نسخة داخل مجلد التطبيق ويعيده. */
    public static File export(Context c) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "mahloula");
        root.put("version", 1);
        root.put("created", System.currentTimeMillis());

        JSONArray tx = new JSONArray();
        for (Models.Tx t : Repo.get(c).allTx()) {
            JSONObject o = new JSONObject();
            o.put("kind", t.kind); o.put("amount", t.amount); o.put("cat", t.cat);
            o.put("wallet", t.wallet); o.put("note", t.note); o.put("method", t.method); o.put("ts", t.ts);
            tx.put(o);
        }
        root.put("tx", tx);

        JSONArray debts = new JSONArray();
        for (Models.Debt d : Repo.get(c).debts()) {
            JSONObject o = new JSONObject();
            o.put("kind", d.kind); o.put("person", d.person); o.put("title", d.title);
            o.put("total", d.total); o.put("due", d.due); o.put("note", d.note);
            JSONArray pays = new JSONArray();
            for (Models.DebtPay p : Repo.get(c).debtPays(d.id)) {
                JSONObject po = new JSONObject();
                po.put("amount", p.amount); po.put("ts", p.ts); po.put("note", p.note);
                pays.put(po);
            }
            o.put("pays", pays);
            debts.put(o);
        }
        root.put("debts", debts);

        JSONArray notes = new JSONArray();
        for (Models.Note n : Repo.get(c).notes(null)) {
            JSONObject o = new JSONObject();
            o.put("title", n.title); o.put("body", n.body); o.put("color", n.color); o.put("pinned", n.pinned);
            notes.put(o);
        }
        root.put("notes", notes);

        JSONArray wallets = new JSONArray();
        for (Models.Wallet w : Repo.get(c).wallets()) {
            JSONObject o = new JSONObject();
            o.put("name", w.name); o.put("kind", w.kind); o.put("initial", w.initial); o.put("def", w.def);
            wallets.put(o);
        }
        root.put("wallets", wallets);

        JSONArray goals = new JSONArray();
        for (Models.Goal g : Repo.get(c).goals()) {
            JSONObject o = new JSONObject();
            o.put("title", g.title); o.put("target", g.target); o.put("saved", g.saved); o.put("due", g.due);
            goals.put(o);
        }
        root.put("goals", goals);

        File f = new File(backupsDir(c), "mahloula-backup-" + stamp() + ".json");
        FileOutputStream out = new FileOutputStream(f);
        out.write(root.toString(2).getBytes("UTF-8"));
        out.close();
        return f;
    }

    /** يستورد من مسار ملف (نتيجة SAF) — يستبدل البيانات الحالية بعد تأكيد المستخدم. */
    public static boolean restore(Context c, InputStream in) throws Exception {
        byte[] buf = readAll(in);
        JSONObject root = new JSONObject(new String(buf, "UTF-8"));
        if (!"mahloula".equals(root.optString("app"))) return false;

        Repo r = Repo.get(c);
        // تفريغ
        for (Models.Tx t : r.allTx()) r.deleteTx(t.id);
        for (Models.Debt d : r.debts()) r.deleteDebt(d.id);
        for (Models.Note n : r.notes(null)) r.deleteNote(n.id);
        for (Models.Wallet w : r.wallets()) r.deleteWallet(w.id);
        for (Models.Goal g : r.goals()) r.deleteGoal(g.id);

        // محافظ + خريطة معرفات
        java.util.Map<Long, Long> walletMap = new java.util.HashMap<>();
        JSONArray wallets = root.optJSONArray("wallets");
        if (wallets != null) {
            for (int i = 0; i < wallets.length(); i++) {
                JSONObject o = wallets.getJSONObject(i);
                Models.Wallet w = new Models.Wallet();
                w.name = o.optString("name", "محفظة");
                w.kind = o.optInt("kind", 0);
                w.initial = o.optDouble("initial", 0);
                w.def = o.optBoolean("def", false);
                long nid = r.addWallet(w);
                walletMap.put((long) (i + 1), nid);
            }
        }
        if (r.wallets().isEmpty()) {
            Models.Wallet w = new Models.Wallet();
            w.name = "المحفظة الرئيسية";
            w.def = true;
            r.addWallet(w);
        }

        JSONArray tx = root.optJSONArray("tx");
        if (tx != null) {
            for (int i = 0; i < tx.length(); i++) {
                JSONObject o = tx.getJSONObject(i);
                Models.Tx t = new Models.Tx();
                t.kind = o.optInt("kind", 0);
                t.amount = o.optDouble("amount", 0);
                t.cat = o.optInt("cat", 12);
                Long wm = walletMap.get(o.optLong("wallet", 1));
                t.wallet = wm == null ? r.defaultWallet().id : wm;
                t.note = o.optString("note", "");
                t.method = o.optString("method", "");
                t.ts = o.optLong("ts", System.currentTimeMillis());
                r.addTx(t);
            }
        }

        JSONArray debts = root.optJSONArray("debts");
        if (debts != null) {
            for (int i = 0; i < debts.length(); i++) {
                JSONObject o = debts.getJSONObject(i);
                Models.Debt d = new Models.Debt();
                d.kind = o.optInt("kind", 0);
                d.person = o.optString("person", "");
                d.title = o.optString("title", "");
                d.total = o.optDouble("total", 0);
                d.due = o.optLong("due", 0);
                d.note = o.optString("note", "");
                long id = r.addDebt(d);
                JSONArray pays = o.optJSONArray("pays");
                if (pays != null) {
                    for (int k = 0; k < pays.length(); k++) {
                        JSONObject po = pays.getJSONObject(k);
                        r.addDebtPay(id, po.optDouble("amount", 0), po.optString("note", ""));
                    }
                }
            }
        }

        JSONArray notes = root.optJSONArray("notes");
        if (notes != null) {
            for (int i = 0; i < notes.length(); i++) {
                JSONObject o = notes.getJSONObject(i);
                Models.Note n = new Models.Note();
                n.title = o.optString("title", "");
                n.body = o.optString("body", "");
                n.color = o.optInt("color", 0);
                n.pinned = o.optBoolean("pinned", false);
                r.addNote(n);
            }
        }

        JSONArray goals = root.optJSONArray("goals");
        if (goals != null) {
            for (int i = 0; i < goals.length(); i++) {
                JSONObject o = goals.getJSONObject(i);
                Models.Goal g = new Models.Goal();
                g.title = o.optString("title", "");
                g.target = o.optDouble("target", 0);
                g.saved = o.optDouble("saved", 0);
                g.due = o.optLong("due", 0);
                r.addGoal(g);
            }
        }
        return true;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toByteArray();
    }

    /** تصدير CSV بسيط يفتح في Excel/جداول Google. */
    public static File exportCsv(Context c) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("النوع,المبلغ,التصنيف,المحفظة,التاريخ,الطريقة,ملاحظة\n");
        List<Models.Wallet> ws = Repo.get(c).wallets();
        for (Models.Tx t : Repo.get(c).allTx()) {
            String wallet = "";
            for (Models.Wallet w : ws) if (w.id == t.wallet) wallet = w.name;
            sb.append(t.isExpense() ? "مصروف" : "دخل").append(',')
                    .append(t.amount).append(',')
                    .append(Models.cat(t.cat).ar).append(',')
                    .append(wallet).append(',')
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(t.ts))).append(',')
                    .append(t.method).append(',')
                    .append(t.note.replace(",", "،")).append('\n');
        }
        File f = new File(backupsDir(c), "mahloula-transactions-" + stamp() + ".csv");
        FileOutputStream out = new FileOutputStream(f);
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // BOM لعربية Excel
        out.write(sb.toString().getBytes("UTF-8"));
        out.close();
        return f;
    }

    public static void copy(File src, OutputStream dst) throws Exception {
        FileInputStream in = new FileInputStream(src);
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) dst.write(b, 0, n);
        in.close();
        dst.close();
    }
}
