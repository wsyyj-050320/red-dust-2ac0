package com.cryptobot.ai;

import com.cryptobot.data.Candlestick;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates numeric feature vectors from candlestick sequences.
 */
public final class FeatureExtractor {
    public List<FeatureVector> extract(List<Candlestick> candles) {
        List<FeatureVector> vectors = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            Candlestick current = candles.get(i);
            Candlestick prev = candles.get(i - 1);
            Candlestick prev2 = candles.get(i - 2);
            double change = (current.getClose() - prev.getClose()) / Math.max(prev.getClose(), 1e-9);
            double volatility = Math.abs(prev.getClose() - prev2.getClose()) / Math.max(prev2.getClose(), 1e-9);
            double range = (current.getHigh() - current.getLow()) / Math.max(current.getOpen(), 1e-9);
            vectors.add(new FeatureVector(change, volatility, range, current.getVolume()));
        }
        return vectors;
    }
}
