package com.cryptobot.model;

import java.time.Instant;

public record SignalSnapshot(String model,
                             String symbol,
                             double score,
                             double confidence,
                             double weight,
                             Instant generatedAt) {
}
