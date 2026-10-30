package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class OrderAccessDeniedException extends ProblemDetailException {

    public OrderAccessDeniedException(String detail) {
        super(HttpStatus.FORBIDDEN, detail);
    }

    @Override
    public String errorCode() {
        return "order-access-denied";
    }

    @Override
    public String title() {
        return "Access denied";
    }
}
