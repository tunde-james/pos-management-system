package com.devtunde.posbackend.product.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 64) String sku,
        @Size(max = 2000) String description,
        @Size(max = 100) String brand,
        @Size(max = 500) String image,
        @NotNull UUID categoryPublicId,

        @NotNull @Positive @Digits(integer = 10, fraction = 2)
        BigDecimal mrp,

        @NotNull @Positive @Digits(integer = 10, fraction = 2)
        BigDecimal sellingPrice,

        @DecimalMin(value = "0.00") @DecimalMax(value = "100.00") @Digits(integer = 3, fraction = 2)
        BigDecimal discountPercentage) {}
