package com.cryptobot.backtest;

public record BacktestResult(String symbol,
                             double cumulativeReturn,
                             double maxDrawdown,
                             double sharpeRatio,
                             int trades) {
}
