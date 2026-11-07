package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class OrderAlreadySettledException extends ProblemDetailException {

    public OrderAlreadySettledException(String status) {
        super(HttpStatus.CONFLICT, "This order is already " + status + " — it cannot be settled again.");
    }

    @Override
    public String errorCode() {
        return "order-already-settled";
    }

    @Override
    public String title() {
        return "Order already settled";
    }
}
