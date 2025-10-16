package com.cryptobot.model;

import java.time.Instant;

public record PortfolioSnapshot(String symbol,
                                PositionSide side,
                                double contracts,
                                double entryPrice,
                                double unrealizedPnl,
                                Instant timestamp) {
}
