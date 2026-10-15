package com.devtunde.posbackend.inventory.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class InventoryNotFoundException extends ProblemDetailException {

    public InventoryNotFoundException() {
        super(HttpStatus.NOT_FOUND, "No stock is tracked for this product in this store.");
    }

    @Override
    public String errorCode() {
        return "inventory-not-found";
    }

    @Override
    public String title() {
        return "Inventory not found";
    }
}
