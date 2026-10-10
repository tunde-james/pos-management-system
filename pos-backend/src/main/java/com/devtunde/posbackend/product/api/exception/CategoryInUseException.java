package com.devtunde.posbackend.product.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class CategoryInUseException extends ProblemDetailException {

    public CategoryInUseException() {
        super(HttpStatus.CONFLICT, "The category is still referenced by at least one product.");
    }

    @Override
    public String errorCode() {
        return "category-in-use";
    }

    @Override
    public String title() {
        return "Category in use";
    }
}
