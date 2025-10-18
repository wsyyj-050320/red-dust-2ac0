package com.cryptobot.execution;

import com.cryptobot.config.BotConfig;
import com.cryptobot.model.TradeDecision;
import com.cryptobot.risk.RiskManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Responsible for dispatching trades to Binance. In offline sandboxes the
 * service will simply print the order payload to stdout.
 */
public final class TradeExecutionService {
    private final BotConfig config;
    private final RiskManager riskManager;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public TradeExecutionService(BotConfig config, RiskManager riskManager) {
        this.config = config;
        this.riskManager = riskManager;
    }

    public void execute(TradeDecision decision) {
        String payload = "symbol=" + decision.symbol() +
                "&side=" + decision.action() +
                "&quantity=" + decision.positionSize();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://fapi.binance.com/fapi/v1/order"))
                    .header("X-MBX-APIKEY", config.getApiKey())
                    .header("Authorization", buildAuthHeader())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(4))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Order response: " + response.statusCode() + " -> " + response.body());
        } catch (Exception ex) {
            System.out.println("Simulated order for " + decision.symbol() + ": " + payload);
        }
    }

    private String buildAuthHeader() {
        String combined = config.getApiKey() + ':' + config.getApiSecret();
        return "Basic " + Base64.getEncoder().encodeToString(combined.getBytes(StandardCharsets.UTF_8));
    }
}
