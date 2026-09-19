package com.devtunde.posbackend.auth.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryLoginAttemptServiceTests {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private Instant now = Instant.parse("2026-01-01T10:00:00Z");

    private InMemoryLoginAttemptService newService() {
        return new InMemoryLoginAttemptService(MAX_ATTEMPTS, LOCK_DURATION, () -> now);
    }

    private void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Test
    @DisplayName("account locks exactly at the max-attempts threshold, not before")
    void locksAtThreshold() {
        InMemoryLoginAttemptService service = newService();

        service.recordFailure("user@devtunde.com");
        service.recordFailure("user@devtunde.com");

        assertThat(service.isLocked("user@devtunde.com")).isFalse();

        service.recordFailure("user@devtunde.com");

        assertThat(service.isLocked("user@devtunde.com")).isTrue();
    }

    @Test
    @DisplayName("lock expires after the configured duration and the counter starts fresh")
    void lockExpiresAndCounterResets() {
        InMemoryLoginAttemptService service = newService();

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            service.recordFailure("user@devtunde.com");
        }

        assertThat(service.isLocked("user@devtunde.com")).isTrue();

        advance(LOCK_DURATION.plusSeconds(1));

        assertThat(service.isLocked("user@devtunde.com")).isFalse();

        // one failure after expiry is not enough to re-lock — the counter restarted
        service.recordFailure("user@devtunde.com");

        assertThat(service.isLocked("user@devtunde.com")).isFalse();
    }

    @Test
    @DisplayName("failures while locked do not extend the lock")
    void failuresWhileLockedAreIgnored() {
        InMemoryLoginAttemptService service = newService();

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            service.recordFailure("user@devtunde.com");
        }
        advance(Duration.ofMinutes(5));
        service.recordFailure("user@devtunde.com");
        service.recordFailure("user@devtunde.com");

        // lock still expires at the original 15-minute mark
        advance(Duration.ofMinutes(10).plusSeconds(1));

        assertThat(service.isLocked("user@devtunde.com")).isFalse();
    }

    @Test
    @DisplayName("clearAttempts resets the counter — a full set of failures is needed to lock again")
    void clearAttemptsResetsCounter() {
        InMemoryLoginAttemptService service = newService();

        service.recordFailure("user@devtunde.com");
        service.recordFailure("user@devtunde.com");
        service.clearAttempts("user@devtunde.com");

        service.recordFailure("user@devtunde.com");
        service.recordFailure("user@devtunde.com");

        assertThat(service.isLocked("user@devtunde.com")).isFalse();
    }

    @Test
    @DisplayName("attempts are tracked case-insensitively by email")
    void emailKeyIsCaseInsensitive() {
        InMemoryLoginAttemptService service = newService();

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            service.recordFailure("User@DevTunde.com");
        }

        assertThat(service.isLocked("user@devtunde.com")).isTrue();
    }
}
