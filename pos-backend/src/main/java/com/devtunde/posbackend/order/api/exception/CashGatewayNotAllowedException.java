package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class CashGatewayNotAllowedException extends ProblemDetailException {

    public CashGatewayNotAllowedException() {
        super(HttpStatus.BAD_REQUEST, "Cash settles at the counter — CASH checkout takes no payment provider.");
    }

    @Override
    public String errorCode() {
        return "cash-gateway-not-allowed";
    }

    @Override
    public String title() {
        return "Cash cannot use a payment provider";
    }
}
