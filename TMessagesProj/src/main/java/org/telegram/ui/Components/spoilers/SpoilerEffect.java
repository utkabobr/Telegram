package org.telegram.ui.Components.spoilers;

import android.animation.TimeInterpolator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.TextStyleSpan;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

public class SpoilerEffect extends Drawable {
    private int maxParticles, maxParticlesPool, initParticlesPool, newParticles;

    private final static int KEYPOINT_DELTA = 4;

    private static Bitmap measureBitmap;
    private static Canvas measureCanvas;

    private static Rect tempRect = new Rect();

    private Paint particlePaint;

    private ArrayList<Particle> particles = new ArrayList<>();
    private Stack<Particle> freeParticles = new Stack<>();
    private float mAlpha = 1f;
    private WeakReference<View> mParent;

    private long lastDrawTime;

    private float rippleX, rippleY;
    private float rippleMaxRadius;
    private float rippleProgress = -1;
    private int rippleMaxDelta;
    private boolean isReverseRipple;
    private Runnable onRippleEndCallback;

    private List<Long> keyPoints;

    private TimeInterpolator rippleInterpolator = input -> input;

    public SpoilerEffect() {
        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStrokeWidth(AndroidUtilities.dp(1f));
        particlePaint.setStrokeCap(Paint.Cap.ROUND);
        particlePaint.setStyle(Paint.Style.STROKE);

        rippleMaxDelta = AndroidUtilities.dp(24);

        setColor(Theme.getColor(Theme.key_actionBarDefaultTitle) & 0xffe6e6e6);

        updateMaxParticles(30);
        for (int i = 0; i < initParticlesPool; i++) {
            freeParticles.push(new Particle());
        }
    }

    /**
     * Updates max particles count
     * @param charsCount Characters for this spoiler
     */
    public void updateMaxParticles(int charsCount) {
        maxParticles = Math.min(Math.max(100, charsCount * 30), 600);
        maxParticlesPool = maxParticles / 2;
        initParticlesPool = maxParticlesPool / 2;
        newParticles = charsCount > 15 ? 80 : 20;
    }

    /**
     * Sets callback to be run after ripple animation ends
     */
    public void setOnRippleEndCallback(@Nullable Runnable onRippleEndCallback) {
        this.onRippleEndCallback = onRippleEndCallback;
    }

    /**
     * Starts ripple
     * @param rX Ripple center x
     * @param rY Ripple center y
     * @param radMax Max ripple radius
     */
    public void startRipple(float rX, float rY, float radMax) {
        startRipple(rX, rY, radMax, false);
    }

    /**
     * Starts ripple
     * @param rX Ripple center x
     * @param rY Ripple center y
     * @param radMax Max ripple radius
     * @param reverse If we should start reverse ripple
     */
    public void startRipple(float rX, float rY, float radMax, boolean reverse) {
        rippleX = rX;
        rippleY = rY;
        rippleMaxRadius = radMax;
        isReverseRipple = reverse;
        rippleProgress = reverse ? 1 : 0;
        invalidateSelf();
    }

    /**
     * Sets new ripple interpolator
     * @param rippleInterpolator New interpolator
     */
    public void setRippleInterpolator(@NonNull TimeInterpolator rippleInterpolator) {
        this.rippleInterpolator = rippleInterpolator;
    }

    /**
     * Sets new keypoints
     * @param keyPoints New keypoints
     */
    public void setKeyPoints(List<Long> keyPoints) {
        this.keyPoints = keyPoints;
        invalidateSelf();
    }

    /**
     * Gets ripple path
     */
    public void getRipplePath(Path path) {
        path.addCircle(rippleX, rippleY, rippleMaxRadius * rippleInterpolator.getInterpolation(Math.max(0, rippleProgress)), Path.Direction.CW);
    }

    /**
     * Sets new ripple progress
     */
    public void setRippleProgress(float rippleProgress) {
        this.rippleProgress = rippleProgress;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        long curTime = System.currentTimeMillis();
        int dt = (int) Math.min(curTime - lastDrawTime, 17);
        lastDrawTime = curTime;

        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle particle = it.next();
            if (particle.currentTime >= particle.lifeTime) {
                if (freeParticles.size() < maxParticlesPool) {
                    freeParticles.push(particle);
                }
                it.remove();
                continue;
            }
            particle.x += particle.vx * particle.velocity * dt / 500f;
            particle.y += particle.vy * particle.velocity * dt / 500f;
            particle.currentTime = Math.min(particle.currentTime + dt, particle.lifeTime);

            if (rippleProgress != -1) {
                float rr = rippleMaxRadius * rippleInterpolator.getInterpolation(rippleProgress);
                if (Math.pow(particle.x - rippleX, 2) + Math.pow(particle.y - rippleY, 2) <= Math.pow(rr, 2)) {
                    particle.alpha = 0;
                } else {
                    float rd = (float) Math.min(rippleMaxDelta, Math.sqrt(Math.pow(particle.x - rippleX, 2) + Math.pow(particle.y - rippleY, 2)) - rr);
                    particle.alpha = rd / rippleMaxDelta;

                    particle.vx += dt / 90f * (particle.x > rippleX ? 1 : -1);
                    particle.vy += dt / 90f * (particle.y > rippleY ? 1 : -1);
                }
            } else {
                particle.alpha = 1f - (Math.max(0.75f, particle.currentTime / particle.lifeTime) - 0.75f) / 0.25f;
            }

            particle.draw(canvas);
        }
        if (rippleProgress != -1) {
            rippleProgress = MathUtils.clamp(rippleProgress + (dt / 500f) * (isReverseRipple ? -1 : 1), 0, 1);

            if (rippleProgress == (isReverseRipple ? 0 : 1)) {
                it = particles.iterator();
                while (it.hasNext()) {
                    Particle p = it.next();
                    if (freeParticles.size() < maxParticlesPool)
                        freeParticles.push(p);
                    it.remove();
                }

                if (onRippleEndCallback != null) {
                    onRippleEndCallback.run();
                    onRippleEndCallback = null;
                }
            }
        }

        if (particles.size() + newParticles < maxParticles) {
            for (int a = 0; a < newParticles; a++) {
                float cx, cy;
                if (keyPoints != null && !keyPoints.isEmpty()) {
                    long kp = keyPoints.get(Utilities.random.nextInt(keyPoints.size()));
                    cx = getBounds().left + (kp >> 16) + Utilities.random.nextFloat() * AndroidUtilities.dp(KEYPOINT_DELTA) - AndroidUtilities.dp(KEYPOINT_DELTA / 2f);
                    cy = getBounds().top + (kp & 0xFFFF) + Utilities.random.nextFloat() * AndroidUtilities.dp(KEYPOINT_DELTA) - AndroidUtilities.dp(KEYPOINT_DELTA / 2f);
                } else {
                    cx = getBounds().left + Utilities.random.nextFloat() * getBounds().width();
                    cy = getBounds().top + Utilities.random.nextFloat() * getBounds().height();
                }

                double angleRad = Utilities.random.nextFloat() * Math.PI * 2 - Math.PI;
                float vx = (float) Math.cos(angleRad);
                float vy = (float) Math.sin(angleRad);

                Particle newParticle;
                if (!freeParticles.isEmpty()) {
                    newParticle = freeParticles.pop();
                } else {
                    newParticle = new Particle();
                }
                newParticle.x = cx;
                newParticle.y = cy;

                newParticle.vx = vx;
                newParticle.vy = vy;

                newParticle.alpha = 1f;
                newParticle.currentTime = 0;

                newParticle.scale = 0.5f + Utilities.random.nextFloat() * 0.5f; // [0.5;1]

                newParticle.lifeTime = 500 + Utilities.random.nextInt(500); // [500;1000]
                newParticle.velocity = 3 + Utilities.random.nextFloat() * 1.5f;
                particles.add(newParticle);
            }
        }

        invalidateSelf();
    }

    @Override
    public void invalidateSelf() {
        super.invalidateSelf();

        if (mParent != null) {
            View v = mParent.get();
            if (v != null)
                v.invalidate();
            else mParent = null;
        }
    }

    /**
     * Attaches to the parent view
     * @param parentView Parent view
     */
    public void setParentView(View parentView) {
        this.mParent = new WeakReference<>(parentView);
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = (float) alpha / 0xFF;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        particlePaint.setColorFilter(colorFilter);
    }

    /**
     * Sets particles color
     * @param color New color
     */
    public void setColor(int color) {
        particlePaint.setColor(color);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    /**
     * @param textLayout Text layout to measure
     * @return Measured key points
     */
    public static synchronized List<Long> measureKeyPoints(Layout textLayout) {
        int w = textLayout.getWidth();
        int h = textLayout.getHeight();

        if (w == 0 || h == 0)
            return Collections.emptyList();

        if (measureBitmap == null || measureBitmap.isRecycled() || measureBitmap.getWidth() < w || measureBitmap.getHeight() < h) { // Multiple reallocations without the need would be bad for performance
            if (measureBitmap != null && !measureBitmap.isRecycled())
                measureBitmap.recycle();
            measureBitmap = Bitmap.createBitmap(Math.round(w), Math.round(h), Bitmap.Config.ARGB_8888);
            measureCanvas = new Canvas(measureBitmap);
        }
        measureBitmap.eraseColor(Color.TRANSPARENT);
        measureCanvas.save();
        textLayout.draw(measureCanvas);
        measureCanvas.restore();

        int[] pixels = new int[measureBitmap.getWidth() * measureBitmap.getHeight()];
        measureBitmap.getPixels(pixels, 0, measureBitmap.getWidth(), 0, 0, w, h);

        ArrayList<Long> keyPoints = new ArrayList<>(pixels.length);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int clr = pixels[y * measureBitmap.getWidth() + x];
                if (Color.alpha(clr) >= 0x80) {
                    keyPoints.add(((long) x << 16) + y);
                }
            }
        }
        keyPoints.trimToSize();
        return keyPoints;
    }

    /**
     * Alias for it's big bro
     * @param tv Text view to use as a parent view
     * @param spoilersPool Cached spoilers pool
     * @param spoilers Spoilers list to populate
     */
    public static void addSpoilers(TextView tv, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers) {
        addSpoilers(tv, tv.getLayout(), spoilersPool, spoilers);
    }

    /**
     * Parses spoilers from spannable
     * @param v View to use as a parent view
     * @param textLayout Text layout to measure
     * @param spoilersPool Cached spoilers pool, could be null, but highly recommended
     * @param spoilers Spoilers list to populate
     */
    public static void addSpoilers(View v, Layout textLayout, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers) {
        Spannable spannable = (Spannable) textLayout.getText();
        TextPaint textPaint = textLayout.getPaint();
        TextStyleSpan[] spans = spannable.getSpans(0, spannable.length(), TextStyleSpan.class);
        for (int line = 0; line < textLayout.getLineCount(); line++) {
            tempRect.set((int) textLayout.getLineLeft(line), textLayout.getLineTop(line), (int) textLayout.getLineRight(line), textLayout.getLineBottom(line));
            int start = textLayout.getLineStart(line), end = textLayout.getLineEnd(line);

            for (TextStyleSpan span : spans) {
                if (span.isSpoiler()) {
                    int ss = spannable.getSpanStart(span), se = spannable.getSpanEnd(span);

                    if (start <= se && end >= ss) {
                        int realStart = Math.max(start, ss), realEnd = Math.min(end, se);

                        int len = realEnd - realStart;
                        if (len == 0) continue;

                        int offWidth = 0;
                        if (realStart != start) {
                            offWidth = (int) textPaint.measureText(textLayout.getText(), start, realStart);
                        }

                        SpannableStringBuilder vSpan = new SpannableStringBuilder(textLayout.getText(), realStart, realEnd);
                        vSpan.removeSpan(span);
                        StaticLayout newLayout = new StaticLayout(vSpan, textPaint, Math.max(v.getWidth(), textLayout.getWidth()), LocaleController.isRTL ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
                        SpoilerEffect spoilerEffect = spoilersPool == null || spoilersPool.isEmpty() ? new SpoilerEffect() : spoilersPool.remove(0);
                        spoilerEffect.setRippleProgress(-1);
                        spoilerEffect.setBounds(tempRect.left + offWidth, tempRect.top, (int) (tempRect.left + offWidth + textPaint.measureText(vSpan, 0, vSpan.length())), tempRect.bottom);
                        spoilerEffect.setColor(textPaint.getColor());
                        spoilerEffect.setRippleInterpolator(CubicBezierInterpolator.EASE_BOTH);
                        spoilerEffect.setKeyPoints(SpoilerEffect.measureKeyPoints(newLayout));
                        spoilerEffect.updateMaxParticles(textLayout.getText().subSequence(realStart, realEnd).toString().trim().length()); // To filter out spaces
                        spoilerEffect.setParentView(v);
                        spoilerEffect.isReverseRipple = false;
                        spoilers.add(spoilerEffect);
                    }
                }
            }
        }
    }

    private class Particle {
        private float x, y;
        private float vx, vy;
        private float velocity;
        private float alpha;
        private float lifeTime, currentTime;
        private float scale;

        private void draw(Canvas canvas) {
            particlePaint.setStrokeWidth(AndroidUtilities.dp(1.5f) * scale);
            particlePaint.setAlpha((int) (0xFF * alpha * mAlpha));
            canvas.drawPoint(x, y, particlePaint);
        }
    }
}
