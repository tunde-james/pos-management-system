package com.devtunde.posbackend.order.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TrayItemResponse(
        UUID publicId,
        String orderNumber,
        String provider,
        long totalKobo,
        String displayTotal,
        LocalDateTime expiresAt) {}
