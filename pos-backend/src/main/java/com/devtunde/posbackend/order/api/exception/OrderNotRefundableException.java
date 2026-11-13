package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class OrderNotRefundableException extends ProblemDetailException {

    public OrderNotRefundableException(String currentStatus) {
        super(HttpStatus.CONFLICT, "Only paid orders can be refunded (this one is " + currentStatus + ").");
    }

    @Override
    public String errorCode() {
        return "order-not-refundable";
    }

    @Override
    public String title() {
        return "Order not refundable";
    }
}
