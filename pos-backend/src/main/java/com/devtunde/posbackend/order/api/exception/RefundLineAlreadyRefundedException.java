package com.devtunde.posbackend.order.api.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class RefundLineAlreadyRefundedException extends ProblemDetailException {

    public RefundLineAlreadyRefundedException(UUID productPublicId) {
        super(HttpStatus.CONFLICT, "Line " + productPublicId + " is already refunded.");
    }

    @Override
    public String errorCode() {
        return "refund-line-already-refunded";
    }

    @Override
    public String title() {
        return "Line already refunded";
    }
}
