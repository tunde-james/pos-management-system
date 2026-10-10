package com.devtunde.posbackend.product.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class CategoryAlreadyExistsException extends ProblemDetailException {

    public CategoryAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "A category with this name already exists.");
    }

    @Override
    public String errorCode() {
        return "category-already-exists";
    }

    @Override
    public String title() {
        return "Category already exists";
    }
}
