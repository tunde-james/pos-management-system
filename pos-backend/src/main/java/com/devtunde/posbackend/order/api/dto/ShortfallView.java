package com.devtunde.posbackend.order.api.dto;

import java.util.UUID;


public record ShortfallView(UUID productPublicId, int requested, int available) {}
