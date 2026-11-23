package dev.lasq.fixture;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Disposable instrumented-test fixture; never packaged in the delivered APK. */
public final class Editor extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        EditText field = new EditText(this);
        field.setId(R.id.editor);
        field.setSingleLine(true);
        field.setShowSoftInputOnFocus(false);
        boolean source = getPackageName().endsWith(".source");
        field.setText(source ? "Original fixture field" : "");
        field.setHint("Test editor");
        layout.addView(field);
        if (source) {
            for (String name : new String[]{"instant", "confirm", "archive", "sensitive"}) {
                Button copy = new Button(this);
                copy.setText(name);
                copy.setContentDescription("copy-" + name);
                copy.setOnClickListener(view -> {
                    ClipData clip = ClipData.newPlainText("Fixture", "LASQ_FIXTURE_" + name);
                    if (name.equals("sensitive")) {
                        PersistableBundle flags = new PersistableBundle();
                        flags.putBoolean("android.content.extra.IS_SENSITIVE", true);
                        clip.getDescription().setExtras(flags);
                    }
                    getSystemService(ClipboardManager.class).setPrimaryClip(clip);
                });
                layout.addView(copy);
            }
        }
        setContentView(layout);
        field.requestFocus();
    }
}
