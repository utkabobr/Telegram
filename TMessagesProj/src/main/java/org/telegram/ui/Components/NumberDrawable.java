package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;

import java.util.ArrayList;
import java.util.Locale;

public class NumberDrawable extends Drawable {
    private ArrayList<StaticLayout> letters = new ArrayList<>();
    private ArrayList<StaticLayout> oldLetters = new ArrayList<>();
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private ObjectAnimator animator;
    private float progress = 0.0f;
    private int currentNumber = 1;
    private boolean addNumber;
    private boolean center;
    private float textWidth;
    private float oldTextWidth;
    private boolean formatShort;
    private float mAlpha = 1f;

    @Keep
    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidateSelf();
    }

    @Keep
    public float getProgress() {
        return progress;
    }

    public void setAddNumber() {
        addNumber = true;
    }

    public void setFormatShortNumber() {
        formatShort = true;
    }

    public void setNumber(int number) {
        setNumber(number, false);
    }

    public void setNumber(int number, boolean animated) {
        if (currentNumber == number && animated) {
            return;
        }
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        oldLetters.clear();
        oldLetters.addAll(letters);
        letters.clear();
        String oldText;
        String text;
        boolean forwardAnimation = addNumber ? number < currentNumber : number > currentNumber;
        if (formatShort) {
            oldText = String.format("%s", LocaleController.formatShortNumber(currentNumber, null));
            int[] r = new int[1];
            text = String.format("%s", LocaleController.formatShortNumber(number, r));
            number = r[0];
        } else {
            String format = addNumber ? "#%d" : "%d";
            oldText = String.format(Locale.US, format, currentNumber);
            text = String.format(Locale.US, format, number);
        }

        boolean replace = false;
        textWidth = textPaint.measureText(text);
        oldTextWidth = textPaint.measureText(oldText);
        if (center) {
            if (textWidth != oldTextWidth) {
                replace = true;
            }
        }

        currentNumber = number;
        progress = 0;
        for (int a = 0; a < text.length(); a++) {
            String ch = text.substring(a, a + 1);
            String oldCh = !oldLetters.isEmpty() && a < oldText.length() ? oldText.substring(a, a + 1) : null;
            if (!replace && oldCh != null && oldCh.equals(ch)) {
                letters.add(oldLetters.get(a));
                oldLetters.set(a, null);
            } else {
                if (replace && oldCh == null) {
                    oldLetters.add(new StaticLayout("", textPaint, 0, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false));
                }
                StaticLayout layout = new StaticLayout(ch, textPaint, (int) Math.ceil(textPaint.measureText(ch)), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                letters.add(layout);
            }
        }
        if (animated && !oldLetters.isEmpty()) {
            animator = ObjectAnimator.ofFloat(this, "progress", forwardAnimation ? -1 : 1, 0);
            animator.setDuration(addNumber ? 180 : 150);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animator = null;
                    oldLetters.clear();
                }
            });
            animator.start();
        }
        invalidateSelf();
    }

    public int getCurrentWidth(boolean ignoreProgress) {
        float p = 1f - Math.abs(progress);
        if (ignoreProgress) p = 1;
        return (int) (oldTextWidth + (textWidth - oldTextWidth) * p);
    }

    public float getTextSize() {
        return textPaint.getTextSize();
    }

    public void setTextSize(int size) {
        textPaint.setTextSize(AndroidUtilities.dp(size));
        oldLetters.clear();
        letters.clear();
        setNumber(currentNumber, false);
    }

    public void setTextColor(int value) {
        textPaint.setColor(value);
        invalidateSelf();
    }

    public void setTypeface(Typeface typeface) {
        textPaint.setTypeface(typeface);
        oldLetters.clear();
        letters.clear();
        setNumber(currentNumber, false);
    }

    public void setCenterAlign(boolean center) {
        this.center = center;
    }

    @Override
    public void draw(Canvas canvas) {
        if (letters.isEmpty()) {
            return;
        }
        float height = letters.get(0).getHeight();
        float translationHeight = addNumber ? AndroidUtilities.dp(4) : height;

        float x = 0;
        float oldDx = 0;
        if (center) {
            x = (getBounds().width() - textWidth) / 2f;
            oldDx = (getBounds().width() - oldTextWidth) / 2f - x;
        }
        canvas.save();
        canvas.translate(getBounds().left + x, (getBounds().height() - height) / 2);
        int count = Math.max(letters.size(), oldLetters.size());
        for (int a = 0; a < count; a++) {
            canvas.save();
            StaticLayout old = a < oldLetters.size() ? oldLetters.get(a) : null;
            StaticLayout layout = a < letters.size() ? letters.get(a) : null;
            if (progress > 0) {
                if (old != null) {
                    textPaint.setAlpha((int) (0xFF * mAlpha * progress));
                    canvas.save();
                    canvas.translate(oldDx, (progress - 1.0f) * translationHeight);
                    old.draw(canvas);
                    canvas.restore();
                    if (layout != null) {
                        textPaint.setAlpha((int) (0xFF * mAlpha * (1.0f - progress)));
                        canvas.translate(0, progress * translationHeight);
                    }
                } else {
                    textPaint.setAlpha((int) (0xFF * mAlpha));
                }
            } else if (progress < 0) {
                if (old != null) {
                    textPaint.setAlpha((int) (0xFF * mAlpha * -progress));
                    canvas.save();
                    canvas.translate(oldDx, (1.0f + progress) * translationHeight);
                    old.draw(canvas);
                    canvas.restore();
                }
                if (layout != null) {
                    if (a == count - 1 || old != null) {
                        textPaint.setAlpha((int) (0xFF * mAlpha * (1.0f + progress)));
                        canvas.translate(0, progress * translationHeight);
                    } else {
                        textPaint.setAlpha((int) (0xFF * mAlpha));
                    }
                }
            } else if (layout != null) {
                textPaint.setAlpha((int) (0xFF * mAlpha));
            }
            if (layout != null) {
                layout.draw(canvas);
            }
            canvas.restore();
            canvas.translate(layout != null ? layout.getLineWidth(0) : old.getLineWidth(0) + AndroidUtilities.dp(1), 0);
            if (layout != null && old != null) {
                oldDx += old.getLineWidth(0) - layout.getLineWidth(0);
            }
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha / (float)0xFF;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        textPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
