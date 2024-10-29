package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import androidx.appcompat.widget.ViewUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.gms.cast.framework.CastButtonFactory;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class BotStartHint extends View {
    private final static int ARROW_DP = 8;
    private final static int PADDING_DP = 14;
    private final static int PADDING_TOP_BOTTOM_DP = 12;
    private final static int ICON_DP = 10;
    private final static int ROUND_DP = 8;
    private final static int MIN_ROUND_DP = 2;
    private final static int LINE_ROUND_DP = 4;

    private TextPaint textPaint = new TextPaint();
    private Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path = new Path();
    private Path mainPath = new Path();

    private String hintText;
    private StaticLayout hintLayout;

    private float bounceA, bounceB;

    public BotStartHint(Context context) {
        super(context);

        linePaint.setStrokeWidth(AndroidUtilities.dp(1.15f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setPathEffect(new CornerPathEffect(AndroidUtilities.dp(LINE_ROUND_DP)));

        bgPaint.setPathEffect(new CornerPathEffect(AndroidUtilities.dp(MIN_ROUND_DP)));

        hintText = LocaleController.getString(R.string.BotStartHint);
        textPaint.setTextSize(AndroidUtilities.dp(15));
        setPadding(AndroidUtilities.dp(ICON_DP + PADDING_DP * 2), AndroidUtilities.dp(PADDING_TOP_BOTTOM_DP), AndroidUtilities.dp(PADDING_DP), AndroidUtilities.dp(PADDING_TOP_BOTTOM_DP + ARROW_DP));
        setWillNotDraw(false);
        updateColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        textPaint.getTextBounds(hintText, 0, hintText.length(), AndroidUtilities.rectTmp2);
        AndroidUtilities.rectTmp2.right += AndroidUtilities.dp(1) + getPaddingLeft() + getPaddingRight();
        AndroidUtilities.rectTmp2.bottom += getPaddingTop() + getPaddingBottom();

        int width = AndroidUtilities.rectTmp2.width(), height = AndroidUtilities.rectTmp2.height();
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
        }
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
        }
        setMeasuredDimension(width, height);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        hintLayout = new StaticLayout(hintText, textPaint, getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        path.rewind();
        mainPath.rewind();

        AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight() - AndroidUtilities.dp(ARROW_DP));
        mainPath.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(ROUND_DP), AndroidUtilities.dp(ROUND_DP), Path.Direction.CW);

        float ext = AndroidUtilities.dp(MIN_ROUND_DP);
        path.moveTo(getWidth() / 2f - AndroidUtilities.dp(ARROW_DP) - ext, getHeight() - AndroidUtilities.dp(ARROW_DP));
        path.lineTo(getWidth() / 2f, getHeight());
        path.lineTo(getWidth() / 2f + AndroidUtilities.dp(ARROW_DP) + ext, getHeight() - AndroidUtilities.dp(ARROW_DP));
        path.lineTo(getWidth() / 2f - AndroidUtilities.dp(ARROW_DP) + ext, getHeight() - AndroidUtilities.dp(ARROW_DP));
        path.close();

        mainPath.op(path, Path.Op.UNION);

        canvas.drawPath(mainPath, bgPaint);

        canvas.save();
        int size = AndroidUtilities.dp(ICON_DP);
        canvas.translate(AndroidUtilities.dp(PADDING_DP), (getHeight() - AndroidUtilities.dp(ARROW_DP) - size) / 2f + 1);

        canvas.save();
        canvas.translate(0, size * 0.25f * bounceB);
        canvas.drawLine(size / 2f, size * 0.5f, 0, 0, linePaint);
        canvas.drawLine(size / 2f, size * 0.5f, size, 0, linePaint);
        canvas.restore();

        canvas.save();
        canvas.translate(0, size * 0.25f * bounceA);
        canvas.drawLine(size / 2f, size, 0, size * 0.5f, linePaint);
        canvas.drawLine(size / 2f, size, size, size * 0.5f, linePaint);
        canvas.restore();

        canvas.restore();

        canvas.save();
        canvas.translate(getPaddingLeft(), (getHeight() - AndroidUtilities.dp(ARROW_DP) - hintLayout.getHeight()) / 2f);
        hintLayout.draw(canvas);
        canvas.restore();
    }

    public void show(boolean show) {
        boolean wasVisible = getTag() != null;
        if (wasVisible == show) {
            return;
        }
        setTag(show ? 1 : null);

        setAlpha(show ? 0 : 1);
        setScaleX(show ? 0.4f : 1f);
        setScaleY(show ? 0.4f : 1f);
        Runnable r = () -> {
            setPivotX(getWidth() / 2f);
            setPivotY(getHeight());
            new SpringAnimation(new FloatValueHolder(show ? 0 : 1))
                    .setMinimumVisibleChange(1 / 256f)
                    .setSpring(new SpringForce(show ? 1 : 0)
                            .setStiffness(1000f)
                            .setDampingRatio(1f))
                    .addUpdateListener((animation, value, velocity) -> {
                        float scale = AndroidUtilities.lerp(0.4f, 1f, value);
                        setScaleX(scale);
                        setScaleY(scale);
                        setAlpha(value);
                    })
                    .addEndListener((animation, canceled, value, velocity) -> {
                        if (!show) {
                            setVisibility(GONE);
                        }
                    })
                    .start();
        };
        if (show) {
            addOnLayoutChangeListener(new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    removeOnLayoutChangeListener(this);
                    r.run();
                }
            });
            setVisibility(VISIBLE);
        } else {
            r.run();
        }
    }

    public void bounce() {
        bounce((obj, value) -> bounceA = value, 150f, 0.75f);
        bounce((obj, value) -> bounceB = value, 250f, 0.75f);
    }

    private void bounce(SimpleFloatPropertyCompat.Setter<Void> set, float stiffness, float damping) {
        SpringAnimation spring = new SpringAnimation(new FloatValueHolder(0))
                .setMinimumVisibleChange(1 / 256f)
                .setSpring(new SpringForce(1)
                        .setStiffness(stiffness)
                        .setDampingRatio(damping));

        spring.addUpdateListener((animation, value, velocity) -> {
                    if (value > 0.5f && spring.getSpring().getFinalPosition() != 0) {
                        spring.getSpring().setFinalPosition(0);
                        spring.start();
                    }

                    set.set(null, value);
                    invalidate();
                })
                .addEndListener((animation, canceled, value, velocity) -> {
                    set.set(null, 0);
                    invalidate();
                })
                .start();
    }

    public void updateColors() {
        bgPaint.setColor(Theme.getColor(Theme.key_chat_gifSaveHintBackground));
        linePaint.setColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
        textPaint.setColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
    }
}
