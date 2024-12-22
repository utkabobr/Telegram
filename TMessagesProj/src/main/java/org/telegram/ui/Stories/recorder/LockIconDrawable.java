package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;

public class LockIconDrawable extends Drawable {
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mColor;

    private Path path = new Path();
    private Path path2 = new Path();

    private float lockProgress = 0f;
    private boolean wasLocked = false;

    public LockIconDrawable() {
        outlinePaint.setStrokeJoin(Paint.Join.ROUND);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeWidth(dp(1.75f));
        outlinePaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    public int getIntrinsicWidth() {
        return dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(24);
    }

    public void setLockProgress(float lockProgress) {
        this.lockProgress = lockProgress;
        invalidateSelf();
    }

    public void setLocked(boolean locked) {
        if (wasLocked == locked) return;

        wasLocked = locked;
        new SpringAnimation(new FloatValueHolder(lockProgress))
                .setMinimumVisibleChange(1 / 500f)
                .setSpring(new SpringForce(locked ? 1f : 0f)
                        .setStiffness(1000f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                .addUpdateListener((animation, value, velocity) -> setLockProgress(value))
                .start();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int l = getBounds().left, t = getBounds().top, r = getBounds().right, b = getBounds().bottom;
        int w = r - l, h = b - t;

        path.rewind();
        path2.rewind();

        // 48 * 68
        // 68 => dp(24)

        float lockW = 3f / 4f * h;
        AndroidUtilities.rectTmp.set(l + (w - lockW) / 2f, t + lockW / 2f, l + (w + lockW) / 2f, t + h);
        path.addRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), Path.Direction.CW);
        path2.addCircle(AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY(), dp(2) * lockProgress, Path.Direction.CW);

        path.op(path2, Path.Op.DIFFERENCE);
        canvas.drawPath(path, paint);

        float sidePad = lockW / 4f;
        AndroidUtilities.rectTmp.set(l + (w - lockW) / 2f + sidePad, t + outlinePaint.getStrokeWidth(), l + (w + lockW) / 2f - sidePad, t + outlinePaint.getStrokeWidth() + lockW - sidePad * 2);

        canvas.save();
        canvas.rotate(15 * (1f - lockProgress), AndroidUtilities.rectTmp.right, t + lockW / 2f);
        canvas.drawArc(AndroidUtilities.rectTmp, 0, -180, false, outlinePaint);
        canvas.drawLine(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.centerY(), AndroidUtilities.rectTmp.left, t + lockW / 2f - (outlinePaint.getStrokeWidth() * (1f - lockProgress)), outlinePaint);
        canvas.drawLine(AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.centerY(), AndroidUtilities.rectTmp.right, t + lockW / 2f, outlinePaint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int i) {
        paint.setColor(ColorUtils.setAlphaComponent(mColor, (int) (Color.alpha(mColor) * (i / (float) 0xFF))));
        outlinePaint.setColor(paint.getColor());
    }

    public void setColor(int color) {
        paint.setColor(mColor = color);
        outlinePaint.setColor(color);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
