package com.devtunde.posbackend.inventory.api.dto;

import java.util.List;

public record LowStockResponse(List<LowStockItemResponse> low, List<LowStockItemResponse> out) {}
