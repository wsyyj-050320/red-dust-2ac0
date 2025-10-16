package com.cryptobot.model;

public record TradeDecision(String symbol,
                            TradeAction action,
                            double confidence,
                            double positionSize,
                            double stopLoss,
                            double takeProfit) {
}
