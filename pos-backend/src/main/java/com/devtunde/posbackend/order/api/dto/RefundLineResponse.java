package com.devtunde.posbackend.order.api.dto;

import java.util.UUID;

public record RefundLineResponse(UUID productPublicId, long lineTotalKobo, String disposition) {}
