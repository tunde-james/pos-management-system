package com.devtunde.posbackend.order.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.devtunde.posbackend.payments.api.GatewayVerifyStatus;
import com.devtunde.posbackend.payments.api.PaymentGateway;
import com.devtunde.posbackend.payments.api.PaymentProvider;
import com.devtunde.posbackend.payments.api.dto.GatewayInitiateRequest;
import com.devtunde.posbackend.payments.api.dto.GatewayInitiateResult;
import com.devtunde.posbackend.payments.api.dto.GatewayVerifyRequest;
import com.devtunde.posbackend.payments.api.dto.GatewayVerifyResult;

@Component
@Profile("test")
public class FakePaymentGateway implements PaymentGateway {

    private final Map<String, Long> initiatedAmounts = new ConcurrentHashMap<>();

    private GatewayVerifyStatus nextVerifyStatus = GatewayVerifyStatus.PENDING;
    private Long nextVerifyAmountKobo = null;
    private long verifyCallCount = 0;

    public void reset() {
        this.nextVerifyStatus = GatewayVerifyStatus.PENDING;
        this.nextVerifyAmountKobo = null;
        this.verifyCallCount = 0;
    }

    public void setNextVerify(GatewayVerifyStatus status, Long amountKobo) {
        this.nextVerifyStatus = status;
        this.nextVerifyAmountKobo = amountKobo;
    }

    public long verifyCallCount() {
        return verifyCallCount;
    }

    @Override
    public GatewayInitiateResult initiate(GatewayInitiateRequest request) {

        String reference = "FAKE-" + request.paymentProvider().name() + "-" + request.orderNumber();
        initiatedAmounts.put(reference, request.amountKobo());

        if (request.paymentProvider() == PaymentProvider.PAYSTACK) {
            return new GatewayInitiateResult(
                    reference, "https://fake.paystack.test/checkout/" + reference, null, null, null);
        }
        return new GatewayInitiateResult(reference, null, "0123456789", "Fake Bank", "001");
    }

    @Override
    public GatewayVerifyResult verify(GatewayVerifyRequest request) {
        
        verifyCallCount++;

        if (nextVerifyStatus == GatewayVerifyStatus.PENDING) {
            return new GatewayVerifyResult(GatewayVerifyStatus.PENDING, 0L, "nothing arrived yet");
        }
        if (nextVerifyStatus == GatewayVerifyStatus.FAILED) {
            return new GatewayVerifyResult(GatewayVerifyStatus.FAILED, 0L, "gateway declined");
        }
        long amount = nextVerifyAmountKobo != null
                ? nextVerifyAmountKobo
                : initiatedAmounts.getOrDefault(request.providerReference(), 0L);
        return new GatewayVerifyResult(GatewayVerifyStatus.SUCCESS, amount, "settled");
    }
}
