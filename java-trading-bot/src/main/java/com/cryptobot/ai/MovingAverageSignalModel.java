package com.cryptobot.ai;

import com.cryptobot.data.Candlestick;

import java.util.List;

/**
 * Computes a classic moving-average crossover indicator.
 */
public final class MovingAverageSignalModel {
    private final int shortWindow;
    private final int longWindow;

    public MovingAverageSignalModel(int shortWindow, int longWindow) {
        this.shortWindow = shortWindow;
        this.longWindow = longWindow;
    }

    public double crossover(List<Candlestick> candles) {
        if (candles.size() < Math.max(shortWindow, longWindow)) {
            return 0.0;
        }
        double shortAvg = average(candles, shortWindow);
        double longAvg = average(candles, longWindow);
        double signal = (shortAvg - longAvg) / Math.max(longAvg, 1e-9);
        return Math.max(-1.0, Math.min(1.0, signal * 5));
    }

    private double average(List<Candlestick> candles, int length) {
        int start = candles.size() - length;
        double sum = 0.0;
        for (int i = start; i < candles.size(); i++) {
            sum += candles.get(i).getClose();
        }
        return sum / length;
    }
}
