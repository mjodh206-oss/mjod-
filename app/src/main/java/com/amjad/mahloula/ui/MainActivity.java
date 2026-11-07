package com.amjad.mahloula.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.amjad.mahloula.R;
import com.amjad.mahloula.data.Repo;
import com.amjad.mahloula.lock.Security;
import com.amjad.mahloula.ui.kit.BottomNav;
import com.amjad.mahloula.ui.panels.DashboardPanel;
import com.amjad.mahloula.ui.panels.DebtsPanel;
import com.amjad.mahloula.ui.panels.GuidePanel;
import com.amjad.mahloula.ui.panels.NotesPanel;
import com.amjad.mahloula.ui.panels.TxPanel;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

/** الشاشة الأم: خمس خانات — الرئيسية، مسحوبات، الديون، ملاحظات، تعليمي. */
public class MainActivity extends BaseActivity implements Repo.OnChange {

    private FrameLayout container;
    private BottomNav nav;
    private final Panel[] panels = new Panel[5];
    private int current = 0;
    private boolean paused;
    private boolean backgrounded;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildRoot());
        Repo.get(this).listen(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 501);
        }
    }

    private android.view.View buildRoot() {
        LinearLayout root = Ui.v(this);
        root.setBackgroundColor(Ui.color(this, R.color.bg));

        container = new FrameLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(container);

        nav = new BottomNav(this);
        nav.setListener(idx -> showTab(idx));
        root.addView(nav);
        return root;
    }

    private Panel panelAt(int i) {
        if (panels[i] == null) {
            switch (i) {
                case 0: panels[i] = new DashboardPanel(this); break;
                case 1: panels[i] = new TxPanel(this); break;
                case 2: panels[i] = new DebtsPanel(this); break;
                case 3: panels[i] = new NotesPanel(this); break;
                default: panels[i] = new GuidePanel(this); break;
            }
        }
        return panels[i];
    }

    private void showTab(int i) {
        if (panels[current] != null) panels[current].onHide();
        current = i;
        container.removeAllViews();
        Panel p = panelAt(i);
        android.view.View v = p.build();
        container.addView(v, new FrameLayout.LayoutParams(-1, -1));
        p.refresh();
        p.onShow();
        Ui.fadeSlideIn(v, 0);
    }

    public void jump(int tab) {
        nav.select(tab, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        paused = false;
        Prefs p = Prefs.get(this);
        if (backgrounded && p.lockEnabled() && !Security.sessionValid()) {
            backgrounded = false;
            startActivity(new Intent(this, LockActivity.class));
            return;
        }
        backgrounded = false;
        if (panels[current] != null) panels[current].refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
        backgrounded = true;
        Security.lockNow();
    }

    @Override
    public void onChanged() {
        if (!paused && panels[current] != null) {
            runOnUiThread(() -> {
                for (Panel p : panels) if (p != null) p.refresh();
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (current != 0) {
            jump(0);
            return;
        }
        moveTaskToBack(true);
    }
}
