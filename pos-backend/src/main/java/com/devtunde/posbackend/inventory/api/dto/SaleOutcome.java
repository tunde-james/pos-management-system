package com.devtunde.posbackend.inventory.api.dto;

import java.util.List;

public record SaleOutcome(List<StockShortfall> shortfalls) {

    public boolean sold() {
        return shortfalls.isEmpty();
    }
}
