package com.devtunde.posbackend.inventory.api.dto;

import java.util.UUID;

public record ReturnLine(UUID productPublicId, int quantity) {}
