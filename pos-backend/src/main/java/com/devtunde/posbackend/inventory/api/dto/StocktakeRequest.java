package com.devtunde.posbackend.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StocktakeRequest(@NotNull @Min(0) Integer quantity) {}
