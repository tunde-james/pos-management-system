package com.devtunde.posbackend.customer.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class CustomerNotFoundException extends ProblemDetailException {

    public CustomerNotFoundException() {
        super(HttpStatus.NOT_FOUND, "No such customer.");
    }

    @Override
    public String errorCode() {
        return "customer-not-found";
    }

    @Override
    public String title() {
        return "Customer not found";
    }
}
