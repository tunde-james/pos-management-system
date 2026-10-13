package com.devtunde.posbackend.store.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class InvalidStoreHoursException extends ProblemDetailException {

    public InvalidStoreHoursException() {
        super(HttpStatus.BAD_REQUEST, "Close time must be after open time.");
    }

    @Override
    public String errorCode() {
        return "invalid-store-hours";
    }

    @Override
    public String title() {
        return "Invalid store hours";
    }
}
