package com.devtunde.posbackend.inventory.api.dto;

import java.util.UUID;

public record SaleLine(UUID productPublicId, int quantity) {}
