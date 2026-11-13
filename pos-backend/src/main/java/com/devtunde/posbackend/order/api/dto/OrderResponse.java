package com.devtunde.posbackend.order.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        UUID publicId,
        String status,
        String statusReason,
        String orderNumber,
        UUID sessionPublicId,
        UUID operatorPublicId,
        UUID storeId,
        UUID customerPublicId,
        String paymentMethod,
        String paymentReference,
        String provider,
        String providerReference,
        long totalKobo,
        String displayTotal,
        LocalDateTime paidAt,
        LocalDateTime expiresAt,
        PaystackBlock paystack,
        MonnifyBlock monnify,
        List<OrderLineResponse> lines,
        List<RefundView> refunds) {

    public record PaystackBlock(String authorizationUrl) {}

    public record MonnifyBlock(String accountNumber, String bankName, String bankCode) {}

    public record RefundView(
            long totalKobo, String displayTotal, String returnMethod, String providerRefundReference, LocalDateTime refundedAt) {}
}
