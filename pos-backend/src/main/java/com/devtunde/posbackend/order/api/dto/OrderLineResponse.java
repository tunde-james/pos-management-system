package com.devtunde.posbackend.order.api.dto;

import java.util.UUID;

public record OrderLineResponse(
        UUID productPublicId,
        String productName,
        long unitPriceKobo,
        int quantity,
        long lineTotalKobo,
        String displayUnitPrice,
        String displayLineTotal) {}
