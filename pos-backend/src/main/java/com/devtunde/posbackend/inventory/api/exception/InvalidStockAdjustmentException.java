package com.devtunde.posbackend.inventory.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class InvalidStockAdjustmentException extends ProblemDetailException {

    public InvalidStockAdjustmentException() {
        super(HttpStatus.BAD_REQUEST, "The adjustment would take stock below zero.");
    }

    @Override
    public String errorCode() {
        return "invalid-stock-adjustment";
    }

    @Override
    public String title() {
        return "Invalid stock adjustment";
    }
}
