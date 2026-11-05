package com.devtunde.posbackend.payments.api.exception;

import org.springframework.http.HttpStatus;

import com.devtunde.posbackend.common.api.ProblemDetailException;
import com.devtunde.posbackend.payments.api.PaymentProvider;

public class PaymentProviderNotConfiguredException extends ProblemDetailException {

    public PaymentProviderNotConfiguredException(PaymentProvider provider) {
        super(HttpStatus.BAD_REQUEST, "Payment provider " + provider + " is not configured on this server.");
    }

    @Override
    public String errorCode() {
        return "payment-provider-not-configured";
    }

    @Override
    public String title() {
        return "Payment provider not configured";
    }
}
