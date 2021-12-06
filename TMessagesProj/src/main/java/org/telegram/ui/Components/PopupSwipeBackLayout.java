package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.GestureDetectorCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;

public class PopupSwipeBackLayout extends FrameLayout {
    private final static int DURATION = 300;

    private float transitionProgress;
    private float toProgress = -1;
    private GestureDetectorCompat detector;
    private boolean isProcessingSwipe;
    private boolean isAnimationInProgress;
    private boolean isSwipeDisallowed;
    private Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Path mPath = new Path();
    private RectF mRect = new RectF();

    private OnSwipeBackProgressListener onSwipeBackProgressListener;
    private boolean isSwipeBackDisallowed;

    private float overrideForegroundHeight;
    private ValueAnimator foregroundAnimator;

    private Rect hitRect = new Rect();

    public PopupSwipeBackLayout(@NonNull Context context) {
        super(context);

        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        detector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (!isProcessingSwipe && !isSwipeDisallowed) {
                    if (!isSwipeBackDisallowed && transitionProgress == 1 && distanceX <= -touchSlop && Math.abs(distanceX) >= Math.abs(distanceY * 1.5f) && !isDisallowedView(e2, getChildAt(transitionProgress > 0.5f ? 1 : 0))) {
                        isProcessingSwipe = true;

                        MotionEvent c = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                        for (int i = 0; i < getChildCount(); i++)
                            getChildAt(i).dispatchTouchEvent(c);
                        c.recycle();
                    } else isSwipeDisallowed = true;
                }

                if (isProcessingSwipe) {
                    toProgress = -1;
                    transitionProgress = 1f - Math.max(0, Math.min(1, (e2.getX() - e1.getX()) / getWidth()));
                    invalidateTransforms();
                }

                return isProcessingSwipe;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isAnimationInProgress || isSwipeDisallowed)
                    return false;

                if (velocityX >= 600) {
                    clearFlags();
                    animateToState(0, velocityX / 6000f);
                }
                return false;
            }
        });
        overlayPaint.setColor(Color.BLACK);
    }

    /**
     * Sets if swipeback action should be disallowed
     * @param swipeBackDisallowed If swipe should be disallowed
     */
    public void setSwipeBackDisallowed(boolean swipeBackDisallowed) {
        isSwipeBackDisallowed = swipeBackDisallowed;
    }

    /**
     * Sets new swipeback listener
     * @param onSwipeBackProgressListener New progress listener
     */
    public void setOnSwipeBackProgressListener(OnSwipeBackProgressListener onSwipeBackProgressListener) {
        this.onSwipeBackProgressListener = onSwipeBackProgressListener;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        int i = indexOfChild(child);
        int s = canvas.save();
        boolean b = super.drawChild(canvas, child, drawingTime);
        if (i == 0) {
            overlayPaint.setAlpha((int) (transitionProgress * 0x40));
            canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);
        }
        canvas.restoreToCount(s);
        return b;
    }

    /**
     * Invalidates transformations
     */
    private void invalidateTransforms() {
        if (getChildCount() != 2) return;

        if (onSwipeBackProgressListener != null)
            onSwipeBackProgressListener.onSwipeBackProgress(this, toProgress, transitionProgress);

        View bg = getChildAt(0), fg = getChildAt(1);
        bg.setTranslationX(-transitionProgress * getWidth() * 0.5f);
        float bSc = 0.95f + (1f - transitionProgress) * 0.05f;
        bg.setScaleX(bSc);
        bg.setScaleY(bSc);
        fg.setTranslationX((1f - transitionProgress) * getWidth());
        invalidateVisibility();

        float fW = bg.getMeasuredWidth(), fH = bg.getMeasuredHeight();
        float tW = fg.getMeasuredWidth(), tH = overrideForegroundHeight != 0 ? overrideForegroundHeight : fg.getMeasuredHeight();
        if (bg.getMeasuredWidth() == 0 || bg.getMeasuredHeight() == 0 || fg.getMeasuredWidth() == 0 || fg.getMeasuredHeight() == 0)
            return;

        ActionBarPopupWindow.ActionBarPopupWindowLayout p = (ActionBarPopupWindow.ActionBarPopupWindowLayout) getParent();
        float w = fW + (tW - fW) * transitionProgress;
        float h = fH + (tH - fH) * transitionProgress;
        w += p.getPaddingLeft() + p.getPaddingRight();
        h += p.getPaddingTop() + p.getPaddingBottom();
        p.setBackScaleX(w / p.getMeasuredWidth());
        p.setBackScaleY(h / p.getMeasuredHeight());

        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            ch.setPivotX(0);
            ch.setPivotY(0);
        }

        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (processTouchEvent(ev))
            return true;

        if (getChildCount() != 2)
            return false;

        View bv = getChildAt(0);
        View fv = getChildAt(1);
        int act = ev.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN && (ev.getX() > (bv.getMeasuredWidth() + (fv.getMeasuredWidth() - bv.getMeasuredWidth()) * transitionProgress) ||
                ev.getY() > (bv.getMeasuredHeight() + ((overrideForegroundHeight != 0 ? overrideForegroundHeight : fv.getMeasuredHeight()) - bv.getMeasuredHeight()) * transitionProgress))) {
            callOnClick();
            return true;
        }

        boolean b = (transitionProgress > 0.5f ? fv : bv).dispatchTouchEvent(ev);
        if (!b && act == MotionEvent.ACTION_DOWN) {
            return true;
        }
        return b || onTouchEvent(ev);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateTransforms();
    }

    /**
     * Processes touch event and return true if processed
     * @param ev Event to process
     * @return If event is processed
     */
    private boolean processTouchEvent(MotionEvent ev) {
        int act = ev.getAction() & MotionEvent.ACTION_MASK;
        if (isAnimationInProgress)
            return true;

        if (!detector.onTouchEvent(ev)) {
            switch (act) {
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (isProcessingSwipe) {
                        clearFlags();
                        animateToState(transitionProgress >= 0.5f ? 1 : 0, 0);
                    } else if (isSwipeDisallowed) clearFlags();
                    return false;
            }
        }
        return isProcessingSwipe;
    }

    /**
     * Animates transition value
     * @param f End value
     * @param flingVal Fling value(If from fling, zero otherwise)
     */
    private void animateToState(float f, float flingVal) {
        ValueAnimator val = ValueAnimator.ofFloat(transitionProgress, f).setDuration((long) (DURATION * Math.max(0.5f, Math.abs(transitionProgress - f) - Math.min(0.2f, flingVal))));
        val.setInterpolator(Easings.easeOutQuad);
        val.addUpdateListener(animation -> {
            transitionProgress = (float) animation.getAnimatedValue();
            invalidateTransforms();
        });
        val.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                isAnimationInProgress = true;
                toProgress = f;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                transitionProgress = f;
                invalidateTransforms();
                isAnimationInProgress = false;
            }
        });
        val.start();
    }

    /**
     * Clears touch flags
     */
    private void clearFlags() {
        isProcessingSwipe = false;
        isSwipeDisallowed = false;
    }

    /**
     * Opens up foreground
     */
    public void openForeground() {
        if (isAnimationInProgress) return;
        animateToState(1, 0);
    }

    /**
     * Closes foreground view
     */
    public void closeForeground() {
        if (isAnimationInProgress) return;
        animateToState(0, 0);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            ch.layout(0, 0, ch.getMeasuredWidth(), ch.getMeasuredHeight());
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        invalidateTransforms();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (getChildCount() != 2) return;

        View bg = getChildAt(0), fg = getChildAt(1);
        float fW = bg.getMeasuredWidth(), fH = bg.getMeasuredHeight();
        float tW = fg.getMeasuredWidth(), tH = overrideForegroundHeight != 0 ? overrideForegroundHeight : fg.getMeasuredHeight();
        if (bg.getMeasuredWidth() == 0 || bg.getMeasuredHeight() == 0 || fg.getMeasuredWidth() == 0 || fg.getMeasuredHeight() == 0)
            return;

        float w = fW + (tW - fW) * transitionProgress;
        float h = fH + (tH - fH) * transitionProgress;

        int s = canvas.save();
        mPath.rewind();
        int rad = AndroidUtilities.dp(6);
        mRect.set(0, 0, w, h);
        mPath.addRoundRect(mRect, rad, rad, Path.Direction.CW);
        canvas.clipPath(mPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(s);
    }

    /**
     * @param e Motion event to check
     * @param v View to check
     * @return If we should ignore view
     */
    private boolean isDisallowedView(MotionEvent e, View v) {
        v.getHitRect(hitRect);
        if (hitRect.contains((int) e.getX(), (int) e.getY()) && v.canScrollHorizontally(-1))
            return true;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++)
                if (isDisallowedView(e, vg.getChildAt(i)))
                    return true;
        }

        return false;
    }

    /**
     * Invalidates view transforms
     */
    private void invalidateVisibility() {
        if (getChildCount() == 2) {
            View bg = getChildAt(0);
            View fg = getChildAt(1);

            if (transitionProgress == 0 && fg.getVisibility() != INVISIBLE)
                fg.setVisibility(INVISIBLE);
            if (transitionProgress != 0 && fg.getVisibility() != VISIBLE)
                fg.setVisibility(VISIBLE);
            if (transitionProgress == 1 && bg.getVisibility() != INVISIBLE)
                bg.setVisibility(INVISIBLE);
            if (transitionProgress != 1 && bg.getVisibility() != VISIBLE)
                bg.setVisibility(VISIBLE);
        }
    }

    /**
     * Sets new foreground height with animation
     * @param height New height
     */
    public void setNewForegroundHeight(int height) {
        if (getChildCount() != 2) return;
        if (foregroundAnimator != null)
            foregroundAnimator.cancel();
        View fg = getChildAt(1);
        float fromH = overrideForegroundHeight != 0 ? overrideForegroundHeight : fg.getMeasuredHeight();
        float toH = height;

        ValueAnimator animator = ValueAnimator.ofFloat(fromH, toH).setDuration(240);
        animator.setInterpolator(Easings.easeInOutQuad);
        animator.addUpdateListener(animation -> {
            overrideForegroundHeight = (float) animation.getAnimatedValue();
            invalidateTransforms();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimationInProgress = false;
            }

            @Override
            public void onAnimationStart(Animator animation) {
                isAnimationInProgress = true;
            }
        });
        animator.start();
        foregroundAnimator = animator;
    }

    public interface OnSwipeBackProgressListener {
        void onSwipeBackProgress(PopupSwipeBackLayout layout, float toProgress, float progress);
    }
}
