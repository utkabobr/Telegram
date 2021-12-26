package org.telegram.ui.Components.spoilers;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanReplacement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

public class SpoilerEffect extends Drawable {
    public final static int MAX_PARTICLES_PER_ENTITY = measureMaxParticlesCount();
    public final static int PARTICLES_PER_CHARACTER = measureParticlesPerCharacter();
    private final static float VERTICAL_PADDING_DP = 2.5f;
    private final static int RAND_REPEAT = 14;
    private final static float KEYPOINT_DELTA = 5f;
    private final static int FPS = 30;
    private final static int renderDelayMs = 1000 / FPS + 1;

    private Stack<Particle> particlesPool = new Stack<>();
    private int maxParticles;
    private float[] particlePoints = new float[MAX_PARTICLES_PER_ENTITY * 2];
    private float[] particleRands = new float[RAND_REPEAT];

    private static Path tempPath = new Path();

    private RectF visibleRect;

    private Paint particlePaint;

    private ArrayList<Particle> particles = new ArrayList<>();
    private View mParent;

    private long lastDrawTime;

    private float rippleX, rippleY;
    private float rippleMaxRadius;
    private float rippleProgress = -1;
    private boolean reverseAnimator;
    private boolean shouldInvalidateColor;
    private Runnable onRippleEndCallback;
    private ValueAnimator rippleAnimator;

    private List<RectF> spaces = new ArrayList<>();
    private List<Long> keyPoints;
    private int mAlpha = 0xFF;

    private TimeInterpolator rippleInterpolator = input -> input;

    private boolean invalidateParent;
    private boolean suppressUpdates;

    private static int measureParticlesPerCharacter() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return 15;
            default:
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return 25;
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return 30;
        }
    }

    private static int measureMaxParticlesCount() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return 400;
            default:
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return 700;
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return 1000;
        }
    }

    public SpoilerEffect() {
        particlePaint = new Paint();
        particlePaint.setStrokeWidth(AndroidUtilities.dp(1.1f));
        particlePaint.setStyle(Paint.Style.STROKE);
        particlePaint.setStrokeCap(Paint.Cap.ROUND);

        setColor(Color.TRANSPARENT);
    }

    /**
     * Sets if we should suppress updates or not
     */
    public void setSuppressUpdates(boolean suppressUpdates) {
        this.suppressUpdates = suppressUpdates;
        invalidateSelf();
    }

    /**
     * Sets if we should invalidate parent instead
     */
    public void setInvalidateParent(boolean invalidateParent) {
        this.invalidateParent = invalidateParent;
    }

    /**
     * Updates max particles count
     */
    public void updateMaxParticles() {
        setMaxParticlesCount(MathUtils.clamp((getBounds().width() / AndroidUtilities.dp(6)) * PARTICLES_PER_CHARACTER, 50, MAX_PARTICLES_PER_ENTITY));
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
        reverseAnimator = reverse;

        if (rippleAnimator != null)
            rippleAnimator.cancel();
        int startAlpha = reverseAnimator ? 0xFF : particlePaint.getAlpha();
        rippleAnimator = ValueAnimator.ofFloat(rippleProgress, reverse ? 0 : 1).setDuration((long) MathUtils.clamp(rippleMaxRadius * 0.3f, 250, 550));
        rippleAnimator.setInterpolator(rippleInterpolator);
        rippleAnimator.addUpdateListener(animation -> {
            rippleProgress = (float) animation.getAnimatedValue();
            setAlpha((int) (startAlpha * (1f - rippleProgress)));
            shouldInvalidateColor = true;
            invalidateSelf();
        });
        rippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Iterator<Particle> it = particles.iterator();
                while (it.hasNext()) {
                    Particle p = it.next();
                    if (particlesPool.size() < maxParticles)
                        particlesPool.push(p);
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
     * @return Current ripple progress
     */
    public float getRippleProgress() {
        return rippleProgress;
    }

    /**
     * @return If we should invalidate color
     */
    public boolean shouldInvalidateColor() {
        boolean b = shouldInvalidateColor;
        shouldInvalidateColor = false;
        return b;
    }

    /**
     * Sets new ripple progress
     */
    public void setRippleProgress(float rippleProgress) {
        this.rippleProgress = rippleProgress;
        if (rippleProgress == -1 && rippleAnimator != null)
            rippleAnimator.cancel();
        shouldInvalidateColor = true;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            if (!getBounds().contains((int)p.x, (int)p.y))
                it.remove();
            if (particlesPool.size() < maxParticles)
                particlesPool.push(p);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        long curTime = System.currentTimeMillis();
        int dt = (int) Math.min(curTime - lastDrawTime, renderDelayMs);
        float rr = rippleProgress * rippleMaxRadius;
        boolean hasAnimator = rippleProgress != -1;
        if (dt >= renderDelayMs) {
            lastDrawTime = curTime;

            int left = getBounds().left, top = getBounds().top, right = getBounds().right, bottom = getBounds().bottom;
            if (!suppressUpdates) {
                Iterator<Particle> it = particles.iterator();
                while (it.hasNext()) {
                    Particle particle = it.next();
                    if (visibleRect != null && !visibleRect.contains(particle.x, particle.y))
                        continue;

                    particle.currentTime = Math.min(particle.currentTime + dt, particle.lifeTime);
                    if (particle.currentTime >= particle.lifeTime || isOutOfBounds(left, top, right, bottom, particle.x, particle.y) ||
                            (hasAnimator && Math.pow(particle.x - rippleX, 2) + Math.pow(particle.y - rippleY, 2) <= Math.pow(rr, 2))) {
                        if (particlesPool.size() < maxParticles) {
                            particlesPool.push(particle);
                        }
                        it.remove();
                        continue;
                    }

                    if (hasAnimator) {
                        float adt = dt / 300f;

                        particle.vecX += ((particle.x - rippleX) / getBounds().width()) * adt;
                        particle.vecY += ((particle.y - rippleY) / getBounds().height()) * adt;
                    }
                    float hdt = particle.velocity * dt / 500f;
                    particle.x += particle.vecX * hdt;
                    particle.y += particle.vecY * hdt;
                }
            }

            if ((!hasAnimator || reverseAnimator) && particles.size() < maxParticles) {
                int np = maxParticles - particles.size();
                Arrays.fill(particleRands, -1);
                for (int i = 0; i < np; i++) {
                    float rf = particleRands[i % RAND_REPEAT];
                    if (rf == -1) {
                        particleRands[i % RAND_REPEAT] = rf = Utilities.fastRandom.nextFloat();
                    }

                    Particle newParticle = !particlesPool.isEmpty() ? particlesPool.pop() : new Particle();
                    int attempts = 0;
                    do {
                        generateRandomLocation(newParticle, i);
                        attempts++;
                    } while (isOutOfBounds(left, top, right, bottom, newParticle.x, newParticle.y) && attempts < 4);

                    double angleRad = rf * Math.PI * 2 - Math.PI;
                    float vx = (float) Math.cos(angleRad);
                    float vy = (float) Math.sin(angleRad);

                    newParticle.vecX = vx;
                    newParticle.vecY = vy;

                    newParticle.currentTime = 0;

                    newParticle.lifeTime = 1000 + Utilities.fastRandom.nextInt(2000); // [1000;3000]
                    newParticle.velocity = 4 + rf * 6;
                    particles.add(newParticle);
                }
            }
        }

        int renderCount = 0;
        for (int i = 0; i < particles.size(); i++) {
            Particle p = particles.get(i);

            if (visibleRect != null && !visibleRect.contains(p.x, p.y))
                continue;

            particlePoints[i * 2] = p.x;
            particlePoints[i * 2 + 1] = p.y;
            renderCount++;
        }
        canvas.drawPoints(particlePoints, 0, renderCount, particlePaint);

        invalidateSelf();
    }

    /**
     * Updates visible bounds to update particles
     */
    public void setVisibleBounds(float left, float top, float right, float bottom) {
        if (visibleRect == null)
            visibleRect = new RectF();
        visibleRect.left = left;
        visibleRect.top = top;
        visibleRect.right = right;
        visibleRect.bottom = bottom;
        invalidateSelf();
    }

    private boolean isOutOfBounds(int left, int top, int right, int bottom, float x, float y) {
        if (x < left || x > right || y < top + AndroidUtilities.dp(VERTICAL_PADDING_DP) ||
                y > bottom - AndroidUtilities.dp(VERTICAL_PADDING_DP))
            return true;

        for (RectF r : spaces) {
            if (r.contains(x, y))
                return true;
        }
        return false;
    }

    private void generateRandomLocation(Particle newParticle, int i) {
        float rf = particleRands[i % RAND_REPEAT];
        if (keyPoints != null && !keyPoints.isEmpty()) {
            long kp = keyPoints.get(Utilities.fastRandom.nextInt(keyPoints.size()));
            newParticle.keyX = getBounds().left + (kp >> 16);
            newParticle.keyY = getBounds().top + (kp & 0xFFFF);
            newParticle.x = newParticle.keyX + rf * AndroidUtilities.dp(KEYPOINT_DELTA) - AndroidUtilities.dp(KEYPOINT_DELTA / 2f);
            newParticle.y = newParticle.keyY + rf * AndroidUtilities.dp(KEYPOINT_DELTA) - AndroidUtilities.dp(KEYPOINT_DELTA / 2f);
        } else {
            newParticle.keyX = -1;
            newParticle.keyY = -1;
            newParticle.x = getBounds().left + rf * getBounds().width();
            newParticle.y = getBounds().top + rf * getBounds().height();
        }
    }

    @Override
    public void invalidateSelf() {
        super.invalidateSelf();

        if (mParent != null) {
            View v = mParent;
            if (v.getParent() != null && invalidateParent) {
                ((View) v.getParent()).invalidate();
            } else {
                v.invalidate();
            }
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
        particlePaint.setAlpha(mAlpha = alpha);
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
        if (particlePaint.getColor() != color) {
            particlePaint.setColor(ColorUtils.setAlphaComponent(color, mAlpha));
        }
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

        Bitmap measureBitmap = Bitmap.createBitmap(Math.round(w), Math.round(h), Bitmap.Config.ARGB_4444); // We can use 4444 as we don't need accuracy here
        Canvas measureCanvas = new Canvas(measureBitmap);
        textLayout.draw(measureCanvas);

        int[] pixels = new int[measureBitmap.getWidth() * measureBitmap.getHeight()];
        measureBitmap.getPixels(pixels, 0, measureBitmap.getWidth(), 0, 0, w, h);

        int sX = -1;
        ArrayList<Long> keyPoints = new ArrayList<>(pixels.length);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int clr = pixels[y * measureBitmap.getWidth() + x];
                if (Color.alpha(clr) >= 0x80) {
                    if (sX == -1)
                        sX = x;
                    keyPoints.add(((long) (x - sX) << 16) + y);
                }
            }
        }
        keyPoints.trimToSize();
        measureBitmap.recycle();
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
        while (particlesPool.size() + particles.size() < maxParticles) {
            particlesPool.push(new Particle());
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
        for (int line = 0; line < textLayout.getLineCount(); line++) {
            float l = textLayout.getLineLeft(line), t = textLayout.getLineTop(line), r = textLayout.getLineRight(line), b = textLayout.getLineBottom(line);
            int start = textLayout.getLineStart(line), end = textLayout.getLineEnd(line);

            for (TextStyleSpan span : spannable.getSpans(start, end, TextStyleSpan.class)) {
                if (span.isSpoiler()) {
                    int ss = spannable.getSpanStart(span), se = spannable.getSpanEnd(span);
                    int realStart = Math.max(start, ss), realEnd = Math.min(end, se);

                    int len = realEnd - realStart;
                    if (len == 0) continue;
                    addSpoilersInternal(v, spannable, textLayout, start, end, l, t, r, b, realStart, realEnd, spoilersPool, spoilers);
                }
            }
        }
        if (v instanceof TextView && spoilersPool != null)
            spoilersPool.clear();
    }

    @SuppressLint("WrongConstant")
    private static void addSpoilersInternal(View v, Spannable spannable, Layout textLayout, int lineStart, int lineEnd, float ll, float lt, float lr, float lb, int realStart, int realEnd, Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers) {
        SpannableStringBuilder vSpan = SpannableStringBuilder.valueOf(AndroidUtilities.replaceNewLines(new SpannableStringBuilder(spannable, realStart, realEnd)));
        for (TextStyleSpan styleSpan : vSpan.getSpans(0, vSpan.length(), TextStyleSpan.class))
            vSpan.removeSpan(styleSpan);
        for (URLSpanReplacement urlSpan : vSpan.getSpans(0, vSpan.length(), URLSpanReplacement.class))
            vSpan.removeSpan(urlSpan);
        int tLen = vSpan.toString().trim().length();
        if (tLen == 0) return;
        int width = textLayout.getEllipsizedWidth() > 0 ? textLayout.getEllipsizedWidth() : textLayout.getWidth();
        StaticLayout newLayout;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            newLayout = StaticLayout.Builder.obtain(vSpan, 0, vSpan.length(), textLayout.getPaint(), width)
                    .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                    .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(textLayout.getSpacingAdd(), textLayout.getSpacingMultiplier())
                    .build();
        } else newLayout = new StaticLayout(vSpan, textLayout.getPaint(), width, Layout.Alignment.ALIGN_NORMAL, textLayout.getSpacingMultiplier(), textLayout.getSpacingAdd(), false);
        boolean rtlInNonRTL = (LocaleController.isRTLCharacter(vSpan.charAt(0)) || LocaleController.isRTLCharacter(vSpan.charAt(vSpan.length() - 1))) && !LocaleController.isRTL;
        SpoilerEffect spoilerEffect = spoilersPool == null || spoilersPool.isEmpty() ? new SpoilerEffect() : spoilersPool.remove(0);
        spoilerEffect.setRippleProgress(-1);
        float ps = realStart == lineStart ? ll : textLayout.getPrimaryHorizontal(realStart),
                pe = realEnd == lineEnd || rtlInNonRTL && realEnd == lineEnd - 1 && spannable.charAt(lineEnd - 1) == '\u2026' ? lr : textLayout.getPrimaryHorizontal(realEnd);
        spoilerEffect.setBounds((int)Math.min(ps, pe), (int)lt, (int)Math.max(ps, pe), (int)lb);
        spoilerEffect.setColor(textLayout.getPaint().getColor());
        spoilerEffect.setRippleInterpolator(Easings.easeInQuad);
        spoilerEffect.setKeyPoints(SpoilerEffect.measureKeyPoints(newLayout));
        spoilerEffect.updateMaxParticles();
        if (v != null)
            spoilerEffect.setParentView(v);
        spoilerEffect.spaces.clear();
        for (int i = 0; i < vSpan.length(); i++) {
            if (vSpan.charAt(i) == ' ') {
                RectF r = new RectF();
                int off = realStart + i;
                int line = textLayout.getLineForOffset(off);
                r.top = textLayout.getLineTop(line);
                r.bottom = textLayout.getLineBottom(line);
                float lh = textLayout.getPrimaryHorizontal(off), rh = textLayout.getPrimaryHorizontal(off + 1);
                r.left = (int) Math.min(lh, rh); // RTL
                r.right = (int) Math.max(lh, rh);
                if (Math.abs(lh - rh) <= AndroidUtilities.dp(20))
                    spoilerEffect.spaces.add(r);
            }
        }
        spoilers.add(spoilerEffect);
    }

    /**
     * Clips out spoilers from canvas
     */
    public static void clipOutCanvas(Canvas canvas, List<SpoilerEffect> spoilers) {
        tempPath.rewind();
        for (SpoilerEffect eff : spoilers) {
            Rect b = eff.getBounds();
            tempPath.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
        }
        canvas.clipPath(tempPath, Region.Op.DIFFERENCE);
    }

    /**
     * Optimized version of text layout double-render
     * @param v View to use as a parent view
     * @param invalidateSpoilersParent Set to invalidate parent or not
     * @param spoilersColor Spoilers' color
     * @param verticalOffset Additional vertical offset
     * @param patchedLayoutRef Patched layout reference
     * @param textLayout Layout to render
     * @param spoilers Spoilers list to render
     * @param canvas Canvas to render
     */
    @SuppressLint("WrongConstant")
    @MainThread
    public static void renderWithRipple(View v, boolean invalidateSpoilersParent, int spoilersColor, int verticalOffset, AtomicReference<Layout> patchedLayoutRef, Layout textLayout, List<SpoilerEffect> spoilers, Canvas canvas) {
        Layout pl = patchedLayoutRef.get();
        if (pl == null || !textLayout.getText().toString().equals(pl.getText().toString()) || textLayout.getWidth() != pl.getWidth() || textLayout.getHeight() != pl.getHeight()) {
            SpannableStringBuilder sb = new SpannableStringBuilder(textLayout.getText());
            Spannable sp = (Spannable) textLayout.getText();
            for (TextStyleSpan ss : sp.getSpans(0, sp.length(), TextStyleSpan.class)) {
                if (ss.isSpoiler()) {
                    int start = sp.getSpanStart(ss), end = sp.getSpanEnd(ss);
                    for (Emoji.EmojiSpan e : sp.getSpans(start, end, Emoji.EmojiSpan.class)) {
                        sb.setSpan(new ReplacementSpan() {
                            @Override
                            public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                                return e.getSize(paint, text, start, end, fm);
                            }

                            @Override
                            public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {}
                        }, start, end, sp.getSpanFlags(ss));
                        sb.removeSpan(e);
                    }

                    sb.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), sp.getSpanStart(ss), sp.getSpanEnd(ss), sp.getSpanFlags(ss));
                    sb.removeSpan(ss);
                }
            }

            Layout layout;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                layout = StaticLayout.Builder.obtain(sb, 0, sb.length(), textLayout.getPaint(), textLayout.getWidth())
                        .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(textLayout.getSpacingAdd(), textLayout.getSpacingMultiplier())
                        .build();
            } else layout = new StaticLayout(sb, textLayout.getPaint(), textLayout.getWidth(), textLayout.getAlignment(), textLayout.getSpacingMultiplier(), textLayout.getSpacingAdd(), false);
            patchedLayoutRef.set(pl = layout);
        }

        if (!spoilers.isEmpty()) {
            canvas.save();
            canvas.translate(0, verticalOffset);
            pl.draw(canvas);
            canvas.restore();
        } else {
            textLayout.draw(canvas);
        }

        tempPath.rewind();
        for (SpoilerEffect eff : spoilers) {
            Rect b = eff.getBounds();
            tempPath.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
        }
        if (!spoilers.isEmpty() && spoilers.get(0).rippleProgress != -1) {
            canvas.save();
            canvas.clipPath(tempPath);
            tempPath.rewind();
            if (!spoilers.isEmpty())
                spoilers.get(0).getRipplePath(tempPath);
            canvas.clipPath(tempPath);
            canvas.translate(0, -v.getPaddingTop());
            textLayout.draw(canvas);
            canvas.restore();
        }

        canvas.save();
        canvas.translate(0, -v.getPaddingTop());
        for (SpoilerEffect eff : spoilers) {
            eff.setInvalidateParent(invalidateSpoilersParent);
            if (eff.getParentView() != v) eff.setParentView(v);
            if (eff.shouldInvalidateColor())
                eff.setColor(ColorUtils.blendARGB(spoilersColor, Theme.chat_msgTextPaint.getColor(), Math.max(0, eff.getRippleProgress())));
            else eff.setColor(spoilersColor);
            eff.draw(canvas);
        }
        canvas.restore();
    }

    private static class Particle {
        private float x, y;
        private float vecX, vecY;
        private float keyX = -1, keyY = -1;
        private float velocity;
        private float lifeTime, currentTime;
    }
}
