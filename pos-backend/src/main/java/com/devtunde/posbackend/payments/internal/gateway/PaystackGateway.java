package com.devtunde.posbackend.payments.internal.gateway;

import java.util.List;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.devtunde.posbackend.payments.api.dto.GatewayReverseRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Paystack client — plain REST via RestClient
 * (Paystack has no official Java server SDK,
 * so there is no phantom dependency either).
 *
 * Endpoints (official docs):
 * POST /transaction/initialize -> hosted checkout url
 * GET /transaction/verify/{reference} -> status + amount in KOBO
 *
 * v1 is NGN-only: the catalog prices in naira, the money in kobo.
 */
public class PaystackGateway {

    private final RestClient restClient;
    private final String currency;

    public PaystackGateway(RestClient restClient, String currency) {
        this.restClient = restClient;
        this.currency = currency;
    }

    public record InitiateData(String providerReference, String authorizationUrl) {}

    public record VerifyData(String providerStatus, long amountKobo) {}

    public InitiateData initiate(String customerEmail, String orderNumber, long amountKobo) {

        InitializeResponse response = restClient
                .post()
                .uri("/transaction/initialize")
                .body(new InitializeBody(customerEmail, amountKobo, currency, orderNumber))
                .retrieve()
                .body(InitializeResponse.class);

        if (response == null || !response.status() || response.data() == null) {
            throw new IllegalStateException("Paystack did not confirm transaction initialization");
        }
        return new InitiateData(response.data().reference(), response.data().authorizationUrl());
    }

    public VerifyData verify(String providerReference) {

        VerifyResponse response = restClient
                .get()
                .uri("/transaction/verify/{reference}", providerReference)
                .retrieve()
                .body(VerifyResponse.class);

        if (response == null || !response.status() || response.data() == null) {
            throw new IllegalStateException("Paystack verification request was unsuccessful");
        }
        return new VerifyData(response.data().status(), response.data().amount());
    }

    public ReverseData reverse(GatewayReverseRequest request) {

        try {
            RefundResponse response = restClient
                    .post()
                    .uri("/refund")
                    .body(new RefundBody(
                            request.customerEmail(), request.amountKobo(),
                            request.providerReference(), request.refundKey()))
                    .retrieve()
                    .body(RefundResponse.class);

            if (response == null || !response.status() || response.data() == null) {
                throw new IllegalStateException("Paystack did not accept the refund");
            }

            return new ReverseData(response.data().reference());
        } catch (HttpClientErrorException.Conflict ex) {
            // A 409 means this exact refund (same reference) already
            // exists - fetch it instead of refunding the lines again.
            // The GET answer carries data as a list, even for one match.
            RefundListResponse existing = restClient
                    .get()
                    .uri("/refund?reference={reference}", request.refundKey())
                    .retrieve()
                    .body(RefundListResponse.class);

            if (existing == null || !existing.status() || existing.data() == null) {
                throw new IllegalStateException("Paystack could not return the existing refund");
            }

            String refundReference = null;
            String refundStatus = null;

            for (RefundData candidate : existing.data()) {
                if (request.refundKey().equals(candidate.reference())) {
                    refundReference = candidate.reference();
                    refundStatus = candidate.status();
                    break;
                }
            }

            if (refundReference == null) {
                throw new IllegalStateException("Paystack returned no refund for the idempotency reference");
            }

            if ("failed".equalsIgnoreCase(refundStatus)) {
                throw new IllegalStateException(
                        "Paystack refund failed (status: " + refundStatus + ") - the local refund is not completed");
            }

            return new ReverseData(refundReference);
        }
    }

    private record InitializeBody(String email, long amount, String currency, String reference) {}

    private record InitializeResponse(boolean status, InitializeData data) {}

    private record InitializeData(
            String reference,
            @JsonProperty("authorization_url") String authorizationUrl) {}

    private record VerifyResponse(boolean status, TransactionData data) {}

    private record TransactionData(String status, long amount) {}

    public record ReverseData(String providerRefundReference) {}

    private record RefundBody(String email, long amount, String transaction, String reference) {}

    private record RefundResponse(boolean status, RefundData data) {}

    private record RefundData(String reference, String status) {}

    private record RefundListResponse(boolean status, List<RefundData> data) {}
}
