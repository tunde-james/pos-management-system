package com.devtunde.posbackend.payments.api.dto;

public record GatewayInitiateResult(
        String providerReference, String authorizationUrl, String accountNumber, String bankName, String bankCode) {}
