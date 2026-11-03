package com.devtunde.posbackend.payments.api.dto;

import com.devtunde.posbackend.payments.api.PaymentProvider;

public record GatewayInitiateRequest(
        PaymentProvider paymentProvider,
        String orderNumber,
        long amountKobo,
        String customerEmail,
        String customerName) {}
