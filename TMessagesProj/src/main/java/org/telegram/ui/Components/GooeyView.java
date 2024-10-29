package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLSurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

import java.util.concurrent.CopyOnWriteArrayList;

public class GooeyView extends FrameLayout {
    @IntDef(value = {
            GLSurfaceView.RENDERMODE_WHEN_DIRTY,
            GLSurfaceView.RENDERMODE_CONTINUOUSLY
    })
    public @interface RenderMode {}

    private GLGooeyView glView;
    private FrameLayout containerView;

    private boolean skipDraw = true;

    public GooeyView(@NonNull Context context) {
        super(context);

        setClipChildren(false);
        setClipToPadding(false);

        containerView = new FrameLayout(context);
        containerView.setVisibility(INVISIBLE);
        containerView.setClipChildren(false);
        containerView.setClipToPadding(false);
        addView(containerView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        glView = new GLGooeyView(context);
        glView.setContainerView(containerView);
        addView(glView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void setFade(float from, float to) {
        glView.setFade(from, to);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChild(containerView, widthMeasureSpec, heightMeasureSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(containerView.getMeasuredWidth() + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(containerView.getMeasuredHeight() + getPaddingTop() + getPaddingBottom(), MeasureSpec.EXACTLY));
    }

    public void setSkipDraw(boolean skipDraw) {
        this.skipDraw = skipDraw;
        setRenderMode(skipDraw ? GLSurfaceView.RENDERMODE_CONTINUOUSLY : GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        glView.setVisibility(skipDraw ? VISIBLE : GONE);
        if (skipDraw) {
            glView.onResume();
        } else {
            glView.onPause();
        }
        containerView.setVisibility(skipDraw ? INVISIBLE : VISIBLE);
        containerView.invalidate();
        invalidate();
    }

    public void setRenderMode(@RenderMode int mode) {
        glView.setRenderMode(mode);
    }

    public void invalidateGl() {
        glView.requestRender();
    }

    public FrameLayout getContainerView() {
        return containerView;
    }

    public void onResume() {
        glView.onResume();
    }

    public void onPause() {
        glView.onPause();
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (child != containerView && child != glView) {
            throw new IllegalArgumentException("Use getContainerView().addView(...)!");
        }
        super.addView(child, index, params);
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (skipDraw && child == containerView) return false;
        return super.drawChild(canvas, child, drawingTime);
    }
}
