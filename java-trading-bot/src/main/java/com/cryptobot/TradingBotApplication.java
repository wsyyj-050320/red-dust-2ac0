package com.cryptobot;

import com.cryptobot.ai.AdaptiveEnsemble;
import com.cryptobot.ai.FeatureExtractor;
import com.cryptobot.config.BotConfig;
import com.cryptobot.data.CandleStore;
import com.cryptobot.data.MarketDataScheduler;
import com.cryptobot.execution.TradeExecutionService;
import com.cryptobot.risk.RiskManager;
import com.cryptobot.strategy.ContractStrategy;
import com.cryptobot.web.RestServer;

/**
 * Entry point for the standalone trading bot runtime.
 */
public final class TradingBotApplication {

    private TradingBotApplication() {
    }

    public static void main(String[] args) throws Exception {
        BotConfig config = BotConfig.fromEnvironment();
        CandleStore candleStore = new CandleStore(config.getSymbols(), config.getHistoryLimit());
        FeatureExtractor extractor = new FeatureExtractor();
        AdaptiveEnsemble ensemble = AdaptiveEnsemble.defaultEnsemble(extractor);
        RiskManager riskManager = new RiskManager(config);
        ContractStrategy strategy = new ContractStrategy(ensemble, riskManager, candleStore);
        TradeExecutionService executionService = new TradeExecutionService(config, riskManager);
        MarketDataScheduler scheduler = new MarketDataScheduler(config, candleStore, ensemble, strategy);
        RestServer restServer = new RestServer(config, candleStore, strategy, ensemble, riskManager, executionService);

        scheduler.start();
        restServer.start();
    }
}
