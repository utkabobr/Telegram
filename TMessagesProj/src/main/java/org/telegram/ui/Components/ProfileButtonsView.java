package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public class ProfileButtonsView extends FrameLayout {
    private final static int SPACING_DP = 8;
    private List<ProfileButton> mainButtons = new ArrayList<>(4);
    private List<ProfileButton> overflowButtons = new ArrayList<>();

    private float buttonWidth;
    private Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Consumer<ProfileButton> onClickListener;
    private boolean blurEnabled;
    private float isDarkProgress = -1;
    private float openProgress;
    private Path path = new Path();
    private Impl impl;

    private View avatar;
    private ProfileGalleryView pager;
    private Paint rPaint = new Paint();
    private RecordDrawer drawer = canvas -> {
        if (avatar.getVisibility() == View.VISIBLE) {
            canvas.save();
            canvas.translate(avatar.getX() - getX(), avatar.getY() - getY());
            canvas.scale(avatar.getScaleX(), avatar.getScaleY(), avatar.getPivotX(), avatar.getPivotY());
            canvas.clipRect(0, 0, avatar.getWidth() * avatar.getScaleX(), avatar.getHeight() * avatar.getScaleY());
            avatar.draw(canvas);
            canvas.restore();
        }

        if (pager.getVisibility() == View.VISIBLE) {
            canvas.save();
            canvas.translate(pager.getX() - getX(), pager.getY() - getY());
            canvas.scale(pager.getScaleX(), pager.getScaleY(), pager.getPivotX(), pager.getPivotY());
            canvas.clipRect(0, 0, pager.getWidth(), pager.getHeight());

            int item = pager.getCurrentItem();
            for (int i = -1; i <= 1; i++) {
                View ch = pager.getItemView(item + i);
                if (ch != null) {
                    float transformPos = (float) (ch.getLeft() - pager.getScrollX()) / pager.getWidth();
                    if (transformPos <= 1f) {
                        canvas.save();
                        canvas.translate(pager.getWidth() * transformPos, 0);
                        ch.draw(canvas);
                        canvas.restore();
                    }
                }
            }
            canvas.restore();
        }
    };

    public ProfileButtonsView(Context context, View avatar, ProfileGalleryView pager) {
        super(context);
        setWillNotDraw(false);
        this.avatar = avatar;
        this.pager = pager;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
            impl = new GPUImpl(SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH ? 1f : 2f);
        } else {
            impl = new NoopImpl();
        }
        impl.setBlurRadius(20f);
        rPaint.setColor(0x42000000);
        setIsDarkProgress(0f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        impl.onSizeChanged(w, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        impl.release();
    }

    public void setOpenProgress(float openProgress) {
        if (this.openProgress == openProgress) {
            return;
        }
        this.openProgress = openProgress;
        rPaint.setColor(Color.argb((int) (0x42 * openProgress), 0, 0, 0));
        invalidate();
    }

    public void setIsDarkProgress(float progress) {
        if (isDarkProgress == progress) {
            return;
        }
        isDarkProgress = progress;
        p.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_listSelector), 0x21000000, progress));
        invalidate();
    }

    public void setBlurEnabled(boolean enabled) {
        if (blurEnabled == enabled) {
            return;
        }
        blurEnabled = enabled;
        invalidate();
    }

    public void setButtonClickListener(Consumer<ProfileButton> onClickListener) {
        this.onClickListener = onClickListener;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        path.rewind();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            AndroidUtilities.rectTmp.set(v.getLeft(), v.getTop() + v.getHeight() * (1f - v.getScaleY()), v.getRight(), v.getBottom());
            path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12), AndroidUtilities.dp(12), Path.Direction.CW);
        }

        if (blurEnabled) {
            if (impl.canBlur()) {
                impl.record(drawer);
                impl.draw(canvas, path);
                invalidate();
            } else {
                canvas.drawPath(path, rPaint);
            }
        }
        canvas.drawPath(path, p);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));

        if (!mainButtons.isEmpty()) {
            buttonWidth = (float) (MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(SPACING_DP) * (mainButtons.size() - 1)) / mainButtons.size();
        }
        int w = (int) (MeasureSpec.getSize(widthMeasureSpec) * buttonWidth);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            ProfileButton btn = ((ProfileButtonView) v).button;
            int j = mainButtons.indexOf(btn);
            if (j != -1) {
                float l = getPaddingLeft() + (buttonWidth + AndroidUtilities.dp(SPACING_DP)) * j;
                v.layout((int) l, 0, (int) (l + buttonWidth), getMeasuredHeight());
            } else {
                removeViewAt(i);
                i--;
            }
        }
    }

    public void setExpandProgress(float v) {
        setAlpha((MathUtils.clamp(v, 0.15f, 0.35f) - 0.15f) / 0.2f);
        float sc = (MathUtils.clamp(v, 0.1f, 0.5f) - 0.1f) / 0.4f;
        for (int i = 0; i < getChildCount(); i++) {
            ProfileButtonView btn = (ProfileButtonView) getChildAt(i);
            btn.setScaleX(AndroidUtilities.lerp(0.2f, 1f, sc));
            btn.setScaleY(AndroidUtilities.lerp(0.2f, 1f, sc));
            btn.setPivotX(btn.getWidth() / 2f);
            btn.setPivotY(btn.getHeight());
        }
        invalidate();
    }

    public View getButtonView(ProfileButton btn) {
        for (int i = 0; i < getChildCount(); i++) {
            ProfileButtonView buttonView = (ProfileButtonView) getChildAt(i);
            if (buttonView.button == btn) {
                return buttonView;
            }
        }
        return this;
    }

    public void setButtons(List<ProfileButton> buttons, boolean animate) {
        mainButtons.clear();
        overflowButtons.clear();

        int limit = 4;
        for (ProfileButton btn : buttons) {
            if (mainButtons.size() >= limit) {
                overflowButtons.add(btn);
            } else {
                mainButtons.add(btn);
            }
        }
        if (!overflowButtons.isEmpty()) {
            if (overflowButtons.contains(ProfileButton.GIFT) && buttons.contains(ProfileButton.DISCUSS)) {
                overflowButtons.remove(ProfileButton.GIFT);
                buttons.remove(ProfileButton.DISCUSS);

                overflowButtons.add(ProfileButton.DISCUSS);
                buttons.add(ProfileButton.GIFT);
            }
        }

        for (ProfileButton btn : mainButtons) {
            boolean contains = false;
            for (int i = 0; i < getChildCount(); i++) {
                if (btn == ((ProfileButtonView) getChildAt(i)).button) {
                    contains = true;
                    break;
                }
            }
            if (contains) continue;
            addView(new ProfileButtonView(getContext()).bind(btn, () -> {
                if (onClickListener != null) {
                    onClickListener.accept(btn);
                }
            }));
        }
        requestLayout();
    }

    public List<ProfileButton> getOverflowButtons() {
        return overflowButtons;
    }

    public enum ProfileButton {
        CHANGE_AVATAR(R.drawable.msg_filled_data_sent, R.string.ChangeAvatar),
        QR_CODE(R.drawable.msg_qr_mini, R.string.QrCode),
        MESSAGE(R.drawable.msg_filled_message, R.string.TypeMessage),
        JOIN(R.drawable.msg_filled_join, R.string.VoipChatJoin),
        MUTE(R.drawable.msg_filled_mute, R.string.Mute),
        UNMUTE(R.drawable.msg_filled_unmute, R.string.Unmute),
        CALL(R.drawable.msg_filled_call, R.string.Call),
        GIFT(R.drawable.msg_filled_gift, R.string.ActionStarGift),
        VIDEO(R.drawable.msg_filled_video, R.string.GroupCallCreateVideo),
        SHARE(R.drawable.msg_filled_share, R.string.VoipChatShare),
        LIVE_STREAM(R.drawable.msg_filled_live_stream, R.string.StartVoipChannelTitle),
        LEAVE(R.drawable.msg_filled_leave, R.string.VoipGroupLeave),
        ADD_STORY(R.drawable.msg_filled_story, R.string.AddStory),
        BLOCK(R.drawable.msg_filled_block, R.string.BizBotStop),
        DISCUSS(R.drawable.msg_filled_message, R.string.Discuss),
        REPORT(R.drawable.msg_filled_report, R.string.ReportChat);

        public final int icon, title;

        ProfileButton(int icon, int title) {
            this.icon = icon;
            this.title = title;
        }
    }

    private final static class ProfileButtonView extends LinearLayout {
        ImageView icon;
        TextView title;
        ProfileButton button;

        ProfileButtonView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);

            icon = new ImageView(context);
            icon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            addView(icon, LayoutHelper.createLinear(28, 28));

            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            title.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            title.setTextColor(Color.WHITE);
            addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        }

        ProfileButtonView bind(ProfileButton button, Runnable onClick) {
            this.button = button;
            icon.setImageResource(button.icon);
            title.setText(button.title);
            setBackground(Theme.AdaptiveRipple.createRect(0, Theme.getColor(Theme.key_listSelector), 16));
            setOnClickListener(v -> onClick.run());
            return this;
        }
    }

    private final class NoopImpl implements Impl {

        @Override
        public void onSizeChanged(int w, int h) {}

        @Override
        public void setBlurRadius(float radius) {}

        @Override
        public void record(RecordDrawer drawer) {}

        @Override
        public void draw(Canvas canvas, Path path) {}

        @Override
        public boolean canBlur() {
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private final class GPUImpl implements Impl {
        private RenderNode blur = new RenderNode("blur");
        private float scaleFactor;
        private Paint shift = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float factorMult;

        private GPUImpl(float factorMult) {
            this.factorMult = factorMult;
            this.scaleFactor = 4f * factorMult;

            float amount = 0.225f;
            float scale = 1f - 2f * amount;
            // Shift light to the dark, dark to the light
            shift.setColorFilter(new ColorMatrixColorFilter(new float[] {
                    scale, 0f, 0f, 0f, amount * 0xFF,
                    0f, scale, 0f, 0f, amount * 0xFF,
                    0f, 0f, scale, 0f, amount * 0xFF,
                    0f, 0f, 0f, 1f, 0f
            }));
        }

        @Override
        public void onSizeChanged(int w, int h) {
            blur.setPosition(0, 0, (int) Math.ceil(w / scaleFactor), (int) Math.ceil(h / scaleFactor));
        }

        @Override
        public void setBlurRadius(float radius) {
            radius /= factorMult;
            blur.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        }

        @Override
        public void record(RecordDrawer drawer) {
            Canvas c = blur.beginRecording();
            c.scale(1f / scaleFactor, 1f / scaleFactor);
            drawer.draw(c);
            blur.endRecording();
        }

        @Override
        public void draw(Canvas canvas, Path path) {
            canvas.saveLayer(0, 0, getWidth(), getHeight(), shift);
            canvas.clipPath(path);
            canvas.scale(scaleFactor, scaleFactor);
            canvas.drawRenderNode(blur);
            canvas.restore();
        }
    }

    private interface Impl {
        void onSizeChanged(int w, int h);
        void setBlurRadius(float radius);
        void record(RecordDrawer drawer);
        void draw(Canvas canvas, Path path);
        default boolean canBlur() {
            return true;
        }
        default void release() {}
    }

    private interface RecordDrawer {
        void draw(Canvas canvas);
    }
}
