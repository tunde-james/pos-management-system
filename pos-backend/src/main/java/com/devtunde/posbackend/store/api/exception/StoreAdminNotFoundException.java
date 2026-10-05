package com.devtunde.posbackend.store.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class StoreAdminNotFoundException extends ProblemDetailException {

    public StoreAdminNotFoundException() {
        super(HttpStatus.BAD_REQUEST, "The assigned store admin does not exist.");
    }

    @Override
    public String errorCode() {
        return "store-admin-not-found";
    }

    @Override
    public String title() {
        return "Store admin not found";
    }
}
