package com.devtunde.posbackend.inventory.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class InventoryAlreadyExistsException extends ProblemDetailException {

    public InventoryAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "This store already tracks stock for this product.");
    }

    @Override
    public String errorCode() {
        return "inventory-already-exists";
    }

    @Override
    public String title() {
        return "Inventory already exists";
    }
}
