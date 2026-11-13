package com.devtunde.posbackend.payments.api.dto;

import com.devtunde.posbackend.payments.api.PaymentProvider;

public record GatewayReverseRequest(
        PaymentProvider paymentProvider,
        String providerReference,
        long amountKobo,
        String customerEmail,
        String refundKey,
        String destinationAccount,
        String destinationBankCode) {}
