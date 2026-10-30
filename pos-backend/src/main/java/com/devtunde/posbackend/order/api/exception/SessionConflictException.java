package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class SessionConflictException extends ProblemDetailException {

    public SessionConflictException(String detail) {
        super(HttpStatus.CONFLICT, detail);
    }

    @Override
    public String errorCode() {
        return "session-conflict";
    }

    @Override
    public String title() {
        return "Session conflict";
    }
}
