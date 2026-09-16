package com.devtunde.posbackend.common.internal;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.devtunde.posbackend.common.api.FieldErrorDetail;
import com.devtunde.posbackend.common.api.ProblemDetailException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorProperties errorProperties;

    public GlobalExceptionHandler(ErrorProperties errorProperties) {
        this.errorProperties = errorProperties;
    }

    @ExceptionHandler(ProblemDetailException.class)
    public ResponseEntity<ProblemDetail> handleProblemDetail(ProblemDetailException ex) {

        ProblemDetail problemDetail = ex.toProblemDetail(errorProperties.baseUrl());
        return ResponseEntity.status(ex.status()).body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {

        List<FieldErrorDetail> filters = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problemDetail.setType(URI.create(errorProperties.baseUrl() + "/validation-error"));
        problemDetail.setTitle("Validation failed");
        problemDetail.setProperty("errors", filters);

        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoResourceFoundException ex) {

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The requested resource does not exist.");
        problemDetail.setType(URI.create(errorProperties.baseUrl() + "/resource-not-found"));
        problemDetail.setTitle("Resource not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing or malformed.");
        problemDetail.setType(URI.create(errorProperties.baseUrl() + "/malformed-body"));
        problemDetail.setTitle("Malformed request body");
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {

        // log server-side, never leak internals to the client
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"));
    }
}
