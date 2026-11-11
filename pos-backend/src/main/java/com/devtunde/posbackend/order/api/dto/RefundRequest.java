package com.devtunde.posbackend.order.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record RefundRequest(
        @NotEmpty(message = "A refund needs at least one line.") @Valid
        List<Line> lines,

        String returnMethod) {

    public record Line(@NotNull UUID productPublicId, String disposition) {}
}
