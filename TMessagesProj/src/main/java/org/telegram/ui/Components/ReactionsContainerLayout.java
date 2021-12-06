package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ReactionsContainerLayout extends FrameLayout {
    public final static FloatPropertyCompat<ReactionsContainerLayout> TRANSITION_PROGRESS_VALUE = new FloatPropertyCompat<ReactionsContainerLayout>("transitionProgress") {
        @Override
        public float getValue(ReactionsContainerLayout object) {
            return object.transitionProgress * 100f;
        }

        @Override
        public void setValue(ReactionsContainerLayout object, float value) {
            object.setTransitionProgress(value / 100f);
        }
    };

    private final static Random RANDOM = new Random();

    private final static int ALPHA_DURATION = 150;
    private final static float SIDE_SCALE = 0.6f;
    private final static float SCALE_PROGRESS = 0.75f;
    private final static float CLIP_PROGRESS = 0.25f;

    private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint leftShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG),
            rightShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float leftAlpha, rightAlpha;
    private float transitionProgress = 1f;
    private RectF rect = new RectF();
    private Path mPath = new Path();
    private float radius = AndroidUtilities.dp(72);
    private float bigCircleRadius = AndroidUtilities.dp(8);
    private float smallCircleRadius = bigCircleRadius / 2;
    private int bigCircleOffset = AndroidUtilities.dp(36);

    private List<TLRPC.TL_availableReaction> reactionsList = Collections.emptyList();

    private LinearLayoutManager llm;
    private RecyclerView.Adapter listAdapter;

    private int[] location = new int[2];

    private ReactionsContainerDelegate delegate;

    private Rect shadowPad = new Rect();
    private Drawable shadow;

    private List<String> triggeredReactions = new ArrayList<>();

    public ReactionsContainerLayout(@NonNull Context context) {
        super(context);

        shadow = ContextCompat.getDrawable(context, R.drawable.reactions_bubble_shadow).mutate();
        shadowPad.left = shadowPad.top = shadowPad.right = shadowPad.bottom = AndroidUtilities.dp(7);
        shadow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelShadow), PorterDuff.Mode.MULTIPLY));

        RecyclerListView recyclerListView = new RecyclerListView(context);
        llm = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        recyclerListView.setLayoutManager(llm);
        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerListView.setAdapter(listAdapter = new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ReactionHolderView hv = new ReactionHolderView(context);
                int size = getLayoutParams().height - getPaddingTop() - getPaddingBottom();
                hv.setLayoutParams(new RecyclerView.LayoutParams(size, size));
                int pad = AndroidUtilities.dp(6);
                hv.setPadding(pad, pad, pad, pad);
                return new RecyclerListView.Holder(hv);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ReactionHolderView h = (ReactionHolderView) holder.itemView;
                h.setScaleX(1);
                h.setScaleY(1);
                h.setReaction(reactionsList.get(position));
            }

            @Override
            public int getItemCount() {
                return reactionsList.size();
            }
        });
        recyclerListView.setOnItemClickListener((view, position) -> {
            ReactionHolderView h = (ReactionHolderView) view;
            if (delegate != null)
                delegate.onReactionClicked(h, h.currentReaction);
        });
        recyclerListView.addOnScrollListener(new LeftRightShadowsListener());
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (recyclerView.getChildCount() > 2) {
                    float sideDiff = 1f - SIDE_SCALE;

                    recyclerView.getLocationInWindow(location);
                    int rX = location[0];

                    View ch1 = recyclerView.getChildAt(0);
                    ch1.getLocationInWindow(location);
                    int ch1X = location[0];

                    int dX1 = ch1X - rX;
                    float s1 = SIDE_SCALE + (1f - Math.min(1, -Math.min(dX1, 0f) / ch1.getWidth())) * sideDiff;
                    if (Float.isNaN(s1)) s1 = 1f;
                    ch1.setScaleX(s1);
                    ch1.setScaleY(s1);

                    View ch2 = recyclerView.getChildAt(recyclerView.getChildCount() - 1);
                    ch2.getLocationInWindow(location);
                    int ch2X = location[0];

                    int dX2 = rX + recyclerView.getWidth() - (ch2X + ch2.getWidth());
                    float s2 = SIDE_SCALE + (1f - Math.min(1, -Math.min(dX2, 0f) / ch2.getWidth())) * sideDiff;
                    if (Float.isNaN(s2)) s2 = 1f;
                    ch2.setScaleX(s2);
                    ch2.setScaleY(s2);
                }
                for (int i = 1; i < recyclerListView.getChildCount() - 1; i++) {
                    View ch = recyclerListView.getChildAt(i);
                    float sc = 1f;
                    ch.setScaleX(sc);
                    ch.setScaleY(sc);
                }
            }
        });
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int i = parent.getChildAdapterPosition(view);
                if (i == 0)
                    outRect.left = AndroidUtilities.dp(8);
                if (i == listAdapter.getItemCount() - 1)
                    outRect.right = AndroidUtilities.dp(8);
            }
        });
        addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        invalidateShaders();

        bgPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
    }

    /**
     * Sets new delegate
     * @param delegate New delegate
     */
    public void setDelegate(ReactionsContainerDelegate delegate) {
        this.delegate = delegate;
    }

    /**
     * Sets new reactions list
     * @param reactionsList New list
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setReactionsList(List<TLRPC.TL_availableReaction> reactionsList) {
        this.reactionsList = reactionsList;
        listAdapter.notifyDataSetChanged();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float cPr = (Math.max(CLIP_PROGRESS, Math.min(transitionProgress, 1f)) - CLIP_PROGRESS) / (1f - CLIP_PROGRESS);
        float br = bigCircleRadius * cPr, sr = smallCircleRadius * cPr;

        float cx = LocaleController.isRTL ? bigCircleOffset : getWidth() - bigCircleOffset, cy = getHeight() - getPaddingBottom();
        int sPad = AndroidUtilities.dp(3);
        shadow.setBounds((int)(cx - br - sPad * cPr), (int)(cy - br - sPad * cPr), (int)(cx + br + sPad * cPr), (int)(cy + br + sPad * cPr));
        shadow.draw(canvas);
        canvas.drawCircle(cx, cy, br, bgPaint);

        cx = LocaleController.isRTL ? bigCircleOffset - bigCircleRadius : getWidth() - bigCircleOffset + bigCircleRadius;
        cy = getHeight() - smallCircleRadius - sPad;
        sPad = -AndroidUtilities.dp(1);
        shadow.setBounds((int)(cx - br - sPad * cPr), (int)(cy - br - sPad * cPr), (int)(cx + br + sPad * cPr), (int)(cy + br + sPad * cPr));
        shadow.draw(canvas);
        canvas.drawCircle(cx, cy, sr, bgPaint);

        int s = canvas.save();
        mPath.rewind();
        mPath.addCircle(LocaleController.isRTL ? bigCircleOffset : getWidth() - bigCircleOffset, getHeight() - getPaddingBottom(), br, Path.Direction.CW);
        canvas.clipPath(mPath, Region.Op.DIFFERENCE);

        float pivotX = LocaleController.isRTL ? getWidth() * 0.125f : getWidth() * 0.875f;

        if (transitionProgress <= SCALE_PROGRESS) {
            float sc = transitionProgress / SCALE_PROGRESS;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }

        float lt = 0, rt = 1;
        if (LocaleController.isRTL) {
            rt = Math.max(CLIP_PROGRESS, transitionProgress);
        } else {
            lt = (1f - Math.max(CLIP_PROGRESS, transitionProgress));
        }
        rect.set(getPaddingLeft() + (getWidth() - getPaddingRight()) * lt, getPaddingTop(), (getWidth() - getPaddingRight()) * rt, getHeight() - getPaddingBottom());
        shadow.setBounds((int) (getPaddingLeft() + (getWidth() - getPaddingRight() + shadowPad.right) * lt - shadowPad.left), getPaddingTop() - shadowPad.top, (int) ((getWidth() - getPaddingRight() + shadowPad.right) * rt), getHeight() - getPaddingBottom() + shadowPad.bottom);
        shadow.draw(canvas);
        canvas.restoreToCount(s);

        s = canvas.save();
        if (transitionProgress <= SCALE_PROGRESS) {
            float sc = transitionProgress / SCALE_PROGRESS;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint);
        canvas.restoreToCount(s);

        mPath.rewind();
        mPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        s = canvas.save();
        if (transitionProgress <= SCALE_PROGRESS) {
            float sc = transitionProgress / SCALE_PROGRESS;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }
        canvas.translate((LocaleController.isRTL ? -1 : 1) * getWidth() * (1f - transitionProgress), 0);
        canvas.clipPath(mPath);
        super.dispatchDraw(canvas);

        canvas.restoreToCount(s);
        s = canvas.save();
        if (LocaleController.isRTL) rt = Math.max(CLIP_PROGRESS, Math.min(1, transitionProgress));
        else lt = 1f - Math.max(CLIP_PROGRESS, Math.min(1f, transitionProgress));
        rect.set(getPaddingLeft() + (getWidth() - getPaddingRight()) * lt, getPaddingTop(), (getWidth() - getPaddingRight()) * rt, getHeight() - getPaddingBottom());
        mPath.rewind();
        mPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(mPath);
        if (leftShadowPaint != null) {
            leftShadowPaint.setAlpha((int) (leftAlpha * transitionProgress * 0xFF));
            canvas.drawRect(rect, leftShadowPaint);
        }
        if (rightShadowPaint != null) {
            rightShadowPaint.setAlpha((int) (rightAlpha * transitionProgress * 0xFF));
            canvas.drawRect(rect, rightShadowPaint);
        }
        canvas.restoreToCount(s);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateShaders();
    }

    /**
     * Invalidates shaders
     */
    private void invalidateShaders() {
        int dp = AndroidUtilities.dp(24);
        float cy = getHeight() / 2f;
        int clr = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);
        leftShadowPaint.setShader(new LinearGradient(0, cy, dp, cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        rightShadowPaint.setShader(new LinearGradient(getWidth(), cy, getWidth() - dp, cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        invalidate();
    }

    /**
     * Sets new transition progress
     * @param transitionProgress New transition progress
     */
    public void setTransitionProgress(float transitionProgress) {
        this.transitionProgress = transitionProgress;
        invalidate();
    }

    private final class LeftRightShadowsListener extends RecyclerView.OnScrollListener {
        private boolean leftVisible, rightVisible;
        private ValueAnimator leftAnimator, rightAnimator;

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            boolean l = llm.findFirstVisibleItemPosition() != 0;
            if (l != leftVisible) {
                if (leftAnimator != null)
                    leftAnimator.cancel();
                leftAnimator = startAnimator(leftAlpha, l ? 1 : 0, aFloat -> {
                    leftShadowPaint.setAlpha((int) ((leftAlpha = aFloat) * 0xFF));
                    invalidate();
                }, () -> leftAnimator = null);

                leftVisible = l;
            }

            boolean r = llm.findLastVisibleItemPosition() != listAdapter.getItemCount() - 1;
            if (r != rightVisible) {
                if (rightAnimator != null)
                    rightAnimator.cancel();
                rightAnimator = startAnimator(rightAlpha, r ? 1 : 0, aFloat -> {
                    rightShadowPaint.setAlpha((int) ((rightAlpha = aFloat) * 0xFF));
                    invalidate();
                }, () -> rightAnimator = null);

                rightVisible = r;
            }
        }

        private ValueAnimator startAnimator(float fromAlpha, float toAlpha, Consumer<Float> callback, Runnable onEnd) {
            ValueAnimator a = ValueAnimator.ofFloat(fromAlpha, toAlpha).setDuration((long) (Math.abs(toAlpha - fromAlpha) * ALPHA_DURATION));
            a.addUpdateListener(animation -> callback.accept((Float) animation.getAnimatedValue()));
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
            a.start();
            return a;
        }
    }

    private final class ReactionHolderView extends View {
        private final static int RANDOM_DELAY = 500;
        private final static float RANDOM_PERCENT = 0.25f;

        ImageReceiver receiver;
        TLRPC.TL_availableReaction currentReaction;
        boolean isAttached;
        boolean triggered = true;
        Runnable trigger = () -> {
            if (RANDOM.nextFloat() <= RANDOM_PERCENT) {
                receiver.setAllowStartLottieAnimation(true);
                receiver.startLottieOnce();
                triggered = true;
                triggeredReactions.add(currentReaction.reaction);
            }
            if (isAttached && !triggered)
                postDelayed(this.trigger, RANDOM_DELAY);
        };

        ReactionHolderView(Context context) {
            super(context);
            receiver = new ImageReceiver(this);
            receiver.setAutoRepeat(0);
            receiver.setParentView(this);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            receiver.onAttachedToWindow();

            isAttached = true;
            if (!triggered) post(trigger);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            receiver.onDetachedFromWindow();

            removeCallbacks(trigger);
            isAttached = false;
        }

        /**
         * Sets new reaction
         * @param react New reaction
         */
        private void setReaction(TLRPC.TL_availableReaction react) {
            currentReaction = react;

            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(currentReaction.select_animation.thumbs, Theme.key_windowBackgroundGray, 1.0f);
            receiver.setAllowStartLottieAnimation(false);
            receiver.setImage(ImageLocation.getForDocument(currentReaction.select_animation), "80_80", null, null, svgThumb, 0, "tgs", react, 0);
            receiver.rewindLottie();

            triggered = triggeredReactions.contains(react.reaction);
            if (!triggered) post(trigger);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            invalidateImageSize();
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            super.setPadding(left, top, right, bottom);
            invalidateImageSize();
        }

        private void invalidateImageSize() {
            receiver.setImageCoords(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight() - getPaddingLeft(), getHeight() - getPaddingBottom() - getPaddingTop());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            receiver.draw(canvas);
        }
    }

    public interface ReactionsContainerDelegate {
        /**
         * Called when reaction is being clicked
         * @param v View that is being clicked
         * @param reaction Reaction that is being clicked
         */
        void onReactionClicked(View v, TLRPC.TL_availableReaction reaction);
    }
}
