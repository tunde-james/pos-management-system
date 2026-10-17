package com.devtunde.posbackend.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DamageRequest(
        @NotNull @Min(1) Integer quantity, @Size(max = 500) String reason) {}
