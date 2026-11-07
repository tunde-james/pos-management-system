package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class OrderNotPaidException extends ProblemDetailException {

    public OrderNotPaidException() {
        super(HttpStatus.CONFLICT, "This order has not been paid yet — no customer receipt exists.");
    }

    @Override
    public String errorCode() {
        return "order-not-paid";
    }

    @Override
    public String title() {
        return "Order not paid";
    }
}
