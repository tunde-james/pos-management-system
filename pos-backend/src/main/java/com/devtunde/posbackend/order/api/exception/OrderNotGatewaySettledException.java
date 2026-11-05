package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class OrderNotGatewaySettledException extends ProblemDetailException {

    public OrderNotGatewaySettledException() {
        super(HttpStatus.CONFLICT, "This order was settled at the counter — it has no payment gateway flow.");
    }

    @Override
    public String errorCode() {
        return "order-not-gateway-settled";
    }

    @Override
    public String title() {
        return "Order not settled via a payment gateway";
    }
}
