package com.devtunde.posbackend.product.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class ProductNotFoundException extends ProblemDetailException {

    public ProductNotFoundException() {
        super(HttpStatus.NOT_FOUND, "No Product exists with the given id.");
    }

    @Override
    public String errorCode() {
        return "product-not-found";
    }

    @Override
    public String title() {
        return "Prodct not found";
    }
}
