package com.devtunde.posbackend.auth.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class AccountLockedException extends ProblemDetailException {

    public AccountLockedException() {
        super(HttpStatus.LOCKED, "Account is locked due to too many failed login attempts. Try again later.");
    }

    @Override
    public String title() {
        return "Account locked";
    }

    @Override
    public String errorCode() {
        return "account-locked";
    }
}
