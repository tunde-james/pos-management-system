package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.payments.api.GatewayVerifyStatus;

class ReviveApiTests extends AbstractPaymentsTest {

    @Test
    @DisplayName("A stale slip comes back to life: PENDING again, fresh deadline, slip re-emitted")
    void reviveExpiredOrderReturnsPendingWithSlipReemitted() throws Exception {

        ManagedStore store = seedStore("Revive Store One");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Rescuer Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Revive Category One");
        String product = listProductAs(store, category, "Rescued Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        waitForTtl();

        MvcResult result = reviveOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(result).get("status").asText()).isEqualTo("PENDING_PAYMENT");

        assertThat(jsonOf(result).get("expiresAt")).isNotNull();

        assertThat(jsonOf(result).get("paystack").get("authorizationUrl").asText())
                .startsWith("https://fake.paystack.test/checkout/");
    }

    @Test
    @DisplayName("A revived slip can still pay: revive, bank says yes, PAID, shelf moves")
    void revivedOrderCanStillBePaid() throws Exception {

        ManagedStore store = seedStore("Revive Store Two");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Second Chance Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Revive Category Two");
        String product = listProductAs(store, category, "SecondChance Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 2), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        waitForTtl();
        assertThat(reviveOrder(cashier.token(), orderPublicId).getResponse().getStatus())
                .isEqualTo(200);

        stubVerify(GatewayVerifyStatus.SUCCESS, null);
        MvcResult paid = verifyOrder(cashier.token(), orderPublicId);

        assertThat(paid.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(paid).get("status").asText()).isEqualTo("PAID");
        assertThat(inventoryQuantity(store, product)).isEqualTo(8);
    }

    @Test
    @DisplayName("No cap in v1: expire, revive, expire, revive — the slip keeps second chances")
    void secondReviveAlsoSucceedsNoCap() throws Exception {

        ManagedStore store = seedStore("Revive Store Three");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Persistent Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Revive Category Three");
        String product = listProductAs(store, category, "Persistent Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        waitForTtl();
        assertThat(reviveOrder(cashier.token(), orderPublicId).getResponse().getStatus())
                .isEqualTo(200);
        waitForTtl();

        MvcResult second = reviveOrder(cashier.token(), orderPublicId);

        assertThat(second.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(second).get("status").asText()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("A live slip has nothing to be revived from: 409")
    void reviveAlivePendingReturns409() throws Exception {

        ManagedStore store = seedStore("Revive Store Four");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Premature Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Revive Category Four");
        String product = listProductAs(store, category, "Premature Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        MvcResult result = reviveOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("A gateway-declined slip stays dead: 409 (fresh sale is the path)")
    void reviveFailedOrderReturns409() throws Exception {

        ManagedStore store = seedStore("Revive Store Five");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Declined Revive Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Revive Category Five");
        String product = listProductAs(store, category, "DeclinedRevive Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        stubVerify(GatewayVerifyStatus.FAILED, null);
        assertThat(verifyOrder(cashier.token(), orderPublicId).getResponse().getStatus())
                .isEqualTo(200);

        MvcResult result = reviveOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("A stock-gone slip stays dead: 409 (the bank's yes cannot be un-spent)")
    void reviveStockGoneOrderReturns409() throws Exception {

        ManagedStore store = seedStore("Revive Store Six");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "StockGone Revive Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Revive Category Six");
        String product = listProductAs(store, category, "StockGone Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 10), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        checkout(cashier.token(), checkoutBody(line(product, 10), "CASH", ""));

        stubVerify(GatewayVerifyStatus.SUCCESS, null);
        assertThat(verifyOrder(cashier.token(), orderPublicId).getResponse().getStatus())
                .isEqualTo(200);

        MvcResult result = reviveOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Only the slip's owner revives it: stranger gets 403")
    void reviveByOtherCashierReturns403() throws Exception {

        ManagedStore store = seedStore("Revive Store Seven");
        TillOperator opener = employ(store.storeId(), "CASHIER", "Revive Owner");
        TillOperator stranger = employ(store.storeId(), "CASHIER", "Revive Stranger");
        openSession(opener.token());
        openSession(stranger.token());
        String category = createCategoryAsAdmin("Revive Category Seven");
        String product = listProductAs(store, category, "ReviveStranger Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(opener.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        waitForTtl();

        MvcResult result = reviveOrder(stranger.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("A counter-settled order has no slip to revive: 409")
    void reviveCounterSettledOrderReturns409() throws Exception {

        ManagedStore store = seedStore("Revive Store Eight");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Counter Revive Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Revive Category Eight");
        String product = listProductAs(store, category, "CounterRevive Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult paid = checkout(cashier.token(), checkoutBody(line(product, 1), "CASH", ""));
        String orderPublicId = jsonOf(paid).get("publicId").asText();

        MvcResult result = reviveOrder(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }
}
