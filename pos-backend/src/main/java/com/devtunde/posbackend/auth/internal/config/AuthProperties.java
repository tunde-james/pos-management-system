package com.devtunde.posbackend.auth.internal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record AuthProperties(Jwt jwt, Argon2 argon2, Lockout lockout, RateLimit rateLimit, Refresh refresh) {

    public record Jwt(String secret, long expirationMinutes, String issuer) {}

    public record Argon2(int saltLength, int hashLength, int parallelism, int memoryKb, int iterations) {}

    public record Lockout(int maxAttempts, Duration lockDuration) {}

    public record RateLimit(int limit, Duration window) {}

    public record Refresh(Duration ttl) {}
}
