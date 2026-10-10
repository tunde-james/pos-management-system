package com.devtunde.posbackend.product.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProductMasterResponse(
        UUID publicId,
        String name,
        String sku,
        String description,
        String brand,
        String image,
        CategoryRef category,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
