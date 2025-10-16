package com.cryptobot.ai;

import java.util.Arrays;

/**
 * Small online logistic regression implementation using gradient descent.
 */
public final class LogisticRegressionModel implements SignalModel {
    private final double[] weights;
    private final double learningRate;

    public LogisticRegressionModel(int dimensions, double learningRate) {
        this.weights = new double[dimensions + 1];
        this.learningRate = learningRate;
    }

    @Override
    public double score(FeatureVector vector) {
        double z = weights[0];
        double[] values = vector.values();
        for (int i = 0; i < values.length; i++) {
            z += weights[i + 1] * values[i];
        }
        double sigmoid = 1.0 / (1.0 + Math.exp(-z));
        return sigmoid * 2 - 1;
    }

    @Override
    public void observe(FeatureVector vector, double outcome) {
        double prediction = (score(vector) + 1) / 2.0;
        double target = (outcome + 1) / 2.0;
        double error = prediction - target;
        weights[0] -= learningRate * error;
        double[] values = vector.values();
        for (int i = 0; i < values.length; i++) {
            weights[i + 1] -= learningRate * error * values[i];
        }
    }

    @Override
    public String name() {
        return "Logistic";
    }

    @Override
    public String toString() {
        return "LogisticRegressionModel" + Arrays.toString(weights);
    }
}
