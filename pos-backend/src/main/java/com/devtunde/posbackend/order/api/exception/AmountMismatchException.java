package com.devtunde.posbackend.order.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.devtunde.posbackend.common.api.ProblemDetailException;

public class AmountMismatchException extends ProblemDetailException {

    private final long expectedKobo;
    private final long actualKobo;

    public AmountMismatchException(long expectedKobo, long actualKobo) {
        super(
                HttpStatus.CONFLICT,
                "The gateway amount (" + actualKobo + " kobo) does not match the order (" + expectedKobo + " kobo).");
        this.expectedKobo = expectedKobo;
        this.actualKobo = actualKobo;
    }

    @Override
    public String errorCode() {
        return "amount-mismatch";
    }

    @Override
    public String title() {
        return "Payment amount mismatch";
    }

    @Override
    public ProblemDetail toProblemDetail(String errorBaseUrl) {
        ProblemDetail problemDetail = super.toProblemDetail(errorBaseUrl);
        problemDetail.setProperty("expectedKobo", expectedKobo);
        problemDetail.setProperty("actualKobo", actualKobo);
        return problemDetail;
    }
}
