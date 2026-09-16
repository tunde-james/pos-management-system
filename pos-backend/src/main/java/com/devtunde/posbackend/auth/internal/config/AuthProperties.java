package com.devtunde.posbackend.auth.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record AuthProperties(Jwt jwt, Argon2 argon2) {

    public record Jwt(String secret, long expirationMinutes, String issuer) {}

    public record Argon2(int saltLength, int hashLength, int parallelism, int memoryKb, int iterations) {}
}
