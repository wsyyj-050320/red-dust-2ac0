package com.cryptobot.ai;

/**
 * Simple inference contract that returns a bullish score in the range [-1, 1].
 */
public interface SignalModel {
    double score(FeatureVector vector);

    default void observe(FeatureVector vector, double outcome) {
        // Optional training hook.
    }

    String name();
}
