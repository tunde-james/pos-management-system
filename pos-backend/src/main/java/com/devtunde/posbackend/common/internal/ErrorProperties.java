package com.devtunde.posbackend.common.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.errors")
public record ErrorProperties(String baseUrl) {}
