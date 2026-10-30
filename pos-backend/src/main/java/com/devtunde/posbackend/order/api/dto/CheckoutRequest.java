package com.devtunde.posbackend.order.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotEmpty(message = "Basket must contain at least one line.") @Valid
        List<Line> lines,

        @NotBlank(message = "Payment method is required.") @Size(max = 10)
        String paymentMethod,

        @Size(max = 255) String paymentReference,

        @Size(max = 20) String customerPhone) {

    public record Line(
            @NotNull(message = "Product is required.") UUID productPublicId,

            @Min(value = 1, message = "Quantity must be at least 1.")
            @Max(value = 999, message = "Quantity must be at most 999.")
            int quantity) {}
}
