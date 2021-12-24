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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import com.google.android.exoplayer2.C;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.TextStyleSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

public class SpoilerEffect extends Drawable {
    private final static int FPS = 25, PARTICLE_COUNT_PER_TILE = 40, FRAME_VARIANTS = 4;
    private final static float TILE_SIZE = 21.5f, SIMULATION_SECONDS = 1.5f;
    private final static float KEY_DEVIATE_X = 20, KEY_DEVIATE_Y = 5;

    private static SparseArray<List<Bitmap>> framesMap = new SparseArray<>();
    private static int lastRenderedTileSize;

    private static Rect tempRect = new Rect();
    private static RectF tempRectF = new RectF();

    private static Path clipOutPath = new Path();

    private Paint particlePaint;
    private Paint bitmapPaint;
    private int bitmapColor;

    private View mParent;

    private long lastDrawTime;

    private float rippleX, rippleY;
    private float rippleMaxRadius;
    private float rippleProgress = -1;
    private boolean shouldInvalidateColor;
    private Runnable onRippleEndCallback;
    private ValueAnimator rippleAnimator;

    private TimeInterpolator rippleInterpolator = input -> input;

    private boolean invalidateParent;

    private Path path = new Path();
    private boolean reverseFrames;
    private int lastDt, frameNum;
    private SparseIntArray frameVariants = new SparseIntArray();

    static {
        for (int i = 0; i < FRAME_VARIANTS; i++) {
            framesMap.put(i, new ArrayList<>());
        }
    }

    public SpoilerEffect() {
        particlePaint = new Paint();
        particlePaint.setStrokeWidth(AndroidUtilities.dp(1f));
        particlePaint.setStrokeCap(Paint.Cap.ROUND);
        particlePaint.setStyle(Paint.Style.STROKE);

        bitmapPaint = new Paint();

        setColor(Color.TRANSPARENT);
        checkBitmaps();
    }

    /**
     * Checks if tile size changed
     */
    private void checkBitmaps() {
        int tSize = AndroidUtilities.dp(TILE_SIZE);
        if (lastRenderedTileSize != tSize) {
            TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(tSize);

            for (int k = 0; k < framesMap.size(); k++) {
                StaticLayout textLayout = new StaticLayout("=", textPaint, tSize, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
                int w = textLayout.getWidth();
                int h = textLayout.getHeight();

                Bitmap measureBitmap = Bitmap.createBitmap(Math.round(w), Math.round(h), Bitmap.Config.ARGB_4444);
                Canvas measureCanvas = new Canvas(measureBitmap);

                measureBitmap.eraseColor(Color.TRANSPARENT);
                measureCanvas.save();
                textLayout.draw(measureCanvas);
                measureCanvas.restore();

                int[] pixels = new int[measureBitmap.getWidth() * measureBitmap.getHeight()];
                measureBitmap.getPixels(pixels, 0, measureBitmap.getWidth(), 0, 0, w, h);

                int sX = -1;
                ArrayList<Integer> keysX = new ArrayList<>(pixels.length);
                ArrayList<Integer> keysY = new ArrayList<>(pixels.length);
                for (int x = 0; x < w; x++) {
                    for (int y = 0; y < h; y++) {
                        int clr = pixels[y * measureBitmap.getWidth() + x];
                        if (Color.alpha(clr) >= 0x80) {
                            if (sX == -1)
                                sX = x;
                            keysX.add(x - sX);
                            keysY.add(y);
                        }
                    }
                }
                keysX.trimToSize();
                keysY.trimToSize();

                measureBitmap.recycle();

                List<Bitmap> frames = framesMap.valueAt(k);
                for (Bitmap b : frames) b.recycle();
                frames.clear();

                List<Particle> particles = new ArrayList<>();
                for (int i = 0; i < PARTICLE_COUNT_PER_TILE; i++) {
                    Particle newParticle = new Particle();
                    int j = Utilities.random.nextInt(keysX.size());
                    newParticle.x = keysX.get(j) + Utilities.random.nextFloat() * AndroidUtilities.dp(KEY_DEVIATE_X) - AndroidUtilities.dp(KEY_DEVIATE_X / 2f);
                    newParticle.y = keysY.get(j) + Utilities.random.nextFloat() * AndroidUtilities.dp(KEY_DEVIATE_Y) - AndroidUtilities.dp(KEY_DEVIATE_Y / 2f);

                    double angleRad = Utilities.random.nextFloat() * Math.PI * 2 - Math.PI;
                    float vx = (float) Math.cos(angleRad);
                    float vy = (float) Math.sin(angleRad);

                    newParticle.vecX = vx;
                    newParticle.vecY = vy;

                    newParticle.startAlpha = newParticle.alpha = Utilities.random.nextFloat() * 0.7f + 0.3f;
                    newParticle.endAlpha = Utilities.random.nextFloat() * 0.7f + 0.3f;

                    newParticle.scale = 0.5f + Utilities.random.nextFloat() * 0.5f; // [0.5;1]

                    newParticle.velocity = 3 + Utilities.random.nextFloat() * 1.5f;
                    particles.add(newParticle);
                }

                int dt = 17;
                for (int i = 0; i < SIMULATION_SECONDS * FPS; i++) {
                    float progress = i / (SIMULATION_SECONDS * FPS);
                    Bitmap bm = Bitmap.createBitmap(tSize, tSize, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(bm);
                    for (Particle particle : particles) {
                        float hdt = particle.velocity * dt / 400f;
                        particle.x += particle.vecX * hdt;
                        particle.y += particle.vecY * hdt;
                        particle.alpha = particle.startAlpha + (particle.endAlpha - particle.startAlpha) * progress;

                        particle.draw(c);
                    }
                    frames.add(bm);
                }
                framesMap.put(k, frames);
            }
            lastRenderedTileSize = tSize;
        }
    }

    /**
     * Sets if we should invalidate parent instead
     */
    public void setInvalidateParent(boolean invalidateParent) {
        this.invalidateParent = invalidateParent;
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

        if (rippleAnimator != null)
            rippleAnimator.cancel();
        rippleAnimator = ValueAnimator.ofFloat(rippleProgress, reverse ? 0 : 1).setDuration((long) MathUtils.clamp(rippleMaxRadius * 0.5f, 300, 550));
        rippleAnimator.setInterpolator(rippleInterpolator);
        rippleAnimator.addUpdateListener(animation -> {
            rippleProgress = (float) animation.getAnimatedValue();
            shouldInvalidateColor = true;
            invalidateSelf();
        });
        rippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
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
    public void draw(@NonNull Canvas canvas) {
        long curTime = System.currentTimeMillis();
        int dt = (int) Math.min(curTime - lastDrawTime, 1000 / FPS + 1);
        lastDrawTime = curTime;

        lastDt = Math.min(lastDt + dt, 1000 / FPS + 1);

        if (lastDt == 1000 / FPS + 1) {
            frameNum++;
            lastDt = 0;
            if (frameNum >= SIMULATION_SECONDS * FPS) {
                reverseFrames = !reverseFrames;
                frameNum = 0;
            }
        }

        canvas.save();
        path.rewind();
        float rad = AndroidUtilities.dp(8);
        tempRectF.set(getBounds().left, getBounds().top, getBounds().right, getBounds().bottom);
        path.addRoundRect(tempRectF, rad, rad, Path.Direction.CW);
        canvas.clipPath(path);

        path.rewind();
        getRipplePath(path);
        canvas.clipPath(path, Region.Op.DIFFERENCE);

        float h = getBounds().height();
        int x = getBounds().left;
        int i = 0;
        canvas.translate(getBounds().left, getBounds().top);

        float pivot = lastRenderedTileSize / 2f;
        float sc = h > lastRenderedTileSize ? h / lastRenderedTileSize : 1f;
        float tStep = sc * lastRenderedTileSize - lastRenderedTileSize * 0.23f;
        int fn = reverseFrames ? (int) (SIMULATION_SECONDS * FPS - frameNum - 1) : frameNum;

        if (h < lastRenderedTileSize) {
            canvas.translate(0, (h - lastRenderedTileSize) / 2f);
        }

        while (x < getBounds().right) {
            int var = frameVariants.get(i, -1);
            if (var == -1) frameVariants.put(i, var = Utilities.random.nextInt(FRAME_VARIANTS));

            canvas.save();
            Bitmap bm = framesMap.get(var).get(fn);
            canvas.scale(sc, sc, pivot, pivot);
            canvas.drawBitmap(bm, 0, 0, bitmapPaint);
            canvas.restore();

            canvas.translate(tStep, 0);
            x += tStep;
            i++;
        }
        canvas.restore();

        invalidateSelf();
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
        bitmapPaint.setAlpha(alpha);
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
        if (bitmapColor != color) {
            bitmapPaint.setColorFilter(new PorterDuffColorFilter(bitmapColor = color, PorterDuff.Mode.SRC_IN));
            invalidateSelf();
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

                        SpannableStringBuilder vSpan = new SpannableStringBuilder(textLayout.getText(), realStart, realEnd);
                        vSpan.removeSpan(span);
                        int tLen = vSpan.toString().trim().length();
                        if (tLen == 0)
                            continue;
                        SpoilerEffect spoilerEffect = spoilersPool == null || spoilersPool.isEmpty() ? new SpoilerEffect() : spoilersPool.remove(0);
                        spoilerEffect.setRippleProgress(-1);
                        float ps = realStart == start ? tempRect.left : textLayout.getPrimaryHorizontal(realStart), pe = realEnd == end ? tempRect.right : textLayout.getPrimaryHorizontal(realEnd);
                        spoilerEffect.setBounds((int)Math.min(ps, pe), tempRect.top, (int)Math.max(ps, pe), tempRect.bottom);
                        spoilerEffect.setColor(textPaint.getColor());
                        spoilerEffect.setRippleInterpolator(CubicBezierInterpolator.DEFAULT);
                        if (v != null)
                            spoilerEffect.setParentView(v);
                        spoilers.add(spoilerEffect);
                    }
                }
            }
        }
    }

    /**
     * Clips out spoilers from canvas
     */
    public static void clipOutCanvas(Canvas canvas, List<SpoilerEffect> spoilers) {
        clipOutPath.rewind();
        for (SpoilerEffect eff : spoilers) {
            Rect b = eff.getBounds();
            clipOutPath.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
        }
        canvas.clipPath(clipOutPath, Region.Op.DIFFERENCE);
    }

    /**
     * Optimized version of text layout double-render
     * @param v View to use as a parent view
     * @param invalidateSpoilersParent Set to invalidate parent or not
     * @param spoilersColor Spoilers' color
     * @param canvas Canvas to render
     * @param patchedLayoutRef Patched layout reference
     * @param textLayout Layout to render
     * @param spoilers Spoilers list to render
     */
    @SuppressLint("WrongConstant")
    @MainThread
    public static void renderWithRipple(View v, boolean invalidateSpoilersParent, int spoilersColor, Canvas canvas, AtomicReference<Layout> patchedLayoutRef, Layout textLayout, List<SpoilerEffect> spoilers) {
        Layout pl = patchedLayoutRef.get();
        if (pl == null || !textLayout.getText().equals(pl.getText()) || textLayout.getWidth() != pl.getWidth() || textLayout.getHeight() != pl.getHeight()) {
            SpannableStringBuilder sb = SpannableStringBuilder.valueOf(textLayout.getText());
            Spannable sp = (Spannable) textLayout.getText();
            for (TextStyleSpan ss : sp.getSpans(0, sp.length(), TextStyleSpan.class)) {
                if (ss.isSpoiler()) {
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
                        .build();
            } else layout = new StaticLayout(sb, textLayout.getPaint(), textLayout.getWidth(), textLayout.getAlignment(), textLayout.getSpacingMultiplier(), textLayout.getSpacingAdd(), false);
            patchedLayoutRef.set(pl = layout);
        }

        if (!spoilers.isEmpty()) {
            pl.draw(canvas);
        } else {
            textLayout.draw(canvas);
        }

        clipOutPath.rewind();
        for (SpoilerEffect eff : spoilers) {
            Rect b = eff.getBounds();
            clipOutPath.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
        }
        if (!spoilers.isEmpty() && spoilers.get(0).rippleProgress != -1) {
            canvas.save();
            canvas.clipPath(clipOutPath);
            clipOutPath.rewind();
            if (!spoilers.isEmpty())
                spoilers.get(0).getRipplePath(clipOutPath);
            canvas.clipPath(clipOutPath);
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

    private class Particle {
        private float x, y;
        private float startAlpha, endAlpha, alpha;
        private float vecX, vecY;
        private float velocity;
        private float scale;

        private void draw(Canvas canvas) {
            particlePaint.setAlpha((int) (MathUtils.clamp(alpha, 0, 1) * 0xFF));
            particlePaint.setStrokeWidth(AndroidUtilities.dp(1.85f) * scale);
            canvas.drawPoint(x, y, particlePaint);
        }
    }
}
