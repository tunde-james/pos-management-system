package com.devtunde.posbackend.common.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.money")
public record MoneyProperties(String currencySymbol) {}
