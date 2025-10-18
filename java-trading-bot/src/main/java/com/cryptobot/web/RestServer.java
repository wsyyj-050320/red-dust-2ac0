package com.cryptobot.web;

import com.cryptobot.ai.AdaptiveEnsemble;
import com.cryptobot.backtest.Backtester;
import com.cryptobot.config.BotConfig;
import com.cryptobot.data.CandleStore;
import com.cryptobot.data.Candlestick;
import com.cryptobot.execution.TradeExecutionService;
import com.cryptobot.model.PortfolioSnapshot;
import com.cryptobot.model.SignalSnapshot;
import com.cryptobot.model.TradeDecision;
import com.cryptobot.risk.RiskManager;
import com.cryptobot.strategy.ContractStrategy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server exposing a JSON API and an SSE stream for the
 * dashboard.
 */
public final class RestServer {
    private final BotConfig config;
    private final CandleStore store;
    private final ContractStrategy strategy;
    private final AdaptiveSseBroadcaster broadcaster;
    private final Backtester backtester;
    private final RiskManager riskManager;
    private final TradeExecutionService executionService;
    private final AdaptiveEnsemble ensemble;
    private HttpServer server;

    public RestServer(BotConfig config,
                      CandleStore store,
                      ContractStrategy strategy,
                      AdaptiveEnsemble ensemble,
                      RiskManager riskManager,
                      TradeExecutionService executionService) {
        this.config = config;
        this.store = store;
        this.strategy = strategy;
        this.backtester = new Backtester(strategy);
        this.broadcaster = new AdaptiveSseBroadcaster(strategy, store, ensemble, riskManager, backtester);
        this.riskManager = riskManager;
        this.executionService = executionService;
        this.ensemble = ensemble;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(8090), 0);
        server.createContext("/api/candles", new CandleHandler());
        server.createContext("/api/signals", new SignalHandler());
        server.createContext("/api/backtest", new BacktestHandler());
        server.createContext("/api/portfolio", new PortfolioHandler());
        server.createContext("/api/trade", new TradeHandler());
        server.createContext("/stream", broadcaster);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("HTTP interface started on http://localhost:8090");
    }

    private final class CandleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] response = JsonRenderer.candles(store.snapshot()).getBytes(StandardCharsets.UTF_8);
            respond(exchange, 200, "application/json", response);
        }
    }

    private final class SignalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<SignalSnapshot> all = new ArrayList<>();
            for (String symbol : config.getSymbols()) {
                List<Candlestick> series = store.latest(symbol, 120);
                if (series.isEmpty()) {
                    continue;
                }
                all.addAll(ensemble.evaluate(symbol, series));
            }
            byte[] response = JsonRenderer.signals(all).getBytes(StandardCharsets.UTF_8);
            respond(exchange, 200, "application/json", response);
        }
    }

    private final class BacktestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<com.cryptobot.backtest.BacktestResult> results = new ArrayList<>();
            for (String symbol : config.getSymbols()) {
                results.add(backtester.run(symbol, store.latest(symbol, config.getHistoryLimit())));
            }
            byte[] response = JsonRenderer.backtest(results).getBytes(StandardCharsets.UTF_8);
            respond(exchange, 200, "application/json", response);
        }
    }

    private final class PortfolioHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<PortfolioSnapshot> snapshots = new ArrayList<>();
            for (String symbol : config.getSymbols()) {
                snapshots.add(new PortfolioSnapshot(symbol, riskManager.position(symbol),
                        riskManager.size(symbol),
                        store.latest(symbol, 1).stream().findFirst().map(Candlestick::getClose).orElse(0.0),
                        0.0,
                        Instant.now()));
            }
            byte[] response = JsonRenderer.portfolio(snapshots).getBytes(StandardCharsets.UTF_8);
            respond(exchange, 200, "application/json", response);
        }
    }

    private final class TradeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "text/plain", "Only POST is supported".getBytes(StandardCharsets.UTF_8));
                return;
            }
            Map<String, List<String>> query = splitQuery(exchange.getRequestURI().getQuery());
            String symbol = query.getOrDefault("symbol", List.of("BTCUSDT")).get(0);
            List<Candlestick> series = store.latest(symbol, 120);
            TradeDecision rawDecision = strategy.decide(symbol, series,
                    ensemble.evaluate(symbol, series));
            TradeDecision decision = riskManager.apply(symbol, rawDecision);
            executionService.execute(decision);
            byte[] response = JsonRenderer.decision(decision).getBytes(StandardCharsets.UTF_8);
            respond(exchange, 200, "application/json", response);
        }
    }

    private void respond(HttpExchange exchange, int status, String contentType, byte[] payload) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    private Map<String, List<String>> splitQuery(String query) {
        return com.cryptobot.web.UrlParser.parse(query);
    }
}
