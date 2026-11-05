package com.devtunde.posbackend.order.api;

import java.util.List;
import java.util.UUID;

import com.devtunde.posbackend.order.api.dto.CheckoutRequest;
import com.devtunde.posbackend.order.api.dto.OrderResponse;
import com.devtunde.posbackend.order.api.dto.ReceiptResponse;
import com.devtunde.posbackend.order.api.dto.TrayItemResponse;

public interface OrderService {

    OrderResponse checkout(CheckoutRequest request);

    OrderResponse getOrder(UUID publicId);

    ReceiptResponse getReceipt(UUID publicId);

    OrderResponse verifyPayment(UUID publicId);

    OrderResponse revivePayment(UUID publicId);

    List<TrayItemResponse> pendingTray(String orderNumber);
}
