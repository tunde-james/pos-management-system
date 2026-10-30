package com.devtunde.posbackend.order.api.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.devtunde.posbackend.common.api.ProblemDetailException;
import com.devtunde.posbackend.order.api.dto.ShortfallView;

public class CheckoutShortfallException extends ProblemDetailException {

    private final List<ShortfallView> shortfalls;

    public CheckoutShortfallException(List<ShortfallView> shortfalls) {
        super(HttpStatus.CONFLICT, "Some items do not have enough stock.");
        this.shortfalls = shortfalls;
    }

    @Override
    public String errorCode() {
        return "checkout-shortfall";
    }

    @Override
    public String title() {
        return "Insufficient stock";
    }

    @Override
    public ProblemDetail toProblemDetail(String errorBaseUrl) {
        ProblemDetail problemDetail = super.toProblemDetail(errorBaseUrl);
        problemDetail.setProperty("shortfalls", shortfalls);
        return problemDetail;
    }
}
