package com.devtunde.posbackend.inventory.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class InvalidStockAdjustmentException extends ProblemDetailException {

    public InvalidStockAdjustmentException() {
        this("The adjustment would take stock below zero.");
    }

    public InvalidStockAdjustmentException(String detail) {
        super(HttpStatus.BAD_REQUEST, detail);
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
