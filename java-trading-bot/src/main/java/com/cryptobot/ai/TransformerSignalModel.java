package com.cryptobot.ai;

/**
 * Small self-attention inspired model that emphasises large deltas.
 */
public final class TransformerSignalModel implements SignalModel {
    private final double temperature;

    public TransformerSignalModel(double temperature) {
        this.temperature = temperature;
    }

    @Override
    public double score(FeatureVector vector) {
        double[] values = vector.values();
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            max = Math.max(max, value);
        }
        double numerator = 0.0;
        double denominator = 0.0;
        for (double value : values) {
            double scaled = Math.exp((value - max) / temperature);
            numerator += scaled * value;
            denominator += scaled;
        }
        double attentionMean = denominator == 0 ? 0.0 : numerator / denominator;
        return Math.max(-1.0, Math.min(1.0, attentionMean * 3));
    }

    @Override
    public String name() {
        return "Transformer";
    }
}
