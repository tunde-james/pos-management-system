package com.devtunde.posbackend.product.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

public record ListingPriceUpdateRequest(
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal mrp,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal sellingPrice,

        @DecimalMin(value = "0.00") @DecimalMax(value = "100.00") @Digits(integer = 3, fraction = 2)
        BigDecimal discountPercentage) {

    public boolean isEmpty() {
        return mrp == null && sellingPrice == null && discountPercentage == null;
    }
}
