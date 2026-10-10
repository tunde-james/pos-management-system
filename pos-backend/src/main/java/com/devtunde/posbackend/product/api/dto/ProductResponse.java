package com.devtunde.posbackend.product.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID publicId,
        String name,
        String sku,
        String description,
        String brand,
        String image,
        CategoryRef category,
        BigDecimal mrp,
        BigDecimal sellingPrice,
        BigDecimal discountPercentage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
