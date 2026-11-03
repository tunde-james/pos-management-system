package com.devtunde.posbackend.payments.api.dto;

import com.devtunde.posbackend.payments.api.PaymentProvider;

public record GatewayVerifyRequest(
        PaymentProvider paymentProvider, String providerReference, long expectedAmountKobo) {}
