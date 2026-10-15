package com.devtunde.posbackend.common.api.email;

import java.util.List;

public record LowStockAlert(List<Item> low, List<Item> out) {

    public record Item(String productName, int quantity, int lowStockThreshold) {}
}
