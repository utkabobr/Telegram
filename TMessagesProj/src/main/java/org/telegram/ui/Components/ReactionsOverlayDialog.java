package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ReactionsOverlayDialog extends Dialog {
    private final static int EMOJI_BIG_SIZE_DP = 180, EFFECT_SIZE_DP = 300;
    private final static int DELAY_OUT = 1000;

    private final OverlayParams params;

    private EmojiView emojiView;
    private EffectView effectView;
    private OnDismissListener preDismissListener;
    private OnShowListener postShowListener;
    private Runnable endPosPoll;

    public ReactionsOverlayDialog(@NonNull Context context, OverlayParams params) {
        super(context, R.style.TransparentDialogNoAnimation);
        this.params = params;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            getWindow().getAttributes().windowAnimations = 0;
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            getWindow().setStatusBarColor(0);
            getWindow().setNavigationBarColor(0);

            int color = Theme.getColor(Theme.key_actionBarDefault, null, true);
            AndroidUtilities.setLightStatusBar(getWindow(), color == Color.WHITE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int color2 = Theme.getColor(Theme.key_windowBackgroundGray, null, true);
                float brightness = AndroidUtilities.computePerceivedBrightness(color2);
                AndroidUtilities.setLightNavigationBar(getWindow(), brightness >= 0.721f);
            }
        }

        Context ctx = getContext();
        FrameLayout fl = new FrameLayout(ctx);
        emojiView = new EmojiView(ctx);
        emojiView.setVisibility(View.INVISIBLE);
        effectView = new EffectView(ctx);
        effectView.setVisibility(View.INVISIBLE);
        fl.addView(emojiView, LayoutHelper.createFrame(EMOJI_BIG_SIZE_DP, EMOJI_BIG_SIZE_DP));
        fl.addView(effectView, LayoutHelper.createFrame(EFFECT_SIZE_DP, EFFECT_SIZE_DP));
        setContentView(fl);
    }

    @Override
    public void show() {
        super.show();

        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();

        emojiView.setPivotX(0);
        emojiView.setPivotY(0);

        float emojiScaleStart = params.startSize / AndroidUtilities.dp(EMOJI_BIG_SIZE_DP);
        emojiView.setScaleX(emojiScaleStart);
        emojiView.setScaleY(emojiScaleStart);
        emojiView.setTranslationX(params.startX);
        emojiView.setTranslationY(params.startY);

        float midX = Math.max(0, Math.min(dm.widthPixels - AndroidUtilities.dp(EMOJI_BIG_SIZE_DP), params.endX - AndroidUtilities.dp(EMOJI_BIG_SIZE_DP) / 2f + params.endSize / 2f)),
                midY = Math.max(0, Math.min(dm.heightPixels - AndroidUtilities.dp(EMOJI_BIG_SIZE_DP), params.endY - AndroidUtilities.dp(EMOJI_BIG_SIZE_DP) / 2f + params.endSize / 2f));

        float cX = dm.widthPixels - AndroidUtilities.dp(EFFECT_SIZE_DP), cY = dm.heightPixels - AndroidUtilities.dp(EFFECT_SIZE_DP);
        int deltaEffect = AndroidUtilities.dp(EFFECT_SIZE_DP - EMOJI_BIG_SIZE_DP);
        float eX = midX - deltaEffect / 2f, eY = midY - deltaEffect / 2f;
        effectView.setTranslationX(Math.max(0, Math.min(cX, eX)));
        effectView.setTranslationY(Math.max(0, Math.min(cY, eY)));
        if (eX < 0) midX -= eX;
        if (eY < 0) midY -= eY;
        if (eX > cX) midX += cX - eX;
        if (eY > cY) midY += cY - eY;

        float emojiScaleMiddle = 1f;
        ValueAnimator scaleIn = ValueAnimator.ofFloat(0, 1).setDuration(300);
        scaleIn.setInterpolator(CubicBezierInterpolator.DEFAULT);
        float finalMidY = midY;
        float finalMidX = midX;
        scaleIn.addUpdateListener(animation -> {
            float v = (float) animation.getAnimatedValue();
            float sc = emojiScaleStart + (emojiScaleMiddle - emojiScaleStart) * v;
            emojiView.setScaleX(sc);
            emojiView.setScaleY(sc);

            emojiView.setTranslationX(params.startX + (finalMidX - params.startX) * v);
            emojiView.setTranslationY(params.startY + (finalMidY - params.startY) * v);
        });

        float emojiScaleEnd = params.endSize / AndroidUtilities.dp(EMOJI_BIG_SIZE_DP);
        ValueAnimator scaleOut = ValueAnimator.ofFloat(0, 1).setDuration(300);
        scaleOut.setInterpolator(CubicBezierInterpolator.DEFAULT);
        scaleOut.addUpdateListener(animation -> {
            float v = (float) animation.getAnimatedValue();
            float sc = emojiScaleMiddle + (emojiScaleEnd - emojiScaleMiddle) * v;
            emojiView.setScaleX(sc);
            emojiView.setScaleY(sc);

            emojiView.setTranslationX(finalMidX + (params.endX - finalMidX) * v);
            emojiView.setTranslationY(finalMidY + (params.endY - finalMidY) * v);
        });

        emojiView.imageReceiver.setOnLottieDrawableCreatedListener(rLottieDrawable -> {
            emojiView.setVisibility(View.VISIBLE);
            effectView.setVisibility(View.VISIBLE);

            if (postShowListener != null)
                postShowListener.onShow(this);

            long d = rLottieDrawable.getDuration();

            ValueAnimator delayMid = ValueAnimator.ofFloat(0).setDuration(d - DELAY_OUT);
            ValueAnimator delayOut = ValueAnimator.ofFloat(0).setDuration(DELAY_OUT);
            ValueAnimator effectAlphaOut = ValueAnimator.ofFloat(1, 0).setDuration(150);
            effectAlphaOut.addUpdateListener(animation -> effectView.setAlpha((Float) animation.getAnimatedValue()));
            AnimatorSet set = new AnimatorSet();
            List<Animator> animators = Arrays.asList(scaleIn, delayMid, scaleOut, delayOut, effectAlphaOut);
            for (Animator a : animators) {
                ((ValueAnimator) a).addUpdateListener(animation -> {
                    if (endPosPoll != null) {
                        endPosPoll.run();
                    }
                });
            }
            set.playSequentially(animators);
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dismiss();
                }
            });
            set.start();
        });

        emojiView.setReaction(params.reaction);
        effectView.setReaction(params.reaction);
    }

    public void setEndPosPoll(Runnable endPosPoll) {
        this.endPosPoll = endPosPoll;
    }

    public void setOnPostShowListener(@Nullable OnShowListener listener) {
        postShowListener = listener;
    }

    public void setOnPreDismissListener(@Nullable OnDismissListener listener) {
        preDismissListener = listener;
    }

    @Override
    public void dismiss() {
        if (preDismissListener != null)
            preDismissListener.onDismiss(this);
        emojiView.postDelayed(super::dismiss, 50);
    }

    public final static class OverlayParams {
        public float startX, startY;
        public float startSize;

        public float endX, endY;
        public float endSize;

        public TLRPC.TL_availableReaction reaction;
    }

    private final static class EffectView extends View {
        private ImageReceiver imageReceiver = new ImageReceiver();

        EffectView(Context context) {
            super(context);
            imageReceiver.setAutoRepeat(0);
            imageReceiver.setParentView(this);
        }

        void setReaction(TLRPC.TL_availableReaction reaction) {
            imageReceiver.setAllowStartLottieAnimation(false);
            imageReceiver.setImage(ImageLocation.getForDocument(reaction.effect_animation), "200_200", null, "tgs", UUID.randomUUID(), 0);
            imageReceiver.setAllowStartLottieAnimation(true);
            imageReceiver.startLottieOnce();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
            imageReceiver.draw(canvas);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            imageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            imageReceiver.onDetachedFromWindow();
        }
    }

    private final static class EmojiView extends View {
        private ImageReceiver imageReceiver = new ImageReceiver();

        EmojiView(Context context) {
            super(context);
            imageReceiver.setAutoRepeat(0);
            imageReceiver.setParentView(this);
        }

        void setReaction(TLRPC.TL_availableReaction reaction) {
            imageReceiver.setAllowStartLottieAnimation(false);
            imageReceiver.setImage(ImageLocation.getForDocument(reaction.activate_animation), "100_100", null, "tgs", UUID.randomUUID(), 0);
            imageReceiver.setAllowStartLottieAnimation(true);
            imageReceiver.startLottieOnce();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
            imageReceiver.draw(canvas);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            imageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            imageReceiver.onDetachedFromWindow();
        }
    }
}
