package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class CheckoutInvalidException extends ProblemDetailException {

    public CheckoutInvalidException(String detail) {
        super(HttpStatus.BAD_REQUEST, detail);
    }

    @Override
    public String errorCode() {
        return "checkout-invalid";
    }

    @Override
    public String title() {
        return "Invalid checkout";
    }
}
