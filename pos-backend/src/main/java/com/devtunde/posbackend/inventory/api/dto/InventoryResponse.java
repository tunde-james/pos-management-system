package com.devtunde.posbackend.inventory.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryResponse(
        UUID storePublicId, UUID productPublicId, int quantity, int lowStockThreshold, LocalDateTime updatedAt) {}
