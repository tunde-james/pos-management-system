package com.devtunde.posbackend.employee.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class EmployeeNotFoundException extends ProblemDetailException {

    public EmployeeNotFoundException() {
        super(HttpStatus.NOT_FOUND, "No such employment at this store.");
    }

    @Override
    public String errorCode() {
        return "employee-not-found";
    }

    @Override
    public String title() {
        return "Employee not found";
    }
}
