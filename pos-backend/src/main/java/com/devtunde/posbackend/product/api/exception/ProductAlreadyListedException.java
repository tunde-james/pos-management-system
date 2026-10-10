package com.devtunde.posbackend.product.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class ProductAlreadyListedException extends ProblemDetailException {

    public ProductAlreadyListedException() {
        super(HttpStatus.CONFLICT, "This product is already listed in this store.");
    }

    @Override
    public String errorCode() {
        return "product-already-listed";
    }

    @Override
    public String title() {
        return "Product already listed";
    }
}
