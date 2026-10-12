package com.devtunde.posbackend.store.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class StoreClosureNotPermittedException extends ProblemDetailException {

    public StoreClosureNotPermittedException() {
        super(
                HttpStatus.BAD_REQUEST,
                "Stores are closed with DELETE /api/v1/stores/{id}; status cannot be set to CLOSED via update.");
    }

    @Override
    public String errorCode() {
        return "store-closure-not-permitted";
    }

    @Override
    public String title() {
        return "Store closure not permitted";
    }
}
