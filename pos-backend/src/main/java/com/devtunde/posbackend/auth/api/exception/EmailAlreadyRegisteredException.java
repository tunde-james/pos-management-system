package com.devtunde.posbackend.auth.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class EmailAlreadyRegisteredException extends ProblemDetailException {

    public EmailAlreadyRegisteredException(String email) {
        super(HttpStatus.CONFLICT, "Email is already registered: " + email);
    }

    @Override
    public String title() {
        return "Email already registered";
    }

    @Override
    public String errorCode() {
        return "email-already-registered";
    }
}
