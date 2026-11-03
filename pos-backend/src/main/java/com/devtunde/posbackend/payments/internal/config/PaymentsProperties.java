package com.devtunde.posbackend.payments.internal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments.providers")
public record PaymentsProperties(String currencyCode, Providers providers) {

    public record Providers(Paystack paystack, Monnify monnify) {}

    public record Paystack(String baseUrl, String secretKey, Duration connectTimeout, Duration readTimeout) {}

    public record Monnify(String apiKey, String secretKey, String contractCode) {}
}
