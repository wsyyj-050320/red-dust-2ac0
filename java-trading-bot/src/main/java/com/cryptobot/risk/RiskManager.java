package com.cryptobot.risk;

import com.cryptobot.config.BotConfig;
import com.cryptobot.model.PositionSide;
import com.cryptobot.model.TradeAction;
import com.cryptobot.model.TradeDecision;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very small risk manager that caps leverage per symbol and tracks current
 * synthetic contract exposure.
 */
public final class RiskManager {
    private final Map<String, PositionSide> sides = new ConcurrentHashMap<>();
    private final Map<String, Double> sizes = new ConcurrentHashMap<>();
    private final BotConfig config;
    private final double maxLeverage = 3.0;

    public RiskManager(BotConfig config) {
        this.config = config;
        for (String symbol : config.getSymbols()) {
            sides.put(symbol, PositionSide.NONE);
            sizes.put(symbol, 0.0);
        }
    }

    public synchronized TradeDecision apply(String symbol, TradeDecision decision) {
        double allowed = Math.min(maxLeverage, 1.0 + Math.abs(decision.confidence()) * 2);
        if (decision.action() == TradeAction.FLAT) {
            sides.put(symbol, PositionSide.NONE);
            sizes.put(symbol, 0.0);
            return decision;
        }
        double proposed = Math.min(allowed, decision.positionSize());
        PositionSide newSide = decision.action() == TradeAction.LONG ? PositionSide.LONG : PositionSide.SHORT;
        sides.put(symbol, newSide);
        sizes.put(symbol, proposed);
        return new TradeDecision(symbol, decision.action(), decision.confidence(), proposed,
                decision.stopLoss(), decision.takeProfit());
    }

    public PositionSide position(String symbol) {
        return sides.getOrDefault(symbol, PositionSide.NONE);
    }

    public double size(String symbol) {
        return sizes.getOrDefault(symbol, 0.0);
    }

    public Instant now() {
        return Instant.now();
    }

    public BotConfig config() {
        return config;
    }
}
