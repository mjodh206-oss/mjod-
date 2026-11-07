package com.amjad.mahloula.ui.panels;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.amjad.mahloula.R;
import com.amjad.mahloula.data.Models;
import com.amjad.mahloula.data.Repo;
import com.amjad.mahloula.ui.Panel;
import com.amjad.mahloula.ui.kit.UiKit;
import com.amjad.mahloula.ui.sheets.Sheets;
import com.amjad.mahloula.util.Fmt;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

import java.util.List;

/** خانة الملاحظات: أفكارك وقوائمك المالية المثبتة والملوّنة. */
public class NotesPanel implements Panel {

    private final Activity a;
    private LinearLayout list;
    private TextView searchField;
    private String query = null;

    public NotesPanel(Activity a) { this.a = a; }

    private boolean ar() { return !Prefs.get(a).language().equals("en"); }

    @Override
    public View build() {
        FrameLayout root = new FrameLayout(a);
        LinearLayout outer = Ui.v(a);
        outer.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 96));

        LinearLayout head = Ui.h(a);
        head.addView(Ui.txt(a, ar() ? "ملاحظاتي" : "My notes", 21, R.color.text_primary, Typeface.BOLD));
        head.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        head.addView(Ui.txt(a, ar() ? "أفكارك المالية" : "Your money thoughts", 12, R.color.text_tertiary));
        outer.addView(head);

        searchField = UiKit.field(a, ar() ? "ابحث في ملاحظاتك…" : "Search notes…", false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = Ui.dp(a, 10);
        searchField.setLayoutParams(slp);
        searchField.setOnEditorActionListener((v, act, ev) -> { query = searchField.getText().toString(); refresh(); return true; });
        outer.addView(searchField);

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        svlp.topMargin = Ui.dp(a, 10);
        sv.setLayoutParams(svlp);
        list = Ui.v(a);
        sv.addView(list);
        outer.addView(sv);
        root.addView(outer, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout fab = new FrameLayout(a);
        fab.setBackground(Ui.ripple(a, Ui.round(Ui.color(a, R.color.brand_600), 18, a), Color.argb(40, 255, 255, 255)));
        fab.setElevation(Ui.dp(a, 6));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(a, 58), Ui.dp(a, 58));
        flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        flp.bottomMargin = Ui.dp(a, 18);
        fab.setLayoutParams(flp);
        ImageView plus = Ui.iconSized(a, R.drawable.ic_plus, Color.WHITE, 26);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(a, 26), Ui.dp(a, 26));
        plp.gravity = Gravity.CENTER;
        plus.setLayoutParams(plp);
        fab.addView(plus);
        fab.setOnClickListener(v -> Sheets.note(a, null));
        root.addView(fab);
        return root;
    }

    @Override
    public void refresh() {
        if (list == null) return;
        list.removeAllViews();
        List<Models.Note> notes = Repo.get(a).notes(query);
        if (notes.isEmpty()) {
            list.addView(UiKit.empty(a, R.drawable.ic_note,
                    ar() ? "لا ملاحظات بعد" : "No notes yet",
                    ar() ? "سجّل فكرة، قائمة شراء، أو خطة شهرية" : "Jot an idea, a shopping list, or a monthly plan"));
            return;
        }
        for (Models.Note n : notes) {
            list.addView(noteCard(n));
            View last = list.getChildAt(list.getChildCount() - 1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(a, 10);
            last.setLayoutParams(lp);
        }
    }

    private View noteCard(final Models.Note n) {
        int colorRes = n.color >= 0 && n.color < Sheets.NOTE_COLORS.length
                ? Sheets.NOTE_COLORS[n.color] : Sheets.NOTE_COLORS[0];
        LinearLayout card = UiKit.card(a, 0);
        card.setPadding(0, 0, 0, Ui.dp(a, 12));
        View strip = new View(a);
        strip.setBackground(Ui.round(Ui.color(a, colorRes), 0, a));
        card.addView(strip, new LinearLayout.LayoutParams(-1, Ui.dp(a, 5)));

        LinearLayout inner = Ui.v(a);
        inner.setPadding(Ui.dp(a, 15), Ui.dp(a, 12), Ui.dp(a, 15), 0);
        LinearLayout head = Ui.h(a);
        TextView title = Ui.txt(a, Ui.empty(n.title) ? (ar() ? "بدون عنوان" : "Untitled") : n.title,
                15.5f, R.color.text_primary, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(title);
        ImageView pin = Ui.iconSized(a, R.drawable.ic_pin,
                Ui.color(a, n.pinned ? R.color.gold_500 : R.color.text_tertiary), 19);
        pin.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        pin.setOnClickListener(v -> { n.pinned = !n.pinned; Repo.get(a).updateNote(n); });
        head.addView(pin);
        inner.addView(head);

        if (!Ui.empty(n.body)) {
            TextView body = Ui.txt(a, n.body, 13.5f, R.color.text_secondary);
            body.setMaxLines(4);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.topMargin = Ui.dp(a, 6);
            body.setLayoutParams(blp);
            inner.addView(body);
        }
        TextView date = Ui.txt(a, Fmt.relative(a, n.upd), 11.5f, R.color.text_tertiary);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(a, 8);
        date.setLayoutParams(dlp);
        inner.addView(date);
        card.addView(inner);

        card.setOnClickListener(v -> Sheets.note(a, n));
        card.setOnLongClickListener(v -> {
            Sheets.confirm(a, ar() ? "حذف الملاحظة؟" : "Delete note?", n.title,
                    () -> Repo.get(a).deleteNote(n.id));
            return true;
        });
        return card;
    }

    @Override
    public void onShow() { refresh(); }
}
