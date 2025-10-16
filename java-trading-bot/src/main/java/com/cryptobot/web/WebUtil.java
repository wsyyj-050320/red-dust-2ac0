package com.cryptobot.web;

import com.cryptobot.ai.AdaptiveEnsemble;
import com.cryptobot.backtest.Backtester;
import com.cryptobot.data.CandleStore;
import com.cryptobot.data.Candlestick;
import com.cryptobot.model.PortfolioSnapshot;
import com.cryptobot.model.SignalSnapshot;
import com.cryptobot.risk.RiskManager;
import com.cryptobot.strategy.ContractStrategy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class WebUtil {
    private WebUtil() {
    }

    static List<PortfolioSnapshot> portfolioSnapshots(CandleStore store, RiskManager riskManager) {
        List<PortfolioSnapshot> snapshots = new ArrayList<>();
        for (String symbol : riskManager.config().getSymbols()) {
            double lastPrice = store.latest(symbol, 1).stream().findFirst().map(Candlestick::getClose).orElse(0.0);
            snapshots.add(new PortfolioSnapshot(symbol, riskManager.position(symbol),
                    riskManager.size(symbol), lastPrice, 0.0, Instant.now()));
        }
        return snapshots;
    }

    static List<SignalSnapshot> latestSignals(ContractStrategy strategy,
                                              CandleStore store,
                                              AdaptiveEnsemble ensemble,
                                              RiskManager riskManager) {
        List<SignalSnapshot> snapshots = new ArrayList<>();
        for (String symbol : riskManager.config().getSymbols()) {
            List<Candlestick> series = store.latest(symbol, 120);
            if (series.isEmpty()) {
                continue;
            }
            snapshots.addAll(ensemble.evaluate(symbol, series));
        }
        return snapshots;
    }

    static List<com.cryptobot.backtest.BacktestResult> backtestSnapshots(CandleStore store,
                                                                        Backtester backtester,
                                                                        RiskManager riskManager) {
        List<com.cryptobot.backtest.BacktestResult> results = new ArrayList<>();
        for (String symbol : riskManager.config().getSymbols()) {
            results.add(backtester.run(symbol, store.latest(symbol, 240)));
        }
        return results;
    }
}
