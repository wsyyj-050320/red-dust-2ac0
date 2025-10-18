package com.cryptobot.data;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable candlestick representation used across strategies and the web API.
 */
public final class Candlestick {
    private final Instant openTime;
    private final double open;
    private final double high;
    private final double low;
    private final double close;
    private final double volume;

    public Candlestick(Instant openTime, double open, double high, double low, double close, double volume) {
        this.openTime = openTime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public Instant getOpenTime() {
        return openTime;
    }

    public double getOpen() {
        return open;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getClose() {
        return close;
    }

    public double getVolume() {
        return volume;
    }

    @Override
    public int hashCode() {
        return Objects.hash(openTime, open, high, low, close, volume);
    }
}
