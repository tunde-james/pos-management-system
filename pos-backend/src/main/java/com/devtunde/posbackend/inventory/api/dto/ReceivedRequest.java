package com.devtunde.posbackend.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReceivedRequest(@NotNull @Min(1) Integer quantity) {}
