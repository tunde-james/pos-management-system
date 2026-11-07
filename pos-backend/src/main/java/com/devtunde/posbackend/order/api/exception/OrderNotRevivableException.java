package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class OrderNotRevivableException extends ProblemDetailException {

    public OrderNotRevivableException(String detail) {
        super(HttpStatus.CONFLICT, detail);
    }

    @Override
    public String errorCode() {
        return "order-not-revivable";
    }

    @Override
    public String title() {
        return "Order not revivable";
    }
}
