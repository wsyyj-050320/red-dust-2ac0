package com.cryptobot.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple JSON array parser tailored to Binance kline responses. Avoids any
 * external dependencies so the project builds offline.
 */
public final class CandleParser {
    private CandleParser() {
    }

    public static List<Candlestick> parse(String json) {
        List<Candlestick> result = new ArrayList<>();
        int index = 0;
        while (index < json.length()) {
            int start = json.indexOf('[', index);
            if (start == -1) {
                break;
            }
            int end = findArrayEnd(json, start + 1);
            if (end == -1) {
                break;
            }
            String payload = json.substring(start + 1, end);
            String[] tokens = payload.split(",");
            if (tokens.length >= 6) {
                long openTime = Long.parseLong(strip(tokens[0]));
                double open = Double.parseDouble(strip(tokens[1]));
                double high = Double.parseDouble(strip(tokens[2]));
                double low = Double.parseDouble(strip(tokens[3]));
                double close = Double.parseDouble(strip(tokens[4]));
                double volume = Double.parseDouble(strip(tokens[5]));
                result.add(new Candlestick(Instant.ofEpochMilli(openTime), open, high, low, close, volume));
            }
            index = end + 1;
        }
        return result;
    }

    private static String strip(String raw) {
        String cleaned = raw.replace("\"", "").replace("\n", "").replace("\r", "").trim();
        if (cleaned.endsWith("]")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static int findArrayEnd(String json, int start) {
        int depth = 1;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
