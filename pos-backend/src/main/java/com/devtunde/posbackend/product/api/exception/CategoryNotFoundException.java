package com.devtunde.posbackend.product.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class CategoryNotFoundException extends ProblemDetailException {

    public CategoryNotFoundException() {
        super(HttpStatus.NOT_FOUND, "No category exists with the given id.");
    }

    @Override
    public String errorCode() {
        return "category-not-found";
    }

    @Override
    public String title() {
        return "Category not found";
    }
}
