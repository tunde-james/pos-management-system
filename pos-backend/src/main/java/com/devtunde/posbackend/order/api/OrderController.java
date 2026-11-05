package com.devtunde.posbackend.order.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.posbackend.order.api.dto.CheckoutRequest;
import com.devtunde.posbackend.order.api.dto.OrderResponse;
import com.devtunde.posbackend.order.api.dto.ReceiptResponse;
import com.devtunde.posbackend.order.api.dto.TrayItemResponse;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@Valid @RequestBody CheckoutRequest request) {

        OrderResponse response = orderService.checkout(request);

        HttpStatus status = response.paidAt() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{publicId}")
    public OrderResponse read(@PathVariable UUID publicId) {

        return orderService.getOrder(publicId);
    }

    @GetMapping("/{publicId}/receipt")
    public ReceiptResponse receipt(@PathVariable UUID publicId) {

        return orderService.getReceipt(publicId);
    }

    @PostMapping("/{publicId}/payment/verify")
    public OrderResponse verify(@PathVariable UUID publicId) {

        return orderService.verifyPayment(publicId);
    }

    @PostMapping("/{publicId}/payment/revive")
    public OrderResponse revive(@PathVariable UUID publicId) {

        return orderService.revivePayment(publicId);
    }

    @GetMapping("/pending")
    public List<TrayItemResponse> pending(@RequestParam(required = false) String number) {

        return orderService.pendingTray(number);
    }
}
