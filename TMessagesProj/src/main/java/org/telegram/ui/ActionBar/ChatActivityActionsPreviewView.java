package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Arrays;
import java.util.List;

public class ChatActivityActionsPreviewView extends FrameLayout {
    private final static int DURATION = 200;
    private LinearLayout actionsView;
    private View contentView;
    private int actionsWidth;
    private Path mPath = new Path();
    private RectF mRect = new RectF();
    private float[] radii = new float[8];
    private Consumer<Float> progressListener;

    private Rect chatPaddings = new Rect();
    private View blurredView;

    private float mValue;
    private float mEndValue;
    private ValueAnimator valueAnimator;

    private ChatActivity chatActivity;
    private OnDismissListener onDismissListener;

    private int topBottomPadding;

    public ChatActivityActionsPreviewView(@NonNull Context context) {
        super(context);
    }

    public ChatActivityActionsPreviewView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ChatActivityActionsPreviewView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    {
        actionsWidth = AndroidUtilities.isTablet() ? AndroidUtilities.dp(300) : (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.6f);
        Arrays.fill(radii, AndroidUtilities.dp(6));
        topBottomPadding = AndroidUtilities.dp(16);
        setVisibility(GONE);
        setOnClickListener(v -> dismiss());
    }

    /**
     * Sets a listener to run after view is dismissed
     * @param onDismissListener New listener
     */
    public void setOnDismissListener(OnDismissListener onDismissListener) {
        this.onDismissListener = onDismissListener;
    }

    /**
     * Shows chat preview
     */
    public void show() {
        animateTo(true);
    }

    /**
     * Dismisses chat preview
     */
    public void dismiss() {
        animateTo(false);
    }

    /**
     * Animates view to the desired value
     * @param show If we should show view
     */
    private void animateTo(boolean show) {
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
        float from = mValue, to = show ? 1 : 0;
        ValueAnimator animator = ValueAnimator.ofFloat(from, mEndValue = to).setDuration((long) (DURATION * Math.abs(from - to)));
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEnd(animation);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!show) {
                    setVisibility(GONE);
                    if (onDismissListener != null)
                        onDismissListener.onViewDismissed(ChatActivityActionsPreviewView.this);
                }
            }

            @Override
            public void onAnimationStart(Animator animation) {
                if (show)
                    setVisibility(VISIBLE);
            }
        });
        animator.addUpdateListener(animation -> {
            mValue = (float) animation.getAnimatedValue();
            invalidateTransforms();
        });
        animator.start();
        valueAnimator = animator;
    }

    /**
     * Invalidates all transformations
     */
    private void invalidateTransforms() {
        float sc = 0.5f + mValue * 0.5f;
        int pad = AndroidUtilities.dp(8);
        if (contentView != null) {
            contentView.setAlpha(mValue);
            contentView.setScaleX(sc);
            contentView.setScaleY(sc);

            int l = pad, t = topBottomPadding;
            contentView.setTranslationX(l);
            contentView.setTranslationY(t);

            if (actionsView != null) {
                actionsView.setAlpha(mValue);
                actionsView.setScaleX(sc);
                actionsView.setScaleY(sc);

                int at = t + contentView.getMeasuredHeight() + AndroidUtilities.dp(2);
                actionsView.setTranslationX(chatPaddings.left);
                actionsView.setTranslationY(at - actionsView.getMeasuredHeight() * (1f - mValue) * 0.5f);
            }
        }
        if (blurredView != null)
            blurredView.setAlpha(mValue);
    }

    /**
     * Sets view to be blurred
     * @param blurredView View to be blurred
     */
    public void setBlurredView(View blurredView) {
        this.blurredView = blurredView;
        if (blurredView != null) addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    /**
     * Called when back button is pressed
     * @return If we should cancel this event
     */
    public boolean onBackPressed() {
        if (mEndValue != 0) {
            animateTo(false);
            return true;
        }
        return false;
    }

    /**
     * Sets a list of actions
     * @param actions New actions
     * @param callback Callback for actions
     */
    public void setActions(List<Action> actions, Consumer<Action> callback) {
        if (actionsView != null)
            throw new IllegalStateException("Actions are already set!");
        LinearLayout ll = new LinearLayout(getContext());
        Drawable shadowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        ll.setBackground(shadowDrawable);
        ll.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < actions.size(); i++) {
            Action a = actions.get(i);
            ActionBarMenuSubItem s = new ActionBarMenuSubItem(getContext(), i == 0, i == actions.size() - 1);
            s.setTextAndIcon(a.title, a.icon);
            s.setOnClickListener(v -> {
                callback.accept(a);
                dismiss();
            });
            ll.addView(s);
        }
        addView(actionsView = ll);
        invalidateTransforms();
    }

    /**
     * Sets chat activity
     * @param chatActivity Chat activity to set
     */
    public void setChatActivity(ChatActivity chatActivity) {
        if (this.chatActivity != null)
            throw new IllegalStateException("Chat activity are already set!");
        this.chatActivity = chatActivity;
        chatActivity.onResume();
        View v = chatActivity.getFragmentView();

        Drawable shadowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        shadowDrawable.getPadding(chatPaddings);

        FrameLayout c = new FrameLayout(v.getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                int s = canvas.save();
                shadowDrawable.setBounds(0, AndroidUtilities.statusBarHeight, getMeasuredWidth(), getMeasuredHeight());
                shadowDrawable.draw(canvas);

                mRect.set(chatPaddings.left, AndroidUtilities.statusBarHeight + chatPaddings.top,
                        getMeasuredWidth() - chatPaddings.right, getMeasuredHeight() - chatPaddings.bottom);
                mPath.rewind();
                mPath.addRoundRect(mRect, radii, Path.Direction.CW);
                canvas.clipPath(mPath);
                super.dispatchDraw(canvas);
                canvas.restoreToCount(s);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return true;
            }
        };
        c.setPadding(chatPaddings.left, chatPaddings.top, chatPaddings.right, chatPaddings.bottom);
        c.addView(v);
        addView(this.contentView = c);
        invalidateTransforms();
    }

    /**
     * @return Currently attached chat activity
     */
    @Nullable
    public ChatActivity getChatActivity() {
        return chatActivity;
    }

    @Override
    public void removeAllViews() {
        if (chatActivity != null) {
            chatActivity.onPause();
            chatActivity.onFragmentDestroy();
        }
        super.removeAllViews();

        chatActivity = null;
        actionsView = null;
        contentView = null;
        blurredView = null;
        if (valueAnimator != null) {
            valueAnimator.cancel();
            valueAnimator = null;
        }
        mValue = 0;
    }

    /**
     * @return If view is visible
     */
    public boolean isVisible() {
        return mValue != 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int pad = AndroidUtilities.dp(8);
        int ah = 0;
        if (actionsView != null) {
            actionsView.measure(MeasureSpec.makeMeasureSpec(actionsWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) (MeasureSpec.getSize(heightMeasureSpec) * 0.5f), MeasureSpec.AT_MOST));
            ah = actionsView.getMeasuredHeight();
        }

        if (contentView != null) {
            contentView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - pad * 2, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - ah - AndroidUtilities.dp(2) - topBottomPadding * 2, MeasureSpec.AT_MOST));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (contentView != null) {
            contentView.layout(0, 0, contentView.getMeasuredWidth(), contentView.getMeasuredHeight());
            if (actionsView != null) {
                actionsView.layout(0, 0, actionsView.getMeasuredWidth(), actionsView.getMeasuredHeight());
            }
        }
        if (blurredView != null)
            blurredView.layout(0, 0, getWidth(), getHeight());
    }

    public interface OnDismissListener {
        void onViewDismissed(ChatActivityActionsPreviewView v);
    }

    public final static class Action {
        public int id;
        public int icon;
        public String title;

        public Action(int id, @DrawableRes int icon, String title) {
            this.id = id;
            this.icon = icon;
            this.title = title;
        }
    }
}
