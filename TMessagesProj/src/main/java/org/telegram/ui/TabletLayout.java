package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.view.GestureDetectorCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedPrefsHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class TabletLayout extends ViewGroup {
    private final static float MIN_SIDE_WIDTH = 0.25f, MAX_SIDE_WIDTH = 0.75f;
    private final static int MIN_SIDE_DP = 280, MAX_SIDE_DP = 380;
    private final static int ANIM_DURATION = 300;

    private int sideWidth;
    private GestureDetectorCompat gestureDetector;
    private Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private boolean isTrackingTouch;

    private ActionBarLayout sideLayout;
    private ActionBarLayout contentLayout;
    private View backgroundTablet;

    private View popupShadow;
    private ActionBarLayout popupLayout;

    private boolean tabletFullSize;

    private float pendingSideProgress;
    private int pendingSideWidth;
    private float sideShadowAlpha;
    private Animator animator;

    private float snapshotFromWidth, snapshotToWidth, snapshotProgress;
    private boolean isSnapshotDrawingNow;
    private Bitmap sideFromSnapshot, sideToSnapshot,
            contentFromSnapshot, contentToSnapshot;

    private Runnable onLayout;

    public TabletLayout(@NonNull Context context) {
        super(context);

        int touchSlop = AndroidUtilities.dp(12);
        gestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                if (popupLayout.getVisibility() == VISIBLE)
                    return false;
                float x = e.getX();
                if (x >= sideWidth - touchSlop / 2f && x <= sideWidth + touchSlop / 2f) {
                    isTrackingTouch = true;
                    MotionEvent c = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    for (int i = 0; i < getChildCount(); i++) {
                        getChildAt(i).dispatchTouchEvent(c);
                    }
                    c.recycle();
                    getParent().requestDisallowInterceptTouchEvent(true);

                    float pw = clampWidth(e.getX() / getWidth()) / (float)getWidth();

                    pendingSideProgress = pw;
                    pendingSideWidth = (int) (pw * getWidth());

                    if (animator != null) {
                        animator.cancel();
                    }
                    ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(ANIM_DURATION);
                    anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    anim.addUpdateListener(animation -> {
                        sideShadowAlpha = (float) animation.getAnimatedValue();
                        invalidate();
                    });
                    anim.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            animator = null;
                        }
                    });
                    anim.start();
                    animator = anim;
                    return true;
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (isTrackingTouch) {
                    float pw = clampWidth(e2.getX() / getWidth()) / (float)getWidth();

                    pendingSideProgress = pw;
                    pendingSideWidth = (int) (pw * getWidth());
                    invalidate();

                    return true;
                }
                return false;
            }
        });
        updateColors();
        setWillNotDraw(false);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean g = gestureDetector.onTouchEvent(ev);
        int act = ev.getActionMasked();
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            if (isTrackingTouch && pendingSideWidth != 0 && pendingSideProgress != 0) {
                isTrackingTouch = false;

                if (animator != null) {
                    animator.cancel();
                }
                ValueAnimator anim1 = ValueAnimator.ofFloat(1, 0).setDuration(ANIM_DURATION);
                anim1.setInterpolator(CubicBezierInterpolator.DEFAULT);
                anim1.addUpdateListener(animation -> {
                    sideShadowAlpha = (float) animation.getAnimatedValue();
                    invalidate();
                });

                snapshotFromWidth = sideWidth;
                snapshotProgress = 0;
                sideFromSnapshot = snapshotView(sideLayout);
                contentFromSnapshot = snapshotView(contentLayout);
                isSnapshotDrawingNow = true;

                SharedPrefsHelper.TabletPrefs.setSideWidth(pendingSideProgress);
                snapshotToWidth = sideWidth = pendingSideWidth;
                onLayout = () -> {
                    sideToSnapshot = snapshotView(sideLayout);
                    contentToSnapshot = snapshotView(contentLayout);
                    invalidate();

                    ValueAnimator anim2 = ValueAnimator.ofFloat(0, 1).setDuration(ANIM_DURATION);
                    anim2.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    anim2.addUpdateListener(animation -> {
                        snapshotProgress = (float) animation.getAnimatedValue();
                        invalidate();
                    });
                    anim2.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            sideFromSnapshot.recycle();
                            sideToSnapshot.recycle();
                            contentFromSnapshot.recycle();
                            contentToSnapshot.recycle();

                            sideFromSnapshot = null;
                            sideToSnapshot = null;
                            contentFromSnapshot = null;
                            contentToSnapshot = null;
                        }
                    });

                    AnimatorSet set = new AnimatorSet();
                    set.playTogether(anim1, anim2);
                    set.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            isSnapshotDrawingNow = false;
                            invalidate();

                            pendingSideWidth = 0;
                            pendingSideProgress = 0;

                            getParent().requestDisallowInterceptTouchEvent(false);

                            animator = null;
                        }
                    });
                    set.start();
                    animator = set;
                };
                NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.tabletSideWidthChanged);
                requestLayout();
            }
        }
        if (g)
            return true;
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (isSnapshotDrawingNow) {
            drawChild(canvas, backgroundTablet, SystemClock.uptimeMillis());

            if (sideToSnapshot == null || contentToSnapshot == null) {
                bitmapPaint.setAlpha(0xFF);
                canvas.drawBitmap(sideFromSnapshot, 0, 0, bitmapPaint);
                canvas.drawBitmap(contentFromSnapshot, snapshotFromWidth, 0, bitmapPaint);
            } else {
                bitmapPaint.setAlpha(0xFF);
                canvas.save();
                canvas.scale(1f + (1f - snapshotFromWidth / snapshotToWidth) * snapshotProgress, 1f);
                canvas.drawBitmap(sideFromSnapshot, 0, 0, bitmapPaint);
                canvas.restore();

                bitmapPaint.setAlpha((int) (snapshotProgress * 0xFF));
                canvas.save();
                canvas.scale(1f + (1f - snapshotToWidth / snapshotFromWidth) * (1f - snapshotProgress), 1f);
                canvas.drawBitmap(sideToSnapshot, 0, 0, bitmapPaint);
                canvas.restore();

                bitmapPaint.setAlpha(0xFF);
                canvas.drawBitmap(contentFromSnapshot, snapshotFromWidth + (snapshotToWidth - snapshotFromWidth) * snapshotProgress, 0, bitmapPaint);
                bitmapPaint.setAlpha((int) (snapshotProgress * 0xFF));
                canvas.drawBitmap(contentToSnapshot, snapshotFromWidth + (snapshotToWidth - snapshotFromWidth) * snapshotProgress, 0, bitmapPaint);
            }
            return;
        }

        super.dispatchDraw(canvas);

        if (!tabletFullSize) {
            shadowPaint.setAlpha((int) (0x66 * sideShadowAlpha));
            canvas.drawRect(0, 0, pendingSideWidth, getHeight(), shadowPaint);
            canvas.drawLine(sideWidth, 0, sideWidth, getHeight(), dividerPaint);
        }
    }

    private Bitmap snapshotView(View v) {
        Bitmap bm = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        cv.save();
        cv.translate(-v.getX(), -v.getY());
        drawChild(cv, v, SystemClock.uptimeMillis());
        cv.restore();
        return bm;
    }

    public void setViews(ActionBarLayout sideLayout, ActionBarLayout contentLayout, View backgroundTablet, View popupShadow, ActionBarLayout popupLayout) {
        this.sideLayout = sideLayout;
        this.contentLayout = contentLayout;
        this.backgroundTablet = backgroundTablet;
        this.popupShadow = popupShadow;
        this.popupLayout = popupLayout;

        removeAllViews();
        addView(backgroundTablet);
        addView(sideLayout);
        addView(contentLayout);
        addView(popupShadow);
        addView(popupLayout);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;

        if (isFullSize()) {
            sideLayout.layout(0, 0, sideLayout.getMeasuredWidth(), sideLayout.getMeasuredHeight());
        } else {
            sideLayout.layout(0, 0, sideLayout.getMeasuredWidth(), sideLayout.getMeasuredHeight());
            contentLayout.layout(sideWidth, 0, sideWidth + contentLayout.getMeasuredWidth(), contentLayout.getMeasuredHeight());
        }
        int x = (w - popupLayout.getMeasuredWidth()) / 2;
        int y = (h - popupLayout.getMeasuredHeight() + (AndroidUtilities.isInMultiwindow ? 0 : AndroidUtilities.statusBarHeight)) / 2;
        popupLayout.layout(x, y, x + popupLayout.getMeasuredWidth(), y + popupLayout.getMeasuredHeight());
        backgroundTablet.layout(0, 0, backgroundTablet.getMeasuredWidth(), backgroundTablet.getMeasuredHeight());
        popupShadow.layout(0, 0, popupShadow.getMeasuredWidth(), popupShadow.getMeasuredHeight());

        if (onLayout != null) {
            onLayout.run();
            onLayout = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec), h = MeasureSpec.getSize(heightMeasureSpec);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int sidePadding = AndroidUtilities.dp(64);

        if (isFullSize()) {
            tabletFullSize = true;
            contentLayout.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        } else {
            tabletFullSize = false;
            sideLayout.measure(MeasureSpec.makeMeasureSpec(sideWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            contentLayout.measure(MeasureSpec.makeMeasureSpec(w - sideWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
        backgroundTablet.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        popupShadow.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        popupLayout.measure(MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(640), dm.widthPixels - sidePadding), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dm.heightPixels - sidePadding - AndroidUtilities.statusBarHeight, MeasureSpec.EXACTLY));

        setMeasuredDimension(w, h);
    }

    /**
     * @return If measured as full size
     */
    public boolean isTabletFullSize() {
        return tabletFullSize;
    }

    /**
     * @return If we should use full size layout
     */
    private boolean isFullSize() {
        return AndroidUtilities.isInMultiwindow || AndroidUtilities.isSmallTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        sideWidth = clampWidth(SharedPrefsHelper.TabletPrefs.getSideWidth());
        invalidate();
    }

    private int clampWidth(float w) {
        return clampWidth(getWidth(), w);
    }

    public static int clampWidth(int displayWidth, float w) {
        int wd = (int) (MathUtils.clamp(w, MIN_SIDE_WIDTH, MAX_SIDE_WIDTH) * displayWidth);
        return MathUtils.clamp(wd, AndroidUtilities.dp(MIN_SIDE_DP), displayWidth - AndroidUtilities.dp(MAX_SIDE_DP));
    }

    private void updateColors() {
        dividerPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider), 0x40));
        shadowPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_actionBarDefault), 0x66));
    }
}
