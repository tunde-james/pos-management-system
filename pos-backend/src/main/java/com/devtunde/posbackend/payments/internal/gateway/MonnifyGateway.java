package com.devtunde.posbackend.payments.internal.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.devtunde.posbackend.payments.api.PaymentProvider;
import com.devtunde.posbackend.payments.api.dto.GatewayReverseRequest;
import com.devtunde.posbackend.payments.api.exception.PaymentProviderNotConfiguredException;
import com.monnify.models.MonnifyBaseResponse;
import com.monnify.models.refund.RefundRequest;
import com.monnify.models.refund.RefundResponse;
import com.monnify.models.transaction.BankTransferRequest;
import com.monnify.models.transaction.BankTransferResponse;
import com.monnify.models.transaction.PaymentMethod;
import com.monnify.models.transaction.TransactionRequest;
import com.monnify.models.transaction.TransactionResponse;
import com.monnify.models.transaction.TransactionStatusResponse;
import com.monnify.services.refund.RefundService;
import com.monnify.services.transaction.TransactionService;

/**
 * Monnify client on the OFFICIAL Java SDK
 * (com.monnify:monnify-java-sdk).
 * Unlike Paystack, Monnify publishes a Java
 * client — so no hand-rolled
 * HTTP here. The SDK handles auth (api key +
 * secret -> access token) and
 * picks the host itself: MK_TEST_ keys hit
 * sandbox.monnify.com,
 * MK_PROD_ keys hit api.monnify.com.
 *
 * Money: Monnify speaks NAIRA (BigDecimal major
 * units); the system keeps
 * KOBO. Conversion happens exactly once, at this
 * boundary.
 */
public class MonnifyGateway {

    private final TransactionService transactions;
    private final RefundService refunds;
    private final String currency;
    private final String contractCode;

    public MonnifyGateway(
            TransactionService transactions, RefundService refunds, String currency, String contractCode) {
        this.transactions = transactions;
        this.refunds = refunds;
        this.currency = currency;
        this.contractCode = contractCode;
    }

    public record InitiateData(
            String providerReference,
            String accountNumber,
            String bankName,
            String bankCode,
            LocalDateTime accountExpiresAt) {}

    public record VerifyData(String providerStatus, long amountKobo) {}

    public record ReverseData(String providerRefundReference) {}

    /**
     * Two steps, both merchant-side:
     *  1. init-transaction — open a payment for
     * this order. OUR order number is the paymentReference;
     * Monnify keys everything on it.
     *
     *  2. init-payment (bank transfer) — returns
     * the reserved account the customer transfers into.
     * bankCode stays null: Monnify picks its default partner bank.
     */
    public InitiateData initiate(String customerName, String customerEmail, String orderNumber, long amountKobo) {

        if (contractCode == null || contractCode.isBlank()) {
            throw new PaymentProviderNotConfiguredException(PaymentProvider.MONNIFY);
        }

        TransactionRequest tx = new TransactionRequest();
        tx.setAmount(naira(amountKobo));
        tx.setCustomerName(customerName);
        tx.setCustomerEmail(customerEmail);
        tx.setPaymentReference(orderNumber);
        tx.setPaymentDescription("POS order " + orderNumber);
        tx.setCurrencyCode(currency);
        tx.setContractCode(contractCode);

        tx.setPaymentMethods(List.of(PaymentMethod.ACCOUNT_TRANSFER));

        MonnifyBaseResponse<TransactionResponse> txResponse = transactions.initializeTransaction(tx);
        requireSuccess(txResponse, "transaction initialization");
        String transactionReference = txResponse.getResponseBody().getTransactionReference();

        BankTransferRequest transfer = new BankTransferRequest();

        transfer.setTransactionReference(transactionReference);

        MonnifyBaseResponse<BankTransferResponse> bankResponse = transactions.payWithBankTransfer(transfer);
        requireSuccess(bankResponse, "bank transfer initiation");
        BankTransferResponse bank = bankResponse.getResponseBody();

        // The order number stays the verification handle for the slip's life.
        return new InitiateData(
                orderNumber, bank.getAccountNumber(), bank.getBankName(), bank.getBankCode(), accountExpiryOf(bank));
    }

    /** Ask Monnify what happened to the payment
     * keyed on our order number. */
    public VerifyData verify(String providerReference) {

        MonnifyBaseResponse<TransactionStatusResponse> response =
                transactions.getStatusByReference(providerReference, null);
        requireSuccess(response, "transaction query");
        TransactionStatusResponse status = response.getResponseBody();

        return new VerifyData(status.getPaymentStatus(), kobo(status.getAmountPaid()));
    }

    public ReverseData reverse(GatewayReverseRequest request) {

        MonnifyBaseResponse<TransactionStatusResponse> statusResponse =
                transactions.getStatusByReference(request.providerReference(), null);

        requireSuccess(statusResponse, "transaction query");
        TransactionStatusResponse status = statusResponse.getResponseBody();
        RefundRequest refundRequest = new RefundRequest();

        refundRequest.setTransactionReference(status.getTransactionReference());
        refundRequest.setRefundReference(request.refundKey());
        refundRequest.setRefundAmount(naira(request.amountKobo()));
        refundRequest.setRefundReason("Order refund");
        refundRequest.setCustomerNote("Order refund");
        refundRequest.setDestinationAccountNumber(request.destinationAccount());
        refundRequest.setDestinationAccountBankCode(request.destinationBankCode());

        MonnifyBaseResponse<RefundResponse> response = refunds.initiateRefund(refundRequest);
        requireSuccess(response, "refund initiation");
        RefundResponse body = response.getResponseBody();

        return new ReverseData(body.getRefundReference());
    }

    private <T> void requireSuccess(MonnifyBaseResponse<T> response, String what) {
        if (response == null || !response.isRequestSuccessful() || response.getResponseBody() == null) {
            String message = response == null ? "no response" : response.getResponseMessage();
            throw new IllegalStateException("Monnify " + what + " failed: " + message);
        }
    }

    private BigDecimal naira(long amountKobo) {
        return BigDecimal.valueOf(amountKobo).movePointLeft(2);
    }

    private long kobo(BigDecimal amountNaira) {
        return amountNaira.movePointRight(2).longValueExact();
    }

    static LocalDateTime accountExpiryOf(BankTransferResponse bank) {

        if (bank.getExpiresOn() != null) {
            return bank.getExpiresOn();
        }

        if (bank.getRequestTime() != null) {
            return LocalDateTime.parse(bank.getRequestTime()).plusSeconds(bank.getAccountDurationSeconds());
        }

        return null;
    }
}
