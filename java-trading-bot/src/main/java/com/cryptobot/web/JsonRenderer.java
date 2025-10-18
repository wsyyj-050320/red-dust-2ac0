package com.cryptobot.web;

import com.cryptobot.backtest.BacktestResult;
import com.cryptobot.data.Candlestick;
import com.cryptobot.model.PortfolioSnapshot;
import com.cryptobot.model.SignalSnapshot;
import com.cryptobot.model.TradeDecision;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Helper class to render JSON without third-party libraries.
 */
final class JsonRenderer {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private JsonRenderer() {
    }

    static String candles(Map<String, List<Candlestick>> data) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        boolean firstSymbol = true;
        for (Map.Entry<String, List<Candlestick>> entry : data.entrySet()) {
            if (!firstSymbol) {
                builder.append(',');
            }
            firstSymbol = false;
            builder.append('"').append(entry.getKey()).append('"').append(':').append('[');
            boolean first = true;
            for (Candlestick candle : entry.getValue()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('[')
                        .append('"').append(FORMATTER.format(candle.getOpenTime())).append('"').append(',')
                        .append(candle.getOpen()).append(',')
                        .append(candle.getHigh()).append(',')
                        .append(candle.getLow()).append(',')
                        .append(candle.getClose()).append(',')
                        .append(candle.getVolume())
                        .append(']');
            }
            builder.append(']');
        }
        builder.append('}');
        return builder.toString();
    }

    static String signals(List<SignalSnapshot> signals) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (SignalSnapshot snapshot : signals) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                    .append("\"model\":\"").append(snapshot.model()).append('\"').append(',')
                    .append("\"symbol\":\"").append(snapshot.symbol()).append('\"').append(',')
                    .append("\"score\":").append(snapshot.score()).append(',')
                    .append("\"confidence\":").append(snapshot.confidence()).append(',')
                    .append("\"weight\":").append(snapshot.weight()).append(',')
                    .append("\"generatedAt\":\"").append(FORMATTER.format(snapshot.generatedAt())).append('\"')
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    static String decision(TradeDecision decision) {
        return '{' +
                "\"symbol\":\"" + decision.symbol() + '\"' + ',' +
                "\"action\":\"" + decision.action() + '\"' + ',' +
                "\"confidence\":" + decision.confidence() + ',' +
                "\"positionSize\":" + decision.positionSize() + ',' +
                "\"stopLoss\":" + decision.stopLoss() + ',' +
                "\"takeProfit\":" + decision.takeProfit() + '}';
    }

    static String portfolio(List<PortfolioSnapshot> snapshots) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (PortfolioSnapshot snapshot : snapshots) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                    .append("\"symbol\":\"").append(snapshot.symbol()).append('\"').append(',')
                    .append("\"side\":\"").append(snapshot.side()).append('\"').append(',')
                    .append("\"contracts\":").append(snapshot.contracts()).append(',')
                    .append("\"entryPrice\":").append(snapshot.entryPrice()).append(',')
                    .append("\"unrealizedPnl\":").append(snapshot.unrealizedPnl()).append(',')
                    .append("\"timestamp\":\"").append(FORMATTER.format(snapshot.timestamp())).append('\"')
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    static String backtest(List<BacktestResult> results) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (BacktestResult result : results) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                    .append("\"symbol\":\"").append(result.symbol()).append('\"').append(',')
                    .append("\"cumulativeReturn\":").append(result.cumulativeReturn()).append(',')
                    .append("\"maxDrawdown\":").append(result.maxDrawdown()).append(',')
                    .append("\"sharpeRatio\":").append(result.sharpeRatio()).append(',')
                    .append("\"trades\":").append(result.trades())
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }
}
