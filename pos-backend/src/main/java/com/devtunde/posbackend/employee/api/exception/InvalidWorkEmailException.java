package com.devtunde.posbackend.employee.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class InvalidWorkEmailException extends ProblemDetailException {

    public InvalidWorkEmailException() {
        super(HttpStatus.BAD_REQUEST, "Employee emails must use the company domain.");
    }

    @Override
    public String errorCode() {
        return "invalid-work-email";
    }

    @Override
    public String title() {
        return "Invalid work email";
    }
}
