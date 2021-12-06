package org.telegram.ui.Components;

import androidx.annotation.FloatRange;

public class InterpolatedFloatValue {
    private float startValue, currentValue, endValue;

    public InterpolatedFloatValue(float value) {
        startValue = currentValue = endValue = value;
    }

    public void setCurrentValue(float currentValue) {
        setCurrentValue(currentValue, true);
    }

    public void setCurrentValue(float currentValue, boolean atRest) {
        this.currentValue = currentValue;
        if (atRest)
            this.startValue = endValue = currentValue;
    }

    public void recordStartValue() {
        startValue = currentValue;
    }

    public float getEndValue() {
        return endValue;
    }

    public void setEndValue(float f) {
        endValue = f;
    }

    public float interpolateValue(@FloatRange(from = 0, to = 1) float progress) {
        return currentValue = startValue + (endValue - startValue) * progress;
    }

    public float getCurrentValue() {
        return currentValue;
    }

    @Override
    public String toString() {
        return "InterpolatedFloatValue{" +
                "startValue=" + startValue +
                ", currentValue=" + currentValue +
                ", endValue=" + endValue +
                '}';
    }
}
