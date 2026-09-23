package com.devtunde.posbackend.auth.internal.application;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class InMemoryRateLimiter implements RateLimiter {

    private static final class Window {
        private long id;
        private int count;
    }

    private final int limit;
    private final long windowMillis;
    private final Supplier<Instant> now;
    private final Map<String, Window> windows = new HashMap<>();

    public InMemoryRateLimiter(int limit, Duration window, Supplier<Instant> now) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.now = now;
    }

    @Override
    public synchronized boolean tryAcquire(String key) {

        String normalized = key.trim().toLowerCase(Locale.ROOT);
        long windowId = now.get().toEpochMilli() / windowMillis;
        Window window = windows.get(normalized);

        if (window == null || window.id != windowId) {
            window = new Window();
            window.id = windowId;
            windows.put(normalized, window);
        }

        if (window.count >= limit) {
            return false;
        }

        window.count++;
        return true;
    }

    @Override
    public synchronized void purgeStaleWindows() {
        long currentWindowId = now.get().toEpochMilli() / windowMillis;

        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Window window = it.next().getValue();
            if (window.id != currentWindowId) {
                it.remove();
            }
        }
    }
}
