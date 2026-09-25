package com.devtunde.posbackend.auth.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class UserNotFoundException extends ProblemDetailException {

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "No user exists with the given id.");
    }

    @Override
    public String errorCode() {
        return "user-not-found";
    }

    @Override
    public String title() {
        return "User not found";
    }
}
