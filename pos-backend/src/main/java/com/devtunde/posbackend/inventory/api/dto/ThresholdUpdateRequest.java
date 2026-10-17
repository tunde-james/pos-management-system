package com.devtunde.posbackend.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ThresholdUpdateRequest(@NotNull @Min(0) Integer lowStockThreshold) {}
