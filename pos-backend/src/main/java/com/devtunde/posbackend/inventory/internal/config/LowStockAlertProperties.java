package com.devtunde.posbackend.inventory.internal.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.inventory.low-stock")
public record LowStockAlertProperties(
        @NotBlank String cron, @NotBlank String zone) {}
