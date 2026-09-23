package com.devtunde.posbackend.auth.internal.config;

import java.util.Arrays;
import java.util.function.Supplier;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Argon2TimingTest {

    // Mirror of app.security.argon2 in application.yml — update both together.
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KB = 32768;
    private static final int ITERATIONS = 3;

    private static final String PASSWORD = "Password123!";
    private static final int WARMUP = 3;
    private static final int RUNS = 10;

    @Test
    @DisplayName("Measures and logs Argon2 encode/verify latency on this machine (no assertions)")
    void measureEncodeAndVerifyLatency() {
        Argon2PasswordEncoder encoder =
                new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KB, ITERATIONS);

        String encoded = encoder.encode(PASSWORD);

        // Warmup (JIT + memory allocator)
        for (int i = 0; i < WARMUP; i++) {
            encoder.encode(PASSWORD);
            encoder.matches(PASSWORD, encoded);
        }

        long encodeMs = medianOf(() -> encoder.encode(PASSWORD));
        long verifyOkMs = medianOf(() -> encoder.matches(PASSWORD, encoded));
        long verifyBadMs = medianOf(() -> encoder.matches("WrongPassword123!", encoded));

        System.out.printf(
                "%n=== Argon2 timing (s=%d, h=%d, p=%d, m=%d KiB, t=%d) ===%n"
                        + "encode       : %d ms median over %d runs%n"
                        + "verify (ok)  : %d ms median over %d runs%n"
                        + "verify (bad) : %d ms median over %d runs%n",
                SALT_LENGTH,
                HASH_LENGTH,
                PARALLELISM,
                MEMORY_KB,
                ITERATIONS,
                encodeMs,
                RUNS,
                verifyOkMs,
                RUNS,
                verifyBadMs,
                RUNS);
    }

    private long medianOf(Supplier<Object> operation) {
        long[] samples = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            operation.get();
            samples[i] = (System.nanoTime() - start) / 1_000_000;
        }
        Arrays.sort(samples);
        return samples[RUNS / 2];
    }
}
