package com.devtunde.posbackend.order.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RefundResponse(
        UUID publicId,
        UUID orderPublicId,
        long totalKobo,
        String displayTotal,
        List<RefundLineResponse> lines,
        String returnMethod,
        String providerRefundReference,
        LocalDateTime refundedAt) {}
