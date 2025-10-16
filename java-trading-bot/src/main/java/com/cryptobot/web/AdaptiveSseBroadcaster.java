package com.cryptobot.web;

import com.cryptobot.ai.AdaptiveEnsemble;
import com.cryptobot.backtest.Backtester;
import com.cryptobot.data.CandleStore;
import com.cryptobot.model.SignalSnapshot;
import com.cryptobot.risk.RiskManager;
import com.cryptobot.strategy.ContractStrategy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Streams signal updates as an SSE feed using the JDK HTTP server.
 */
final class AdaptiveSseBroadcaster implements HttpHandler {
    private final ContractStrategy strategy;
    private final CandleStore store;
    private final AdaptiveEnsemble ensemble;
    private final RiskManager riskManager;
    private final Backtester backtester;
    private final Set<OutputStream> clients = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    AdaptiveSseBroadcaster(ContractStrategy strategy,
                           CandleStore store,
                           AdaptiveEnsemble ensemble,
                           RiskManager riskManager,
                           Backtester backtester) {
        this.strategy = strategy;
        this.store = store;
        this.ensemble = ensemble;
        this.riskManager = riskManager;
        this.backtester = backtester;
        scheduler.scheduleAtFixedRate(this::broadcast, 3, 5, TimeUnit.SECONDS);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        OutputStream output = exchange.getResponseBody();
        clients.add(output);
    }

    private void broadcast() {
        for (OutputStream client : clients) {
            try {
                String payload = buildEvent();
                client.write(payload.getBytes(StandardCharsets.UTF_8));
                client.flush();
            } catch (IOException ex) {
                clients.remove(client);
            }
        }
    }

    private String buildEvent() {
        StringBuilder builder = new StringBuilder("data:");
        builder.append('{');
        builder.append("\"signals\":");
        builder.append(JsonRenderer.signals(flattenSignals()));
        builder.append(',');
        builder.append("\"positions\":");
        builder.append(JsonRenderer.portfolio(WebUtil.portfolioSnapshots(store, riskManager)));
        builder.append(',');
        builder.append("\"backtests\":");
        builder.append(JsonRenderer.backtest(WebUtil.backtestSnapshots(store, backtester, riskManager)));
        builder.append('}');
        builder.append("\n\n");
        return builder.toString();
    }

    private List<SignalSnapshot> flattenSignals() {
        return WebUtil.latestSignals(strategy, store, ensemble, riskManager);
    }
}
