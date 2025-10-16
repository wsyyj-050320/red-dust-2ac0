package com.cryptobot.data;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe storage of recent candles per trading symbol.
 */
public final class CandleStore {
    private final Map<String, Deque<Candlestick>> candles = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final int limit;

    public CandleStore(List<String> symbols, int limit) {
        this.limit = limit;
        for (String symbol : symbols) {
            candles.put(symbol, new ArrayDeque<>());
        }
    }

    public CandleStore(List<String> symbols) {
        this(symbols, 720);
    }

    public void upsert(String symbol, List<Candlestick> latest) {
        lock.writeLock().lock();
        try {
            Deque<Candlestick> deque = candles.computeIfAbsent(symbol, key -> new ArrayDeque<>());
            deque.clear();
            int start = Math.max(0, latest.size() - limit);
            for (int i = start; i < latest.size(); i++) {
                deque.addLast(latest.get(i));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Candlestick> latest(String symbol, int count) {
        lock.readLock().lock();
        try {
            Deque<Candlestick> deque = candles.get(symbol);
            if (deque == null || deque.isEmpty()) {
                return List.of();
            }
            int size = Math.min(count, deque.size());
            List<Candlestick> result = new ArrayList<>(size);
            Object[] array = deque.toArray();
            for (int i = array.length - size; i < array.length; i++) {
                result.add((Candlestick) array[i]);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, List<Candlestick>> snapshot() {
        lock.readLock().lock();
        try {
            Map<String, List<Candlestick>> copy = new HashMap<>();
            for (Map.Entry<String, Deque<Candlestick>> entry : candles.entrySet()) {
                copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return copy;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Instant newestTime(String symbol) {
        lock.readLock().lock();
        try {
            Deque<Candlestick> deque = candles.get(symbol);
            if (deque == null || deque.isEmpty()) {
                return Instant.EPOCH;
            }
            return deque.getLast().getOpenTime();
        } finally {
            lock.readLock().unlock();
        }
    }
}
