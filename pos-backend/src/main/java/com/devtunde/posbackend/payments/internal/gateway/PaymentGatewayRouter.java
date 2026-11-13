package com.devtunde.posbackend.payments.internal.gateway;

import com.devtunde.posbackend.payments.api.GatewayVerifyStatus;
import com.devtunde.posbackend.payments.api.PaymentGateway;
import com.devtunde.posbackend.payments.api.PaymentProvider;
import com.devtunde.posbackend.payments.api.dto.GatewayInitiateRequest;
import com.devtunde.posbackend.payments.api.dto.GatewayInitiateResult;
import com.devtunde.posbackend.payments.api.dto.GatewayReverseRequest;
import com.devtunde.posbackend.payments.api.dto.GatewayReverseResult;
import com.devtunde.posbackend.payments.api.dto.GatewayVerifyRequest;
import com.devtunde.posbackend.payments.api.dto.GatewayVerifyResult;
import com.devtunde.posbackend.payments.api.exception.PaymentProviderNotConfiguredException;

public class PaymentGatewayRouter implements PaymentGateway {

    private static final String WALK_IN_NAME = "Walk-in Customer";
    private static final String WALK_IN_EMAIL_FORMAT = "order-%s@example.com";

    private final PaystackGateway paystack;
    private final MonnifyGateway monnify;

    public PaymentGatewayRouter(PaystackGateway paystack, MonnifyGateway monnify) {
        this.paystack = paystack;
        this.monnify = monnify;
    }

    @Override
    public GatewayInitiateResult initiate(GatewayInitiateRequest request) {

        return switch (request.paymentProvider()) {
            case PAYSTACK -> paystackInitiate(request);
            case MONNIFY -> monnifyInitiate(request);
        };
    }

    @Override
    public GatewayVerifyResult verify(GatewayVerifyRequest request) {

        return switch (request.paymentProvider()) {
            case PAYSTACK -> paystackVerify(request);
            case MONNIFY -> monnifyVerify(request);
        };
    }

    @Override
    public GatewayReverseResult reverse(GatewayReverseRequest request) {

        String email = request.customerEmail() != null
                ? request.customerEmail()
                : WALK_IN_EMAIL_FORMAT.formatted(request.providerReference());

        GatewayReverseRequest withEmail = new GatewayReverseRequest(
                request.paymentProvider(),
                request.providerReference(),
                request.amountKobo(),
                email,
                request.refundKey(),
                request.destinationAccount(),
                request.destinationBankCode());

        return switch (request.paymentProvider()) {
            case PAYSTACK -> paystackReverse(withEmail);
            case MONNIFY -> monnifyReverse(withEmail);
        };
    }

    private GatewayInitiateResult paystackInitiate(GatewayInitiateRequest request) {

        if (paystack == null) {
            return notConfigured(request.paymentProvider());
        }

        String email = request.customerEmail() != null
                ? request.customerEmail()
                : WALK_IN_EMAIL_FORMAT.formatted(request.orderNumber());

        PaystackGateway.InitiateData data = paystack.initiate(email, request.orderNumber(), request.amountKobo());

        return new GatewayInitiateResult(data.providerReference(), data.authorizationUrl(), null, null, null, null);
    }

    private GatewayInitiateResult monnifyInitiate(GatewayInitiateRequest request) {

        if (monnify == null) {
            return notConfigured(request.paymentProvider());
        }

        String name = request.customerName() != null ? request.customerName() : WALK_IN_NAME;
        String email = request.customerEmail() != null
                ? request.customerEmail()
                : WALK_IN_EMAIL_FORMAT.formatted(request.orderNumber());

        MonnifyGateway.InitiateData data = monnify.initiate(name, email, request.orderNumber(), request.amountKobo());

        return new GatewayInitiateResult(
                data.providerReference(),
                null,
                data.accountNumber(),
                data.bankName(),
                data.bankCode(),
                data.accountExpiresAt());
    }

    private GatewayVerifyResult paystackVerify(GatewayVerifyRequest request) {

        if (paystack == null) {
            return notConfigured(request.paymentProvider());
        }

        PaystackGateway.VerifyData data = paystack.verify(request.providerReference());

        return new GatewayVerifyResult(statusOf(data.providerStatus()), data.amountKobo(), data.providerStatus());
    }

    private GatewayVerifyResult monnifyVerify(GatewayVerifyRequest request) {

        if (monnify == null) {
            return notConfigured(request.paymentProvider());
        }

        MonnifyGateway.VerifyData data = monnify.verify(request.providerReference());

        return new GatewayVerifyResult(
                monnifyStatusOf(data.providerStatus()), data.amountKobo(), data.providerStatus());
    }

    private <T> T notConfigured(PaymentProvider provider) {

        throw new PaymentProviderNotConfiguredException(provider);
    }

    private GatewayVerifyStatus statusOf(String providerStatus) {

        if ("success".equals(providerStatus)) {
            return GatewayVerifyStatus.SUCCESS;
        }

        if ("failed".equals(providerStatus)) {
            return GatewayVerifyStatus.FAILED;
        }

        // "pending" — and anything the gateway says that is not a yes.
        return GatewayVerifyStatus.PENDING;
    }

    static GatewayVerifyStatus monnifyStatusOf(String providerStatus) {

        String s = providerStatus == null ? "" : providerStatus.toLowerCase();

        if (s.equals("paid") || s.contains("success")) {
            return GatewayVerifyStatus.SUCCESS;
        }

        if (s.contains("fail")) {
            return GatewayVerifyStatus.FAILED;
        }

        return GatewayVerifyStatus.PENDING;
    }

    private GatewayReverseResult paystackReverse(GatewayReverseRequest request) {

        if (paystack == null) {
            return notConfigured(request.paymentProvider());
        }

        PaystackGateway.ReverseData data = paystack.reverse(request);

        return new GatewayReverseResult(data.providerRefundReference());
    }

    private GatewayReverseResult monnifyReverse(GatewayReverseRequest request) {

        if (monnify == null) {
            return notConfigured(request.paymentProvider());
        }

        MonnifyGateway.ReverseData data = monnify.reverse(request);

        return new GatewayReverseResult(data.providerRefundReference());
    }
}
