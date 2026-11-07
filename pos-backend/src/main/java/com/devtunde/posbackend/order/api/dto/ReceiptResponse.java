package com.devtunde.posbackend.order.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReceiptResponse(
        String receiptNo,
        String storeName,
        String address,
        String cashierName,
        String paymentMethod,
        LocalDateTime paidAt,
        String total,
        List<Line> lines) {

    public record Line(String name, int quantity, String amount) {}
}
