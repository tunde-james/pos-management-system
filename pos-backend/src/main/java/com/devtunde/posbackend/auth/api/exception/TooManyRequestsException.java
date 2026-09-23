package com.devtunde.posbackend.auth.api.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class TooManyRequestsException extends ProblemDetailException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Slow down and try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public String title() {
        return "Too many requests";
    }

    @Override
    public String errorCode() {
        return "too-many-requests";
    }

    @Override
    public Map<String, String> headers() {
        return Map.of("Retry-After", String.valueOf(retryAfterSeconds));
    }
}
