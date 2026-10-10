package com.devtunde.posbackend.product.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class EmptyPatchException extends ProblemDetailException {

    public EmptyPatchException() {
        super(HttpStatus.BAD_REQUEST, "At least one field must be provided.");
    }

    @Override
    public String errorCode() {
        return "empty-patch";
    }

    @Override
    public String title() {
        return "Empty patch";
    }
}
