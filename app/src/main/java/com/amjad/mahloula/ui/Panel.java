package com.amjad.mahloula.ui;

import android.view.View;

/** لوحة تبويب داخل الشاشة الرئيسية. */
public interface Panel {
    View build();

    void refresh();

    default void onShow() {}

    default void onHide() {}
}
