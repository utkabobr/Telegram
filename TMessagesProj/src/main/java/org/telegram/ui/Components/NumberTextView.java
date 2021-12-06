/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.ImageView;

public class NumberTextView extends ImageView {
    private NumberDrawable numberDrawable = new NumberDrawable();

    public NumberTextView(Context context) {
        super(context);
        setImageDrawable(numberDrawable);
    }

    public void setAddNumber() {
        numberDrawable.setAddNumber();
    }

    public void setNumber(int number, boolean animated) {
        numberDrawable.setNumber(number, animated);
    }

    public void setTextSize(int size) {
        numberDrawable.setTextSize(size);
    }

    public void setTextColor(int value) {
        numberDrawable.setTextColor(value);
    }

    public void setTypeface(Typeface typeface) {
        numberDrawable.setTypeface(typeface);
    }

    public void setCenterAlign(boolean center) {
        numberDrawable.setCenterAlign(center);
    }
}
