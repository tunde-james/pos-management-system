package com.devtunde.posbackend.inventory.api.dto;

import java.time.LocalDateTime;

import com.devtunde.posbackend.inventory.api.MovementType;

public record MovementResponse(
        MovementType type,
        int quantityDelta,
        int beforeQuantity,
        int afterQuantity,
        String reason,
        LocalDateTime createdAt) {}
