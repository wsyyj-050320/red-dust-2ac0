package com.cryptobot.ai;

import java.util.Arrays;

/**
 * Lightweight immutable feature representation.
 */
public final class FeatureVector {
    private final double[] values;

    public FeatureVector(double... values) {
        this.values = values;
    }

    public double[] values() {
        return values;
    }

    public int size() {
        return values.length;
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
