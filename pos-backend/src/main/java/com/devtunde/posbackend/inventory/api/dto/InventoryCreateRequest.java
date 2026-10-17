package com.devtunde.posbackend.inventory.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryCreateRequest(
        @NotNull UUID productPublicId,
        @NotNull @Min(0) Integer quantity,
        @NotNull @Min(0) Integer lowStockThreshold) {}
