package com.devtunde.posbackend.payments.api.dto;

import com.devtunde.posbackend.payments.api.GatewayVerifyStatus;

public record GatewayVerifyResult(GatewayVerifyStatus status, long amountKobo, String providerMessage) {}
