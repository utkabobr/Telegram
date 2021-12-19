package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.text.Layout;
import android.text.Spannable;
import android.view.MotionEvent;
import android.widget.EditText;

import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Components.spoilers.SpoilersClickDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class EditTextEffects extends EditText {
    private List<SpoilerEffect> spoilers = new ArrayList<>();
    private Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private boolean isSpoilersRevealed;
    private boolean shouldRevealSpoilersByTouch = true;
    private SpoilersClickDetector clickDetector;
    private boolean suppressOnTextChanged;
    private Path path = new Path();

    public EditTextEffects(Context context) {
        super(context);

        clickDetector = new SpoilersClickDetector(this, spoilers, (eff, x, y) -> {
            setSpoilersRevealed(true, false);
            eff.setOnRippleEndCallback(() -> post(this::invalidateSpoilers));

            for (SpoilerEffect ef : spoilers)
                ef.startRipple(x, y, Math.max(getWidth(), getHeight()));
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateEffects();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (!suppressOnTextChanged)
            invalidateEffects();
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (!suppressOnTextChanged) {
            isSpoilersRevealed = false;
            if (spoilersPool != null) // Constructor check
                spoilersPool.clear();
        }
        super.setText(text, type);
    }

    /**
     * Sets if spoilers should be revealed by touch or not
     */
    public void setShouldRevealSpoilersByTouch(boolean shouldRevealSpoilersByTouch) {
        this.shouldRevealSpoilersByTouch = shouldRevealSpoilersByTouch;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (shouldRevealSpoilersByTouch && clickDetector.onTouchEvent(event)) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                MotionEvent c = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                super.dispatchTouchEvent(c);
                c.recycle();
            }
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Sets if spoiler are already revealed or not
     */
    public void setSpoilersRevealed(boolean spoilersRevealed, boolean notifyEffects) {
        isSpoilersRevealed = spoilersRevealed;
        Spannable text = getText();
        if (text != null) {
            TextStyleSpan[] spans = text.getSpans(0, text.length(), TextStyleSpan.class);
            for (TextStyleSpan span : spans) {
                if (span.isSpoiler()) {
                    span.setSpoilerRevealed(spoilersRevealed);
                }
            }
        }
        suppressOnTextChanged = true;
        setText(text);
        suppressOnTextChanged = false;

        if (notifyEffects) {
            invalidateSpoilers();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        path.rewind();
        for (SpoilerEffect eff : spoilers) {
            Rect bounds = eff.getBounds();
            path.addRect(bounds.left, bounds.top, bounds.right, bounds.bottom, Path.Direction.CW);
        }
        canvas.clipPath(path, Region.Op.DIFFERENCE);
        super.onDraw(canvas);
        canvas.restore();

        canvas.save();
        canvas.clipPath(path);
        path.rewind();
        if (!spoilers.isEmpty())
            spoilers.get(0).getRipplePath(path);
        canvas.clipPath(path);
        canvas.translate(0, -getPaddingTop());
        super.onDraw(canvas);
        canvas.restore();

        for (SpoilerEffect eff : spoilers) {
            eff.draw(canvas);
        }
    }

    public void invalidateEffects() {
        invalidateSpoilers();
    }

    private void invalidateSpoilers() {
        if (spoilers == null) return; // A null-check for super constructor, because it calls onTextChanged
        spoilersPool.addAll(spoilers);
        spoilers.clear();

        if (isSpoilersRevealed) {
            invalidate();
            return;
        }

        Layout layout = getLayout();
        if (layout != null && getText() != null)
            SpoilerEffect.addSpoilers(this, spoilersPool, spoilers);
        invalidate();
    }
}
