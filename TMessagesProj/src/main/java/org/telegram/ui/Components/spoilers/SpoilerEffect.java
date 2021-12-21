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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.TextStyleSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SpoilerEffect extends Drawable {
    private final static int TILE_SIZE = 16, FPS = 30, PARTICLE_COUNT_PER_TILE = 40, FRAME_VARIANTS = 3;
    private final static float SIMULATION_SECONDS = 1.5f;

    private static SparseArray<List<Bitmap>> framesMap = new SparseArray<>();
    private static int lastRenderedTileSize;

    private static Rect tempRect = new Rect();
    private static RectF tempRectF = new RectF();

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
    private SparseArray<Float> rotations = new SparseArray<>();
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
            for (int k = 0; k < framesMap.size(); k++) {
                List<Bitmap> frames = framesMap.valueAt(k);
                for (Bitmap b : frames) b.recycle();
                frames.clear();

                List<Particle> particles = new ArrayList<>();
                for (int i = 0; i < PARTICLE_COUNT_PER_TILE; i++) {
                    Particle newParticle = new Particle();
                    newParticle.x = getBounds().left + Utilities.random.nextFloat() * tSize;
                    newParticle.y = getBounds().top + Utilities.random.nextFloat() * tSize;

                    double angleRad = Utilities.random.nextFloat() * Math.PI * 2 - Math.PI;
                    float vx = (float) Math.cos(angleRad);
                    float vy = (float) Math.sin(angleRad);

                    newParticle.vecX = vx;
                    newParticle.vecY = vy;

                    newParticle.scale = 0.5f + Utilities.random.nextFloat() * 0.5f; // [0.5;1]

                    newParticle.velocity = 3 + Utilities.random.nextFloat() * 1.5f;
                    particles.add(newParticle);
                }

                int dt = 1000 / FPS + 1;
                for (int i = 0; i < SIMULATION_SECONDS * FPS; i++) {
                    Bitmap bm = Bitmap.createBitmap(tSize, tSize, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(bm);
                    for (Particle particle : particles) {
                        float hdt = particle.velocity * dt / 500f;
                        particle.x += particle.vecX * hdt;
                        particle.y += particle.vecY * hdt;

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
        while (x < getBounds().right) {
            Float rot = rotations.get(i, null);
            if (rot == null) rotations.put(i, rot = Utilities.random.nextFloat() * 360f);
            int var = frameVariants.get(i, -1);
            if (var == -1) frameVariants.put(i, var = Utilities.random.nextInt(FRAME_VARIANTS));

            canvas.save();
            canvas.rotate(rot, lastRenderedTileSize / 2f, lastRenderedTileSize / 2f);

            Bitmap bm = framesMap.get(var).get(reverseFrames ? (int) (SIMULATION_SECONDS * FPS - frameNum - 1) : frameNum);
            float sc = h / lastRenderedTileSize + 0.3f;
            canvas.scale(sc, sc, lastRenderedTileSize / 2f, lastRenderedTileSize / 2f);
            canvas.drawBitmap(bm, 0, 0, bitmapPaint);
            canvas.restore();

            canvas.translate(lastRenderedTileSize, 0);
            x += lastRenderedTileSize;
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

                        int offWidth = 0;
                        if (realStart != start) {
                            offWidth = (int) textPaint.measureText(textLayout.getText(), start, realStart);
                        }

                        SpannableStringBuilder vSpan = new SpannableStringBuilder(textLayout.getText(), realStart, realEnd);
                        vSpan.removeSpan(span);
                        int tLen = vSpan.toString().trim().length();
                        if (tLen == 0)
                            continue;
                        SpoilerEffect spoilerEffect = spoilersPool == null || spoilersPool.isEmpty() ? new SpoilerEffect() : spoilersPool.remove(0);
                        spoilerEffect.setRippleProgress(-1);
                        spoilerEffect.setBounds(tempRect.left + offWidth, tempRect.top, (int) (tempRect.left + offWidth + textPaint.measureText(vSpan, 0, vSpan.length())), tempRect.bottom);
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

    private class Particle {
        private float x, y;
        private float vecX, vecY;
        private float velocity;
        private float scale;

        private void draw(Canvas canvas) {
            particlePaint.setStrokeWidth(AndroidUtilities.dp(1.5f) * scale);
            canvas.drawPoint(x, y, particlePaint);
        }
    }
}
