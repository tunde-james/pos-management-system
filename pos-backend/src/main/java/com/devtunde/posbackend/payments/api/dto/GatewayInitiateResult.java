package com.devtunde.posbackend.payments.api.dto;

import java.time.LocalDateTime;

public record GatewayInitiateResult(
        String providerReference,
        String authorizationUrl,
        String accountNumber,
        String bankName,
        String bankCode,
        LocalDateTime accountExpiresAt) {}
