package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefundProviderApiTests extends AbstractRefundTest {

    @Test
    @DisplayName("A gateway-paid order is refunded through the ORIGINAL provider")
    void gatewayPaidOrderRefundReversesThroughProvider() throws Exception {

        ManagedStore store = seedStore("Refund Provider Store One");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Provider Ringr One");
        TillOperator manager = employManager(store, "Provider Manager One");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Provider Category One");
        String product = listProductAs(store, category, "Reversed Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = gatewayPaidOrder(store, seller, line(product, 1), "PAYSTACK");

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), null));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("providerRefundReference").asText()).startsWith("REV-FAKE-PAYSTACK-");

        assertThat(jsonOf(result).get("returnMethod").isNull()).isTrue();

        assertThat(fakeGateway.reverseCallCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Monnify-paid orders reverse through Monnify too (the router, not a special case)")
    void monnifyGatewayRefundAlsoReverses() throws Exception {

        ManagedStore store = seedStore("Refund Provider Store Two");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Provider Ringr Two");
        TillOperator manager = employManager(store, "Provider Manager Two");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Provider Category Two");
        String product = listProductAs(store, category, "MonnifyReversed Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = gatewayPaidOrder(store, seller, line(product, 1), "MONNIFY");

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), null));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("providerRefundReference").asText()).startsWith("REV-FAKE-MONNIFY-");

        assertThat(fakeGateway.reverseCallCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("One line of a two-line sale: the reversal covers only the refunded kobo")
    void gatewayPartialRefundReversesOnlyTheRefundedKobo() throws Exception {

        ManagedStore store = seedStore("Refund Provider Store Three");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Provider Ringr Three");
        TillOperator manager = employManager(store, "Provider Manager Three");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Provider Category Three");
        String milk = listProductAs(store, category, "Partial Milk", "15000.00", "12000.00");
        String bread = listProductAs(store, category, "Partial Bread", "5000.00", "4000.00");
        stockAs(store, milk, 10);
        stockAs(store, bread, 10);

        String orderPublicId = gatewayPaidOrder(store, seller, line(milk, 1) + ", " + line(bread, 1), "PAYSTACK");

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(milk, null), null));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        // Only the milk line's kobo goes back to the provider.

        assertThat(jsonOf(result).get("totalKobo").asLong()).isEqualTo(1200000L);

        assertThat(fakeGateway.reverseCallCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("A counter-settled order records the cash return — no provider is ever called")
    void counterOrderRefundRecordsCashReturnMethod() throws Exception {

        ManagedStore store = seedStore("Refund Provider Store Four");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Provider Ringr Four");
        TillOperator manager = employManager(store, "Provider Manager Four");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Provider Category Four");
        String product = listProductAs(store, category, "CounterBack Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("returnMethod").asText()).isEqualTo("CASH");

        assertThat(jsonOf(result).get("providerRefundReference").isNull()).isTrue();

        assertThat(fakeGateway.reverseCallCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("A bank transfer back to the customer is a legal return method too")
    void counterOrderRefundRecordsBankTransferReturnMethod() throws Exception {

        ManagedStore store = seedStore("Refund Provider Store Five");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Provider Ringr Five");
        TillOperator manager = employManager(store, "Provider Manager Five");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Provider Category Five");
        String product = listProductAs(store, category, "TransferredBack Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result =
                refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "BANK_TRANSFER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("returnMethod").asText()).isEqualTo("BANK_TRANSFER");
    }

    @Test
    @DisplayName(
            "A counter-settled refund without a return method: 400 — the system must record HOW the money went back")
    void counterOrderRefundWithoutReturnMethodReturns400() throws Exception {

        ManagedStore store = seedStore("Refund Provider Store Six");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Provider Ringr Six");
        TillOperator manager = employManager(store, "Provider Manager Six");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Provider Category Six");
        String product = listProductAs(store, category, "NoMethod Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), null));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("A gateway-paid refund carrying a return method: 400 — the provider reversal IS the return")
    void gatewayOrderRefundWithReturnMethodReturns400() throws Exception {

        ManagedStore store = seedStore("Refund Provider Store Seven");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Provider Ringr Seven");
        TillOperator manager = employManager(store, "Provider Manager Seven");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Provider Category Seven");
        String product = listProductAs(store, category, "DoubleTrack Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = gatewayPaidOrder(store, seller, line(product, 1), "PAYSTACK");

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }
}
