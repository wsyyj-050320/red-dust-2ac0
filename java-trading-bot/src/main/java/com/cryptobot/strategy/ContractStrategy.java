package com.cryptobot.strategy;

import com.cryptobot.ai.AdaptiveEnsemble;
import com.cryptobot.ai.MovingAverageSignalModel;
import com.cryptobot.data.Candlestick;
import com.cryptobot.model.SignalSnapshot;
import com.cryptobot.model.TradeAction;
import com.cryptobot.model.TradeDecision;
import com.cryptobot.risk.RiskManager;

import java.util.List;

/**
 * Futures-oriented strategy that blends the ensemble output with a classical
 * moving-average indicator and defers risk constraints to {@link RiskManager}.
 */
public final class ContractStrategy {
    private final AdaptiveEnsemble ensemble;
    private final RiskManager riskManager;
    private final MovingAverageSignalModel fast = new MovingAverageSignalModel(9, 21);
    private final MovingAverageSignalModel slow = new MovingAverageSignalModel(21, 55);
    private final com.cryptobot.data.CandleStore store;

    public ContractStrategy(AdaptiveEnsemble ensemble, RiskManager riskManager, com.cryptobot.data.CandleStore store) {
        this.ensemble = ensemble;
        this.riskManager = riskManager;
        this.store = store;
    }

    public void onMarketUpdate(String symbol, List<Candlestick> candles, List<SignalSnapshot> signals) {
        TradeDecision decision = decide(symbol, candles, signals);
        riskManager.apply(symbol, decision);
    }

    public TradeDecision simulate(String symbol, List<Candlestick> candles) {
        List<SignalSnapshot> signals = ensemble.evaluate(symbol, candles);
        return riskManager.apply(symbol, decide(symbol, candles, signals));
    }

    public TradeDecision decide(String symbol, List<Candlestick> candles, List<SignalSnapshot> signals) {
        if (candles.size() < 30) {
            return new TradeDecision(symbol, TradeAction.FLAT, 0.0, 0.0, 0.0, 0.0);
        }
        double fastScore = fast.crossover(candles);
        double slowScore = slow.crossover(candles);
        double ensembleScore = 0.0;
        double ensembleWeight = 0.0;
        for (SignalSnapshot snapshot : signals) {
            ensembleScore += snapshot.score() * (1 + snapshot.weight());
            ensembleWeight += (1 + snapshot.weight());
        }
        double blended = (fastScore + slowScore + (ensembleWeight == 0 ? ensembleScore : ensembleScore / ensembleWeight)) / 3.0;
        TradeAction action;
        if (blended > 0.15) {
            action = TradeAction.LONG;
        } else if (blended < -0.15) {
            action = TradeAction.SHORT;
        } else {
            action = TradeAction.FLAT;
        }
        double confidence = Math.min(1.0, Math.abs(blended));
        double baseSize = 0.5 + confidence;
        double stopLoss = candles.get(candles.size() - 1).getClose() * (action == TradeAction.LONG ? 0.992 : 1.008);
        double takeProfit = candles.get(candles.size() - 1).getClose() * (action == TradeAction.LONG ? 1.012 : 0.988);
        return new TradeDecision(symbol, action, confidence, baseSize, stopLoss, takeProfit);
    }

    public com.cryptobot.data.CandleStore store() {
        return store;
    }

    public AdaptiveEnsemble ensemble() {
        return ensemble;
    }
}
