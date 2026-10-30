package com.devtunde.posbackend.order.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        UUID publicId,
        String status,
        UUID sessionPublicId,
        UUID operatorPublicId,
        UUID storeId,
        UUID customerPublicId,
        String paymentMethod,
        String paymentReference,
        long totalKobo,
        String displayTotal,
        LocalDateTime paidAt,
        List<OrderLineResponse> lines) {}
