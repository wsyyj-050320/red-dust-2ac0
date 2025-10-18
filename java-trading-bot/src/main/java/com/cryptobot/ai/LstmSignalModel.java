package com.cryptobot.ai;

import java.util.Arrays;

/**
 * Minimal gated recurrent unit approximating an LSTM cell. It is intentionally
 * lightweight yet still reacts to feature momentum.
 */
public final class LstmSignalModel implements SignalModel {
    private final double[] hidden;
    private final double[] cell;

    public LstmSignalModel(int dimensions) {
        this.hidden = new double[dimensions];
        this.cell = new double[dimensions];
    }

    @Override
    public double score(FeatureVector vector) {
        double[] values = vector.values();
        double total = 0.0;
        for (int i = 0; i < values.length; i++) {
            double inputGate = sigmoid(values[i] + hidden[i]);
            double forgetGate = sigmoid(0.5 * values[i] - hidden[i]);
            double outputGate = sigmoid(values[i] + hidden[i] * 0.5);
            double candidate = Math.tanh(values[i]);
            cell[i] = forgetGate * cell[i] + inputGate * candidate;
            hidden[i] = outputGate * Math.tanh(cell[i]);
            total += hidden[i];
        }
        return Math.max(-1.0, Math.min(1.0, total / values.length));
    }

    @Override
    public void observe(FeatureVector vector, double outcome) {
        double adjustment = outcome * 0.05;
        Arrays.setAll(cell, i -> cell[i] * (1 - 0.1) + adjustment);
    }

    @Override
    public String name() {
        return "LSTM";
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
