package com.devtunde.posbackend.inventory.api.dto;

import java.util.UUID;

public record LowStockItemResponse(UUID productPublicId, String name, int quantity, int lowStockThreshold) {}
