package com.devtunde.posbackend.product.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategoryResponse(UUID publicId, String name, LocalDateTime createdAt, LocalDateTime updatedAt) {}
