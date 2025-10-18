package com.cryptobot.backtest;

import com.cryptobot.data.Candlestick;
import com.cryptobot.model.TradeAction;
import com.cryptobot.model.TradeDecision;
import com.cryptobot.strategy.ContractStrategy;

import java.util.List;

/**
 * Performs a walk-forward backtest using the provided strategy decisions.
 */
public final class Backtester {
    private final ContractStrategy strategy;

    public Backtester(ContractStrategy strategy) {
        this.strategy = strategy;
    }

    public BacktestResult run(String symbol, List<Candlestick> candles) {
        if (candles.size() < 10) {
            return new BacktestResult(symbol, 0.0, 0.0, 0.0, 0);
        }
        double capital = 1.0;
        double peak = capital;
        int trades = 0;
        for (int i = 20; i < candles.size(); i++) {
            List<Candlestick> window = candles.subList(0, i);
            TradeDecision decision = strategy.simulate(symbol, window);
            if (decision.action() == TradeAction.FLAT) {
                continue;
            }
            double direction = decision.action() == TradeAction.LONG ? 1 : -1;
            double move = (candles.get(i).getClose() - candles.get(i - 1).getClose()) / Math.max(1e-9, candles.get(i - 1).getClose());
            double pnl = direction * move * decision.positionSize();
            capital += pnl;
            peak = Math.max(peak, capital);
            trades++;
        }
        double drawdown = peak == 0 ? 0 : (peak - capital) / peak;
        double sharpe = (capital - 1.0) / Math.max(1e-9, Math.sqrt(trades));
        return new BacktestResult(symbol, capital - 1.0, drawdown, sharpe, trades);
    }
}
