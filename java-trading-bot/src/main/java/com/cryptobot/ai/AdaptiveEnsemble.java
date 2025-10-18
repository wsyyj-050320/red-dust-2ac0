package com.cryptobot.ai;

import com.cryptobot.data.Candlestick;
import com.cryptobot.model.SignalSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Combines all signal models and keeps a rolling performance score per model
 * so that the strategy can rely on the best-performing signals.
 */
public final class AdaptiveEnsemble {
    private final FeatureExtractor extractor;
    private final List<SignalModel> models;
    private final Map<String, Double> performance = new HashMap<>();

    public AdaptiveEnsemble(FeatureExtractor extractor, List<SignalModel> models) {
        this.extractor = extractor;
        this.models = List.copyOf(models);
        for (SignalModel model : models) {
            performance.put(model.name(), 0.0);
        }
    }

    public static AdaptiveEnsemble defaultEnsemble(FeatureExtractor extractor) {
        List<SignalModel> models = List.of(
                new LogisticRegressionModel(4, 0.05),
                new LstmSignalModel(4),
                new TransformerSignalModel(0.4)
        );
        return new AdaptiveEnsemble(extractor, models);
    }

    public List<SignalSnapshot> evaluate(String symbol, List<Candlestick> candles) {
        List<FeatureVector> features = extractor.extract(candles);
        List<SignalSnapshot> snapshots = new ArrayList<>();
        for (SignalModel model : models) {
            double score = 0.0;
            for (FeatureVector vector : features) {
                score += model.score(vector);
            }
            if (!features.isEmpty()) {
                score /= features.size();
            }
            double confidence = Math.min(1.0, Math.abs(score));
            double weight = performance.getOrDefault(model.name(), 0.0);
            snapshots.add(new SignalSnapshot(model.name(), symbol, score, confidence, weight, Instant.now()));
        }
        return snapshots;
    }

    public void feedback(String modelName, double realizedOutcome) {
        performance.compute(modelName, (key, value) -> {
            double current = value == null ? 0.0 : value;
            double updated = current * 0.9 + realizedOutcome * 0.1;
            return Math.max(-1.0, Math.min(1.0, updated));
        });
    }

    @Override
    public int hashCode() {
        return Objects.hash(models);
    }
}
