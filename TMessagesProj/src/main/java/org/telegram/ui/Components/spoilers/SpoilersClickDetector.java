package org.telegram.ui.Components.spoilers;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.GestureDetectorCompat;

import java.util.List;

public class SpoilersClickDetector {
    private GestureDetectorCompat gestureDetector;

    public SpoilersClickDetector(View v, List<SpoilerEffect> spoilers, OnSpoilerClickedListener clickedListener) {
        gestureDetector = new GestureDetectorCompat(v.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                int x = (int) e.getX(), y = (int) e.getY();
                y += v.getScrollY();
                y -= v.getPaddingTop();
                for (SpoilerEffect eff : spoilers) {
                    if (eff.getBounds().contains(x, y)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                int x = (int) e.getX(), y = (int) e.getY();
                y += v.getScrollY();
                y -= v.getPaddingTop();
                for (SpoilerEffect eff : spoilers) {
                    if (eff.getBounds().contains(x, y)) {
                        clickedListener.onSpoilerClicked(eff, x, y);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public boolean onTouchEvent(MotionEvent ev) {
        return gestureDetector.onTouchEvent(ev);
    }

    public interface OnSpoilerClickedListener {
        void onSpoilerClicked(SpoilerEffect spoiler, float x, float y);
    }
}