package com.devtunde.posbackend.auth.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class InvalidCredentialsException extends ProblemDetailException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @Override
    public String title() {
        return "Invalid credentials";
    }

    @Override
    public String errorCode() {
        return "invalid-credentials";
    }
}
