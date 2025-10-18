package com.cryptobot.data;

import com.cryptobot.ai.AdaptiveEnsemble;
import com.cryptobot.config.BotConfig;
import com.cryptobot.model.SignalSnapshot;
import com.cryptobot.strategy.ContractStrategy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically refreshes market data from Binance if network access is
 * available. In sandboxed environments the scheduler falls back to a
 * deterministic pseudo-random generator so the remainder of the stack
 * keeps operating.
 */
public final class MarketDataScheduler {
    private final BotConfig config;
    private final CandleStore store;
    private final AdaptiveEnsemble ensemble;
    private final ContractStrategy strategy;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final Random random = new Random(42L);

    public MarketDataScheduler(BotConfig config, CandleStore store, AdaptiveEnsemble ensemble, ContractStrategy strategy) {
        this.config = config;
        this.store = store;
        this.ensemble = ensemble;
        this.strategy = strategy;
    }

    public void start() {
        executor.scheduleAtFixedRate(this::refreshAll, 0L, config.getRefreshInterval().toSeconds(), TimeUnit.SECONDS);
    }

    private void refreshAll() {
        for (String symbol : config.getSymbols()) {
            try {
                List<Candlestick> candles = fetchCandles(symbol);
                store.upsert(symbol, candles);
                List<SignalSnapshot> signals = ensemble.evaluate(symbol, store.latest(symbol, 60));
                strategy.onMarketUpdate(symbol, store.latest(symbol, 120), signals);
            } catch (Exception e) {
                System.err.println("Failed to refresh " + symbol + ": " + e.getMessage());
            }
        }
    }

    private List<Candlestick> fetchCandles(String symbol) throws IOException, InterruptedException {
        String url = "https://fapi.binance.com/fapi/v1/klines?symbol=" + symbol + "&interval=1m&limit=" + config.getHistoryLimit();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return CandleParser.parse(response.body());
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Falling back to synthetic candles for " + symbol + ": " + ex.getMessage());
        }
        return syntheticSeries(symbol, config.getHistoryLimit());
    }

    private List<Candlestick> syntheticSeries(String symbol, int points) {
        List<Candlestick> synthetic = new ArrayList<>(points);
        double base = 20000.0 + symbol.hashCode() % 5000;
        double price = base;
        Instant start = Instant.now().minusSeconds(points * 60L);
        for (int i = 0; i < points; i++) {
            double drift = Math.sin(i / 15.0) * 25 + (random.nextDouble() - 0.5) * 10;
            double open = price;
            price = Math.max(100.0, price + drift);
            double close = price;
            double high = Math.max(open, close) + random.nextDouble() * 10;
            double low = Math.min(open, close) - random.nextDouble() * 10;
            double volume = 100 + random.nextDouble() * 50;
            synthetic.add(new Candlestick(start.plusSeconds(i * 60L), open, high, low, close, volume));
        }
        return synthetic;
    }
}
