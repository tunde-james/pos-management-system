package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class TransferAccountExpiredException extends ProblemDetailException {

    public TransferAccountExpiredException() {
        super(HttpStatus.CONFLICT, "The bank account on this slip has expired — re-ring the order.");
    }

    @Override
    public String errorCode() {
        return "transfer-account-expired";
    }

    @Override
    public String title() {
        return "Transfer account expired";
    }
}
