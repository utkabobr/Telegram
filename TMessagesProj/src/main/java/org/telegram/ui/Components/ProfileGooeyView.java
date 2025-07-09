package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotchInfoUtils;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ProfileActivity;

import java.util.Arrays;

public class ProfileGooeyView extends FrameLayout {
    private static float BLACK_KING_BAR = 32;

    private Paint fadeToTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint fadeToBottom = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Impl impl;
    private float intensity;
    private float blurIntensity;
    private boolean enabled;

    private NotchInfoUtils.NotchInfo notchInfo;

    public ProfileGooeyView(Context context) {
        super(context);

        blackPaint.setColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
            impl = new GPUImpl(SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH ? 1f : 1.5f);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH) {
            impl = new CPUImpl();
        } else {
            impl = new NoopImpl();
        }
        setIntensity(10f);
        setBlurIntensity(0f);
        setWillNotDraw(false);
    }

    public float getEndOffset(boolean occupyStatusBar, float avatarScale) {
        if (notchInfo != null) {
            return -(AndroidUtilities.dp(16) + (notchInfo.isLikelyCircle ? notchInfo.bounds.width() + notchInfo.bounds.width() * getAvatarEndScale() : notchInfo.bounds.height() - notchInfo.bounds.top));
        }

        return -((occupyStatusBar ? AndroidUtilities.statusBarHeight : 0) + AndroidUtilities.dp(16) + AndroidUtilities.dp(ProfileActivity.AVATAR_SIZE_DP));
    }

    public float getAvatarEndScale() {
        if (notchInfo != null) {
            float f;
            if (notchInfo.isLikelyCircle) {
                f = (notchInfo.bounds.width() - AndroidUtilities.dp(2)) / AndroidUtilities.dp(ProfileActivity.AVATAR_SIZE_DP);
            } else {
                f = Math.min(notchInfo.bounds.width(), notchInfo.bounds.height()) / AndroidUtilities.dp(ProfileActivity.AVATAR_SIZE_DP);
            }
            return Math.min(0.8f, f);
        }

        return 0.8f;
    }

    public boolean hasNotchInfo() {
        return notchInfo != null;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
        impl.setIntensity(intensity);
        invalidate();
    }

    public void setBlurIntensity(float intensity) {
        this.blurIntensity = intensity;
        impl.setBlurIntensity(intensity);
        invalidate();
    }

    public void setGooeyEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        notchInfo = NotchInfoUtils.getInfo(getContext());
        if (notchInfo != null && (notchInfo.gravity != Gravity.CENTER) || getWidth() > getHeight()) {
            notchInfo = null;
        }

        impl.onSizeChanged(w, h);

        fadeToTop.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        fadeToTop.setShader(new LinearGradient(getWidth() / 2f, 0, getWidth() / 2f, AndroidUtilities.dp(100), new int[] {0xFF000000, 0xFFFFFFFF}, new float[]{0.15f, 1f}, Shader.TileMode.CLAMP));

        fadeToBottom.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        fadeToBottom.setShader(new LinearGradient(getWidth() / 2f, 0, getWidth() / 2f, AndroidUtilities.dp(100), new int[] {0xFFFFFFFF, 0xFF000000}, new float[]{0.25f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!enabled) {
            super.draw(canvas);
            return;
        }
        impl.draw(c -> {
            c.save();
            c.translate(0, AndroidUtilities.dp(BLACK_KING_BAR));
            super.draw(c);
            c.restore();
        }, canvas);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        impl.release();
    }

    private final static class NoopImpl implements Impl {

        @Override
        public void setIntensity(float intensity) {}

        @Override
        public void setBlurIntensity(float intensity) {}

        @Override
        public void onSizeChanged(int w, int h) {}

        @Override
        public void draw(Drawer drawer, Canvas canvas) {
            canvas.save();
            canvas.translate(0, -AndroidUtilities.dp(BLACK_KING_BAR));
            drawer.draw(canvas);
            canvas.restore();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private final class CPUImpl implements Impl {
        private Paint filter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap[] bitmaps = new Bitmap[2];
        private Canvas[] bitmapCanvas = new Canvas[bitmaps.length];
        private Paint bitmapPaint = new Paint(Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);

        @Override
        public void setIntensity(float intensity) {
            filter.setColorFilter(new ColorMatrixColorFilter(new float[] {
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 4f * intensity, -125 * 4 * intensity
            }));
        }

        @Override
        public void setBlurIntensity(float intensity) {}

        @Override
        public void onSizeChanged(int w, int h) {
            for (Bitmap bm : bitmaps) {
                if (bm != null) {
                    bm.recycle();
                }
            }
            bitmaps[0] = Bitmap.createBitmap(w, h + AndroidUtilities.dp(BLACK_KING_BAR), Bitmap.Config.ARGB_8888);
            bitmapCanvas[0] = new Canvas(bitmaps[0]);

            bitmaps[1] = Bitmap.createBitmap(w / 4, h / 4, Bitmap.Config.ARGB_8888);
            bitmapCanvas[1] = new Canvas(bitmaps[1]);
        }

        @Override
        public void draw(Drawer drawer, Canvas canvas) {
            if (bitmaps[0] == null) return;

            // Draw into buffers
            for (int i = 0; i < 2; i++) {
                bitmaps[i].eraseColor(0);
            }
            drawer.draw(bitmapCanvas[0]);

            float scX = bitmaps[1].getWidth() / (float) bitmaps[0].getWidth(),
                  scY = bitmaps[1].getWidth() / (float) bitmaps[0].getWidth();
            bitmapCanvas[1].save();
            bitmapCanvas[1].scale(scX, scY);
            bitmapCanvas[1].drawBitmap(bitmaps[0], 0, 0, null);
            if (notchInfo != null) {
                bitmapCanvas[1].translate(0, AndroidUtilities.dp(BLACK_KING_BAR));
                if (notchInfo.isLikelyCircle) {
                    float rad = Math.min(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                    bitmapCanvas[1].drawCircle(notchInfo.bounds.centerX(), notchInfo.bounds.bottom - notchInfo.bounds.width() / 2f, rad, blackPaint);
                } else if (notchInfo.isAccurate) {
                    bitmapCanvas[1].drawPath(notchInfo.path, blackPaint);
                } else {
                    float rad = Math.max(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                    bitmapCanvas[1].drawRoundRect(notchInfo.bounds, rad, rad, blackPaint);
                }
            } else {
                bitmapCanvas[1].drawRect(0, 0, getWidth(), AndroidUtilities.dp(BLACK_KING_BAR), blackPaint);
            }
            bitmapCanvas[1].restore();

            // Blur buffer
            Utilities.stackBlurBitmap(bitmaps[1], (int) (intensity / 2));

            // Offset everything for black bar
            canvas.translate(0, -AndroidUtilities.dp(BLACK_KING_BAR));
            canvas.save();

            // Filter alpha + fade, then draw
            canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            canvas.saveLayer(0, 0, getWidth(), getHeight(), filter);
            canvas.scale(1f / scX, 1f / scY);
            canvas.drawBitmap(bitmaps[1], 0, 0, bitmapPaint);
            canvas.restore();
            canvas.drawRect(0, 0, getWidth(), getHeight(), fadeToBottom);
            canvas.restore();

            // Fade, draw blurred
            canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            canvas.saveLayer(0, 0, getWidth(), getHeight(), filter);
            float v = (MathUtils.clamp(blurIntensity, 0.22f, 0.24f) - 0.22f) / (0.24f - 0.22f);
            bitmapPaint.setAlpha((int) (v * 0xFF));
            canvas.scale(1f / scX,  1f / scY);
            canvas.drawBitmap(bitmaps[1], 0, 0, bitmapPaint);
            canvas.restore();
            if (v != 1) {
                bitmapPaint.setAlpha((int) ((1f - v) * 0xFF));
                canvas.drawBitmap(bitmaps[0], 0, 0, bitmapPaint);
            }
            canvas.drawRect(0, 0, getWidth(), getHeight(), fadeToTop);
            canvas.restore();

            canvas.restore();
        }

        @Override
        public void release() {
            for (Bitmap bm : bitmaps) {
                if (bm != null) {
                    bm.recycle();
                }
            }
            Arrays.fill(bitmaps, null);
            Arrays.fill(bitmapCanvas, null);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private final class GPUImpl implements Impl {
        private Paint filter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RenderNode node = new RenderNode("render");
        private RenderNode effectNode = new RenderNode("effect");
        private RenderNode blurNode = new RenderNode("blur");
        private float factorMult;

        private GPUImpl(float factorMult) {
            this.factorMult = factorMult;
        }

        @Override
        public void setIntensity(float intensity) {
            effectNode.setRenderEffect(RenderEffect.createBlurEffect(intensity / factorMult, intensity / factorMult, Shader.TileMode.CLAMP));
            filter.setColorFilter(new ColorMatrixColorFilter(new float[] {
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 4 * intensity, -125 * 4 * intensity
            }));
        }

        @Override
        public void setBlurIntensity(float blurIntensity) {
            if (blurIntensity == 0) {
                blurNode.setRenderEffect(null);
                return;
            }
            blurNode.setRenderEffect(RenderEffect.createBlurEffect(blurIntensity * intensity / factorMult, blurIntensity * intensity / factorMult, Shader.TileMode.DECAL));
        }

        @Override
        public void onSizeChanged(int w, int h) {
            int off = AndroidUtilities.dp(BLACK_KING_BAR);
            node.setPosition(0, 0, w, h + off);
            effectNode.setPosition(0, 0, w, h + off);
            blurNode.setPosition(0, 0, w, h + off);
        }

        @Override
        public void draw(Drawer drawer, @NonNull Canvas canvas) {
            // Record everything into buffer
            drawer.draw(node.beginRecording());
            node.endRecording();

            // Blur only buffer
            float blurScaleFactor = factorMult / 4f + 1.5f + blurIntensity * (5f - factorMult);
            Canvas c = blurNode.beginRecording();
            c.scale(1f / blurScaleFactor, 1f / blurScaleFactor, 0, 0);
            c.drawRenderNode(node);
            blurNode.endRecording();

            // Blur + filter buffer
            float gooScaleFactor = 2f + factorMult;
            c = effectNode.beginRecording();
            c.scale(1f / gooScaleFactor, 1f / gooScaleFactor, 0, 0);
            c.drawRenderNode(node);
            if (notchInfo != null) {
                c.translate(0, AndroidUtilities.dp(BLACK_KING_BAR));
                if (notchInfo.isLikelyCircle) {
                    float rad = Math.min(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                    c.drawCircle(notchInfo.bounds.centerX(), notchInfo.bounds.bottom - notchInfo.bounds.width() / 2f, rad, blackPaint);
                } else if (notchInfo.isAccurate) {
                    c.drawPath(notchInfo.path, blackPaint);
                } else {
                    float rad = Math.max(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                    c.drawRoundRect(notchInfo.bounds, rad, rad, blackPaint);
                }
            } else {
                c.drawRect(0, 0, getWidth(), AndroidUtilities.dp(BLACK_KING_BAR), blackPaint);
            }
            effectNode.endRecording();

            // Offset everything for black bar
            canvas.translate(0, -AndroidUtilities.dp(BLACK_KING_BAR));
            canvas.save();

            // Filter alpha + fade, then draw
            canvas.saveLayer(0, 0, getWidth() * gooScaleFactor, getHeight() * gooScaleFactor, null);
            canvas.saveLayer(0, 0, getWidth() * gooScaleFactor, getHeight() * gooScaleFactor, filter);
            canvas.scale(gooScaleFactor, gooScaleFactor);
            canvas.drawRenderNode(effectNode);
            canvas.restore();
            canvas.drawRect(0, 0, getWidth(), getHeight(), fadeToTop);
            canvas.restore();

            // Fade, draw blurred
            canvas.saveLayer(0, 0, getWidth() * blurScaleFactor, getHeight() * blurScaleFactor, null);
            if (blurIntensity != 0) {
                canvas.saveLayer(0, 0, getWidth() * blurScaleFactor, getHeight() * blurScaleFactor, filter);
                canvas.scale(blurScaleFactor, blurScaleFactor);
                canvas.drawRenderNode(blurNode);
                canvas.restore();
            } else {
                canvas.drawRenderNode(node);
            }
            canvas.drawRect(0, 0, getWidth(), getHeight(), fadeToBottom);
            canvas.restore();

            canvas.restore();
        }
    }

    private interface Impl {
        void setIntensity(float intensity);
        void setBlurIntensity(float intensity);
        void onSizeChanged(int w, int h);
        void draw(Drawer drawer, Canvas canvas);
        default void release() {}
    }

    private interface Drawer {
        void draw(Canvas canvas);
    }
}
