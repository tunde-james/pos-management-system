package com.devtunde.posbackend.auth.internal.application;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class InMemoryLoginAttemptService implements LoginAttemptService {

    private static final class Attempt {
        private int count;
        private Instant lockedUntil;
        private Instant lastFailure;
    }

    private final int maxAttempts;
    private final Duration lockDuration;
    private final Supplier<Instant> now;
    private final Map<String, Attempt> attempts = new HashMap<>();

    public InMemoryLoginAttemptService(int maxAttempts, Duration lockDuration, Supplier<Instant> now) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
        this.now = now;
    }

    @Override
    public synchronized boolean isLocked(String email) {
        Attempt attempt = attempts.get(key(email));
        return attempt != null && attempt.lockedUntil != null && now.get().isBefore(attempt.lockedUntil);
    }

    @Override
    public synchronized void recordFailure(String email) {
        Attempt attempt = attempts.get(key(email));

        if (attempt == null) {
            attempt = new Attempt();
            attempts.put(key(email), attempt);
        }

        Instant time = now.get();

        if (attempt.lockedUntil == null
                && attempt.lastFailure != null
                && time.isAfter(attempt.lastFailure.plus(lockDuration))) {
            attempt.count = 0;
        }

        if (attempt.lockedUntil != null) {
            if (time.isBefore(attempt.lockedUntil)) {
                return;
            }
            attempt.count = 0;
            attempt.lockedUntil = null;
        }

        attempt.count++;
        attempt.lastFailure = time;

        if (attempt.count >= maxAttempts) {
            attempt.lockedUntil = time.plus(lockDuration);
        }
    }

    @Override
    public synchronized void clearAttempts(String email) {
        attempts.remove(key(email));
    }

    @Override
    public synchronized void purgeStale() {
        Instant time = now.get();

        Iterator<Map.Entry<String, Attempt>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Attempt attempt = it.next().getValue();

            if (time.isAfter(attempt.lastFailure.plus(lockDuration))) {
                it.remove();
            }
        }
    }

    private String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
