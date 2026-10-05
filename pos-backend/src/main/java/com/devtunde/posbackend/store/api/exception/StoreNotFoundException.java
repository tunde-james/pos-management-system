package com.devtunde.posbackend.store.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class StoreNotFoundException extends ProblemDetailException {

    public StoreNotFoundException() {
        super(HttpStatus.NOT_FOUND, "No store exists with the given id.");
    }

    @Override
    public String errorCode() {
        return "store-not-found";
    }

    @Override
    public String title() {
        return "Store not found";
    }
}
