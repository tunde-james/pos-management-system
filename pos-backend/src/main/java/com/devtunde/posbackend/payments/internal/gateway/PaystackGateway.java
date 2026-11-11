package com.devtunde.posbackend.payments.internal.gateway;

import org.springframework.web.client.RestClient;

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

    public ReverseData reverse(String providerReference) {

        ReverseResponse response = restClient
                .post()
                .uri("/transfer/{reference}/reverse", providerReference)
                .retrieve()
                .body(ReverseResponse.class);

        if (response == null || !response.status()) {
            throw new IllegalStateException("Paystack did not reverse the transfer");
        }

        return new ReverseData("REV-" + providerReference);
    }

    private record InitializeBody(String email, long amount, String currency, String reference) {}

    private record InitializeResponse(boolean status, InitializeData data) {}

    private record InitializeData(
            String reference,
            @JsonProperty("authorization_url") String authorizationUrl) {}

    private record VerifyResponse(boolean status, TransactionData data) {}

    private record TransactionData(String status, long amount) {}

    public record ReverseData(String providerRefundReference) {}

    private record ReverseResponse(boolean status, String message) {}
}
