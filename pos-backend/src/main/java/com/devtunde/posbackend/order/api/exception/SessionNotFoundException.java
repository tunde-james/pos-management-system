package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class SessionNotFoundException extends ProblemDetailException {

    public SessionNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Session not found.");
    }

    @Override
    public String errorCode() {
        return "session-not-found";
    }

    @Override
    public String title() {
        return "Session not found";
    }
}
