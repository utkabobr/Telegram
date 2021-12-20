package org.telegram.ui.Components.spoilers;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
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
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.TextStyleSpan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

public class SpoilerEffect extends Drawable {
    public final static int MAX_PARTICLES_PER_MESSAGE = 600;
    public final static int MIN_AVG_PARTICLES = 60, AVG_STEP = 15;
    public final static int MIN_PARTICLES_PER_TICK_OVER = 5, MAX_PARTICLES_PER_TICK_OVER = 25;

    private int maxParticles, maxParticlesPool, initParticlesPool, newParticles;
    private float dropOutPercent = 0.3f;

    private final static int KEYPOINT_DELTA = 4;

    private static Bitmap measureBitmap;
    private static Canvas measureCanvas;

    private static Rect tempRect = new Rect();

    private Paint particlePaint;

    private ArrayList<Particle> particles = new ArrayList<>();
    private Stack<Particle> freeParticles = new Stack<>();
    private float mAlpha = 1f;
    private View mParent;

    private long lastDrawTime;

    private float rippleX, rippleY;
    private float rippleMaxRadius;
    private float rippleProgress = -1;
    private int rippleMaxDelta;
    private Runnable onRippleEndCallback;
    private ValueAnimator rippleAnimator;

    private List<Long> keyPoints;
    private List<Integer> dropOutIndexes = new ArrayList<>();

    private TimeInterpolator rippleInterpolator = input -> input;

    public SpoilerEffect() {
        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStrokeWidth(AndroidUtilities.dp(1f));
        particlePaint.setStrokeCap(Paint.Cap.ROUND);
        particlePaint.setStyle(Paint.Style.STROKE);

        rippleMaxDelta = AndroidUtilities.dp(24);

        setColor(Color.TRANSPARENT);

        updateMaxParticles(30);
        for (int i = 0; i < initParticlesPool; i++) {
            freeParticles.push(new Particle());
        }
    }

    /**
     * Sets dropout percent to optimize resources
     */
    public void setDropOutPercent(float dropOutPercent) {
        this.dropOutPercent = dropOutPercent;
    }

    /**
     * Updates max particles count
     * @param charsCount Characters for this spoiler
     */
    public void updateMaxParticles(int charsCount) {
        maxParticles = MathUtils.clamp(charsCount * 25, 30, 500);
        maxParticlesPool = maxParticles / 2;
        initParticlesPool = maxParticlesPool / 2;
        newParticles = maxParticles / 10;
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
        rippleProgress = reverse ? 1 : 0;

        if (!reverse) {
            for (Particle p : particles) {
                p.startX = p.x;
                p.startY = p.y;
            }
        }

        if (rippleAnimator != null)
            rippleAnimator.cancel();
        rippleAnimator = ValueAnimator.ofFloat(rippleProgress, reverse ? 0 : 1).setDuration((long) MathUtils.clamp(rippleMaxRadius * 0.5f, 300, 550));
        rippleAnimator.setInterpolator(rippleInterpolator);
        rippleAnimator.addUpdateListener(animation -> {
            rippleProgress = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        rippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Iterator<Particle> it = particles.iterator();
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

                rippleAnimator = null;
                invalidateSelf();
            }
        });
        rippleAnimator.start();

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
        path.addCircle(rippleX, rippleY, rippleMaxRadius * MathUtils.clamp(rippleProgress, 0, 1), Path.Direction.CW);
    }

    /**
     * Sets new ripple progress
     */
    public void setRippleProgress(float rippleProgress) {
        this.rippleProgress = rippleProgress;
        if (rippleProgress == -1 && rippleAnimator != null)
            rippleAnimator.cancel();
    }


    @Override
    public void draw(@NonNull Canvas canvas) {
        long curTime = System.currentTimeMillis();
        int dt = (int) Math.min(curTime - lastDrawTime, 17);
        lastDrawTime = curTime;

        dropOutIndexes.clear();
        for (int i = 0; i < dropOutPercent * particles.size(); i++) {
            int r = Utilities.random.nextInt(particles.size());
            while (dropOutIndexes.contains(r))
                r = Utilities.random.nextInt(particles.size());
            dropOutIndexes.add(r);
        }
        int i = 0;
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle particle = it.next();
            if (particle.currentTime >= particle.lifeTime) {
                if (freeParticles.size() < maxParticlesPool) {
                    freeParticles.push(particle);
                }
                it.remove();
                i++;
                continue;
            }
            if (rippleAnimator == null && dropOutIndexes.contains(i)) {
                particle.draw(canvas);
                i++;
                continue;
            }

            if (rippleAnimator != null) {
                particle.x = particle.startX + (particle.keyX - particle.startX) * rippleProgress;
                particle.y = particle.startY + (particle.keyY - particle.startY) * rippleProgress;
            } else {
                float hdt = particle.velocity * dt / 500f;
                particle.x += particle.vecX * hdt;
                particle.y += particle.vecY * hdt;
            }
            particle.currentTime = Math.min(particle.currentTime + dt, particle.lifeTime);

            if (rippleAnimator != null) {
                float rr = rippleMaxRadius * rippleProgress;
                if (Math.pow(particle.x - rippleX, 2) + Math.pow(particle.y - rippleY, 2) <= Math.pow(rr, 2)) {
                    particle.alpha = 0;
                } else {
                    float rd = (float) Math.min(rippleMaxDelta, Math.sqrt(Math.pow(particle.x - rippleX, 2) + Math.pow(particle.y - rippleY, 2)) - rr);
                    particle.alpha = rd / rippleMaxDelta;
                }
            } else {
                particle.alpha = 1f - (Math.max(0.75f, particle.currentTime / particle.lifeTime) - 0.75f) / 0.25f;
            }

            particle.draw(canvas);
            i++;
        }

        if (rippleAnimator == null && particles.size() + newParticles < maxParticles) {
            for (int a = 0; a < newParticles; a++) {
                Particle newParticle;
                if (!freeParticles.isEmpty()) {
                    newParticle = freeParticles.pop();
                } else {
                    newParticle = new Particle();
                }

                if (keyPoints != null && !keyPoints.isEmpty()) {
                    long kp = keyPoints.get(Utilities.random.nextInt(keyPoints.size()));
                    newParticle.keyX = getBounds().left + (kp >> 16);
                    newParticle.keyY = getBounds().top + (kp & 0xFFFF);
                    newParticle.x = newParticle.keyX + Utilities.random.nextFloat() * AndroidUtilities.dp(KEYPOINT_DELTA) - AndroidUtilities.dp(KEYPOINT_DELTA / 2f);
                    newParticle.y = newParticle.keyY + Utilities.random.nextFloat() * AndroidUtilities.dp(KEYPOINT_DELTA) - AndroidUtilities.dp(KEYPOINT_DELTA / 2f);
                } else {
                    newParticle.x = getBounds().left + Utilities.random.nextFloat() * getBounds().width();
                    newParticle.y = getBounds().top + Utilities.random.nextFloat() * getBounds().height();
                }

                double angleRad = Utilities.random.nextFloat() * Math.PI * 2 - Math.PI;
                float vx = (float) Math.cos(angleRad);
                float vy = (float) Math.sin(angleRad);

                newParticle.vecX = vx;
                newParticle.vecY = vy;

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
            View v = mParent;
            v.invalidate();
        }
    }

    /**
     * Attaches to the parent view
     * @param parentView Parent view
     */
    public void setParentView(View parentView) {
        this.mParent = parentView;
    }

    /**
     * @return Currently used parent view
     */
    public View getParentView() {
        return mParent;
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

    /**
     * @return If effect has color
     */
    public boolean hasColor() {
        return particlePaint.getColor() != Color.TRANSPARENT;
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
     * @return Max particles count
     */
    public int getMaxParticlesCount() {
        return maxParticles;
    }

    /**
     * Sets new max particles count
     */
    public void setMaxParticlesCount(int maxParticles) {
        this.maxParticles = maxParticles;
    }

    /**
     * Sets how many new particles to spawn per tick
     */
    public void setNewParticlesCountPerTick(int newParticles) {
        this.newParticles = newParticles;
    }

    /**
     * Optimizes spoilers for big messages
     * @param spoilers A list of spoilers to optimize
     */
    public static void optimizeSpoilers(List<SpoilerEffect> spoilers) {
        int partsTotal = 0;
        int partsCount = 0;
        for (SpoilerEffect eff : spoilers) {
            partsTotal += eff.getMaxParticlesCount();
            partsCount++;
        }

        int average = (int) (partsTotal / (float)partsCount);
        if (partsTotal > SpoilerEffect.MAX_PARTICLES_PER_MESSAGE) {
            while (average > SpoilerEffect.MIN_AVG_PARTICLES && average * partsCount > SpoilerEffect.MAX_PARTICLES_PER_MESSAGE) {
                average -= SpoilerEffect.AVG_STEP;
            }
            average = Math.max(SpoilerEffect.MIN_AVG_PARTICLES, average);

            for (SpoilerEffect eff : spoilers) {
                eff.setMaxParticlesCount(average);
                eff.setNewParticlesCountPerTick(MathUtils.clamp(average / 10, SpoilerEffect.MIN_PARTICLES_PER_TICK_OVER, SpoilerEffect.MAX_PARTICLES_PER_TICK_OVER));
                eff.setDropOutPercent(0.7f);
            }
        }
    }


    /**
     * Alias for it's big bro
     * @param tv Text view to use as a parent view
     * @param spoilersPool Cached spoilers pool
     * @param spoilers Spoilers list to populate
     */
    public static void addSpoilers(TextView tv, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers) {
        addSpoilers(tv, tv.getLayout(), (Spannable) tv.getText(), spoilersPool, spoilers);
    }

    /**
     * Alias for it's big bro
     * @param v View to use as a parent view
     * @param textLayout Text layout to measure
     * @param spoilersPool Cached spoilers pool, could be null, but highly recommended
     * @param spoilers Spoilers list to populate
     */
    public static void addSpoilers(@Nullable View v, Layout textLayout, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers) {
        addSpoilers(v, textLayout, (Spannable) textLayout.getText(), spoilersPool, spoilers);
    }

    /**
     * Parses spoilers from spannable
     * @param v View to use as a parent view
     * @param textLayout Text layout to measure
     * @param spannable Text to parse
     * @param spoilersPool Cached spoilers pool, could be null, but highly recommended
     * @param spoilers Spoilers list to populate
     */
    public static void addSpoilers(@Nullable View v, Layout textLayout, Spannable spannable, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers) {
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
                        int tLen = textLayout.getText().subSequence(realStart, realEnd).toString().trim().length();
                        if (tLen == 0)
                            continue;
                        StaticLayout newLayout = new StaticLayout(vSpan, textPaint, Math.max(v != null ? v.getWidth() : 0, textLayout.getWidth()), LocaleController.isRTL ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
                        SpoilerEffect spoilerEffect = spoilersPool == null || spoilersPool.isEmpty() ? new SpoilerEffect() : spoilersPool.remove(0);
                        spoilerEffect.setRippleProgress(-1);
                        spoilerEffect.setBounds(tempRect.left + offWidth, tempRect.top, (int) (tempRect.left + offWidth + textPaint.measureText(vSpan, 0, vSpan.length())), tempRect.bottom);
                        spoilerEffect.setColor(textPaint.getColor());
                        spoilerEffect.setRippleInterpolator(CubicBezierInterpolator.DEFAULT);
                        spoilerEffect.setKeyPoints(SpoilerEffect.measureKeyPoints(newLayout));
                        spoilerEffect.updateMaxParticles(tLen); // To filter out spaces
                        if (v != null)
                            spoilerEffect.setParentView(v);
                        spoilers.add(spoilerEffect);
                    }
                }
            }
        }
    }

    private class Particle {
        private float x, y;
        private float vecX, vecY;
        private float keyX, keyY;
        private float startX, startY;
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
