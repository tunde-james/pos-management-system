package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class RefundInvalidException extends ProblemDetailException {

    public RefundInvalidException(String detail) {
        super(HttpStatus.BAD_REQUEST, detail);
    }

    @Override
    public String errorCode() {
        return "refund-invalid";
    }

    @Override
    public String title() {
        return "Refund invalid";
    }
}
