package com.devtunde.posbackend.product.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class UnknownCategoryException extends ProblemDetailException {

    public UnknownCategoryException() {
        super(HttpStatus.BAD_REQUEST, "No category exists with the given id.");
    }

    @Override
    public String errorCode() {
        return "unknown-category";
    }

    @Override
    public String title() {
        return "Category not found";
    }
}
