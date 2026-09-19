package com.devtunde.posbackend.common.api;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public abstract class ProblemDetailException extends RuntimeException {

    private final HttpStatus status;

    protected ProblemDetailException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public abstract String errorCode();

    public abstract String title();

    public Map<String, String> headers() {
        return Map.of();
    }

    public ProblemDetail toProblemDetail(String errorBaseUrl) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, getMessage());
        problemDetail.setType(URI.create(errorBaseUrl + "/" + errorCode()));
        problemDetail.setTitle(title());
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }
}
