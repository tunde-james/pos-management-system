package com.devtunde.posbackend.auth.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTests {

    private static final int LIMIT = 3;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private Instant now = Instant.parse("2026-01-01T10:00:00Z");

    private InMemoryRateLimiter newLimiter() {
        return new InMemoryRateLimiter(LIMIT, WINDOW, () -> now);
    }

    private void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Test
    @DisplayName("allows up to the limit, then blocks")
    void allowsUpToLimitThenBlocks() {
        InMemoryRateLimiter limiter = newLimiter();

        for (int i = 0; i < LIMIT; i++) {

            assertThat(limiter.tryAcquire("192.168.1.10")).isTrue();
        }

        assertThat(limiter.tryAcquire("192.168.1.10")).isFalse();
    }

    @Test
    @DisplayName("a new window starts the count from zero")
    void newWindowResetsTheCount() {
        InMemoryRateLimiter limiter = newLimiter();

        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire("192.168.1.10");
        }

        assertThat(limiter.tryAcquire("192.168.1.10")).isFalse();

        advance(WINDOW.plusSeconds(1));

        for (int i = 0; i < LIMIT; i++) {

            assertThat(limiter.tryAcquire("192.168.1.10")).isTrue();
        }

        assertThat(limiter.tryAcquire("192.168.1.10")).isFalse();
    }

    @Test
    @DisplayName("each key has its own window")
    void eachKeyHasItsOwnWindow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(1, WINDOW, () -> now);

        assertThat(limiter.tryAcquire("ip-one")).isTrue();
        assertThat(limiter.tryAcquire("ip-one")).isFalse();

        assertThat(limiter.tryAcquire("ip-two")).isTrue();
    }
}
