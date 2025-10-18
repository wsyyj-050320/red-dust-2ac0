package com.cryptobot.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Basic runtime configuration loaded from environment variables with
 * sensible defaults to keep the application self-contained.
 */
public final class BotConfig {

    private final String apiKey;
    private final String apiSecret;
    private final List<String> symbols;
    private final Duration refreshInterval;
    private final int historyLimit;

    private BotConfig(String apiKey, String apiSecret, List<String> symbols,
                      Duration refreshInterval, int historyLimit) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.symbols = symbols;
        this.refreshInterval = refreshInterval;
        this.historyLimit = historyLimit;
    }

    public static BotConfig fromEnvironment() {
        String apiKey = System.getenv().getOrDefault("BINANCE_API_KEY", "demo-key");
        String apiSecret = System.getenv().getOrDefault("BINANCE_API_SECRET", "demo-secret");
        String symbolsValue = System.getenv().getOrDefault("BOT_SYMBOLS", "BTCUSDT,ETHUSDT");
        List<String> symbols = Arrays.stream(symbolsValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .toList();
        long seconds = Long.parseLong(System.getenv().getOrDefault("BOT_REFRESH_SECONDS", "20"));
        Duration refresh = Duration.ofSeconds(Math.max(5, seconds));
        int historyLimit = Integer.parseInt(System.getenv().getOrDefault("BOT_HISTORY_LIMIT", "720"));
        return new BotConfig(apiKey, apiSecret, symbols, refresh, Math.max(50, historyLimit));
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public int getHistoryLimit() {
        return historyLimit;
    }

    @Override
    public String toString() {
        return "BotConfig{" +
                "symbols=" + symbols +
                ", refreshInterval=" + refreshInterval +
                ", historyLimit=" + historyLimit +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKey, apiSecret, symbols, refreshInterval, historyLimit);
    }
}
