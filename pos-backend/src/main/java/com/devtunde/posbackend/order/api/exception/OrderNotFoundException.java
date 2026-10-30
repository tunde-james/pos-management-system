package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class OrderNotFoundException extends ProblemDetailException {

    public OrderNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Order not found.");
    }

    @Override
    public String errorCode() {
        return "order-not-found";
    }

    @Override
    public String title() {
        return "Order not found";
    }
}
