package com.devtunde.posbackend.employee.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class EmployeeAlreadyEmployedException extends ProblemDetailException {

    public EmployeeAlreadyEmployedException() {
        super(HttpStatus.CONFLICT, "This person already holds an active employment.");
    }

    @Override
    public String errorCode() {
        return "employee-already-employed";
    }

    @Override
    public String title() {
        return "Already employed";
    }
}
