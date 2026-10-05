package com.devtunde.posbackend.store.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class StoreAlreadyExistsException extends ProblemDetailException {

    public StoreAlreadyExistsException(String conflictingField) {
        super(HttpStatus.CONFLICT, "A store with this " + conflictingField + " already exists.");
    }

    @Override
    public String errorCode() {
        return "store-already-exists";
    }

    @Override
    public String title() {
        return "Store already exists";
    }
}
