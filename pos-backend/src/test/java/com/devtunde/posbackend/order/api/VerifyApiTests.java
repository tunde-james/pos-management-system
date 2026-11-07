package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.payments.api.GatewayVerifyStatus;

class VerifyApiTests extends AbstractPaymentsTest {

    private static final String UNKNOWN_ORDER = "00000000-0000-0000-0000-000000000001";

    @Test
    @DisplayName("Bank says yes, amounts match: PAID — and the shelf moves NOW")
    void verifySuccessWithMatchingAmountPaysAndMovesShelf() throws Exception {

        ManagedStore store = seedStore("Verify Store One");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Verify Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Verify Category One");
        String product = listProductAs(store, category, "Verify Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 4), "TRANSFER", "PAYSTACK");

        assertThat(slip.getResponse().getStatus()).isEqualTo(202);
        String orderPublicId = jsonOf(slip).get("publicId").asText();
        String providerReference = jsonOf(slip).get("providerReference").asText();
        assertThat(inventoryQuantity(store, product)).isEqualTo(10);

        stubVerify(GatewayVerifyStatus.SUCCESS, null);

        MvcResult result = verifyOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(result).get("status").asText()).isEqualTo("PAID");

        assertThat(jsonOf(result).get("paidAt")).isNotNull();

        assertThat(jsonOf(machineOrder(cashier.token(), orderPublicId)).get("paidAt")).isNotNull();

        assertThat(jsonOf(result).get("paymentReference").asText()).isEqualTo(providerReference);
        
        assertThat(inventoryQuantity(store, product)).isEqualTo(6);

        MvcResult movements = mockMvc.perform(MockMvcRequestBuilders.get(
                                "/api/v1/stores/" + store.storeId() + "/inventory/products/" + product + "/movements")
                        .header("Authorization", "Bearer " + store.managerToken()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(jsonOf(movements).get(1).get("type").asText()).isEqualTo("SALE");

        assertThat(jsonOf(movements).get(1).get("quantityDelta").asInt()).isEqualTo(-4);
    }

    @Test
    @DisplayName("Money arrived but wrong amount: 409 with both kobo figures, slip stays PENDING and can still pay")
    void verifySuccessWithWrongAmountReturns409AndStaysPending() throws Exception {

        ManagedStore store = seedStore("Verify Store Two");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Mismatch Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Verify Category Two");
        String product = listProductAs(store, category, "Mismatch Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        stubVerify(GatewayVerifyStatus.SUCCESS, 500L);

        MvcResult mismatch = verifyOrder(cashier.token(), orderPublicId);

        assertThat(mismatch.getResponse().getStatus()).isEqualTo(409);

        assertThat(jsonOf(mismatch).get("expectedKobo").asLong()).isEqualTo(1200000L);

        assertThat(jsonOf(mismatch).get("actualKobo").asLong()).isEqualTo(500L);

        assertThat(jsonOf(machineOrder(cashier.token(), orderPublicId))
                        .get("status")
                        .asText())
                .isEqualTo("PENDING_PAYMENT");
        assertThat(inventoryQuantity(store, product)).isEqualTo(10);

        stubVerify(GatewayVerifyStatus.SUCCESS, null);
        MvcResult paid = verifyOrder(cashier.token(), orderPublicId);

        assertThat(paid.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(paid).get("status").asText()).isEqualTo("PAID");
        assertThat(inventoryQuantity(store, product)).isEqualTo(9);
    }

    @Test
    @DisplayName("Gateway declines: FAILED, shelf untouched")
    void verifyDeclinedReturnsFailedShelfUntouched() throws Exception {

        ManagedStore store = seedStore("Verify Store Three");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Declined Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Verify Category Three");
        String product = listProductAs(store, category, "Declined Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        stubVerify(GatewayVerifyStatus.FAILED, null);

        MvcResult result = verifyOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(result).get("status").asText()).isEqualTo("FAILED");

        assertThat(jsonOf(result).get("statusReason").asText()).isEqualTo("GATEWAY_DECLINED");
        assertThat(inventoryQuantity(store, product)).isEqualTo(10);
    }

    @Test
    @DisplayName("Nothing arrived yet: still PENDING, cashier taps again later")
    void verifyNothingArrivedYetReturnsStillPending() throws Exception {

        ManagedStore store = seedStore("Verify Store Four");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Patient Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Verify Category Four");
        String product = listProductAs(store, category, "Patient Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "MONNIFY");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        MvcResult result = verifyOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(result).get("status").asText()).isEqualTo("PENDING_PAYMENT");

        assertThat(jsonOf(result).get("providerReference").asText())
                .isEqualTo(jsonOf(slip).get("providerReference").asText());
        assertThat(inventoryQuantity(store, product)).isEqualTo(10);
    }

    @Test
    @DisplayName("The clock ran out: EXPIRED/TTL_TIMEOUT, and the bank is never even asked")
    void verifyAfterTtlReturnsExpiredTtlTimeoutAndNeverConsultsGateway() throws Exception {

        ManagedStore store = seedStore("Verify Store Five");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Slow Clock Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Verify Category Five");
        String product = listProductAs(store, category, "SlowClock Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        waitForTtl();
        stubVerify(GatewayVerifyStatus.SUCCESS, null);

        MvcResult result = verifyOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(result).get("status").asText()).isEqualTo("EXPIRED");

        assertThat(jsonOf(result).get("statusReason").asText()).isEqualTo("TTL_TIMEOUT");
        assertThat(inventoryQuantity(store, product)).isEqualTo(10);

        assertThat(fakeGateway.verifyCallCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Each cashier to their own till: another cashier's verify tap = 403")
    void verifyByOtherCashierReturns403() throws Exception {

        ManagedStore store = seedStore("Verify Store Six");
        TillOperator opener = employ(store.storeId(), "CASHIER", "Slip Owner");
        TillOperator stranger = employ(store.storeId(), "CASHIER", "Slip Stranger");
        openSession(opener.token());
        openSession(stranger.token());
        String category = createCategoryAsAdmin("Verify Category Six");
        String product = listProductAs(store, category, "Stranger Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(opener.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        MvcResult result = verifyOrder(stranger.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);

        assertThat(jsonOf(machineOrder(opener.token(), orderPublicId))
                        .get("status")
                        .asText())
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("A closed till cannot check payments: 409 (resume first, then check)")
    void verifyWithClosedSessionReturns409() throws Exception {

        ManagedStore store = seedStore("Verify Store Seven");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Closed Till Cashier");
        String sessionId = openSession(cashier.token());
        String category = createCategoryAsAdmin("Verify Category Seven");
        String product = listProductAs(store, category, "ClosedTill Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        postAction("/api/v1/sessions/" + sessionId + "/close", cashier.token());

        MvcResult result = verifyOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Double tap: the first settles, the second gets 409 — never two settlements")
    void verifySecondTapAfterPaidReturns409() throws Exception {

        ManagedStore store = seedStore("Verify Store Eight");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Double Tap Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Verify Category Eight");
        String product = listProductAs(store, category, "DoubleTap Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        stubVerify(GatewayVerifyStatus.SUCCESS, null);
        assertThat(verifyOrder(cashier.token(), orderPublicId).getResponse().getStatus())
                .isEqualTo(200);

        MvcResult second = verifyOrder(cashier.token(), orderPublicId);

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(inventoryQuantity(store, product)).isEqualTo(9);
    }

    @Test
    @DisplayName("An order that never existed: 404")
    void verifyUnknownOrderReturns404() throws Exception {

        ManagedStore store = seedStore("Verify Store Nine");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Ghost Slip Cashier");
        openSession(cashier.token());

        MvcResult result = verifyOrder(cashier.token(), UNKNOWN_ORDER);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("A counter-settled (cash) order has no gateway leg: 409")
    void verifyCounterSettledOrderReturns409() throws Exception {

        ManagedStore store = seedStore("Verify Store Ten");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Cash Only Verify Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Verify Category Ten");
        String product = listProductAs(store, category, "CashOnlyVerify Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult paid = checkout(cashier.token(), checkoutBody(line(product, 1), "CASH", ""));
        String orderPublicId = jsonOf(paid).get("publicId").asText();

        MvcResult result = verifyOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Bank paid but the shelf sold out meanwhile: EXPIRED/STOCK_GONE_AT_SETTLEMENT")
    void verifyWithStockGoneReturnsExpiredStockGoneAtSettlement() throws Exception {

        ManagedStore store = seedStore("Verify Store Eleven");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "SoldOut Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Verify Category Eleven");
        String product = listProductAs(store, category, "SoldOut Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 10), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();
        assertThat(inventoryQuantity(store, product)).isEqualTo(10);

        // While the customer pays, the whole remaining shelf sells to someone else.
        MvcResult rivalSale = checkout(cashier.token(), checkoutBody(line(product, 10), "CASH", ""));

        assertThat(rivalSale.getResponse().getStatus()).isEqualTo(201);
        assertThat(inventoryQuantity(store, product)).isEqualTo(0);

        stubVerify(GatewayVerifyStatus.SUCCESS, null);

        MvcResult result = verifyOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(result).get("status").asText()).isEqualTo("EXPIRED");

        assertThat(jsonOf(result).get("statusReason").asText()).isEqualTo("STOCK_GONE_AT_SETTLEMENT");
        assertThat(inventoryQuantity(store, product)).isEqualTo(0);
    }
}
