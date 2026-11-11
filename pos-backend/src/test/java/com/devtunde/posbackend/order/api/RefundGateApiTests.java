package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.payments.api.GatewayVerifyStatus;

class RefundGateApiTests extends AbstractRefundTest {

    private static final String UNKNOWN_PRODUCT = "00000000-0000-0000-0000-000000000002";

    private String refundBodyFor(String productPublicId) {
        return refundBody(refundLine(productPublicId, null), "CASH");
    }

    @Test
    @DisplayName("An order that never existed: 404")
    void refundUnknownOrderReturns404() throws Exception {

        ManagedStore store = seedStore("Refund Gate Store One");
        TillOperator manager = employManager(store, "Ghost Order Manager");

        MvcResult result = refund(manager.token(), UNKNOWN_ORDER, refundBodyFor(UNKNOWN_PRODUCT));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("The SELLING cashier cannot refund their own sale: 403")
    void refundBySellingCashierReturns403() throws Exception {

        ManagedStore store = seedStore("Refund Gate Store Two");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Self Serving Seller");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Gate Category Two");
        String product = listProductAs(store, category, "SelfServe Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(seller.token(), orderPublicId, refundBodyFor(product));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Any other cashier is no more allowed: 403")
    void refundByOtherCashierReturns403() throws Exception {

        ManagedStore store = seedStore("Refund Gate Store Three");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Sale Maker");
        TillOperator stranger = employ(store.storeId(), "CASHIER", "Refund Wanderer");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Gate Category Three");
        String product = listProductAs(store, category, "Wanderer Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(stranger.token(), orderPublicId, refundBodyFor(product));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("A sales attendant has no refund authority: 403")
    void refundBySalesAttendantReturns403() throws Exception {

        ManagedStore store = seedStore("Refund Gate Store Four");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Sale Ringr");
        TillOperator attendant = employ(store.storeId(), "SALES_ATTENDANT", "Helpful Hands");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Gate Category Four");
        String product = listProductAs(store, category, "Attendant Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(attendant.token(), orderPublicId, refundBodyFor(product));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("The store's manager refunds: 201")
    void refundByStoreManagerReturns201() throws Exception {

        ManagedStore store = seedStore("Refund Gate Store Five");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Ringr Five");
        TillOperator manager = employManager(store, "Manager Five");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Gate Category Five");
        String product = listProductAs(store, category, "Manager Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(manager.token(), orderPublicId, refundBodyFor(product));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("The seat admin refunds: 201")
    void refundBySeatAdminReturns201() throws Exception {

        ManagedStore store = seedStore("Refund Gate Store Six");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Ringr Six");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Gate Category Six");
        String product = listProductAs(store, category, "SeatAdmin Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(store.managerToken(), orderPublicId, refundBodyFor(product));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("The chain admin refunds: 201")
    void refundByChainAdminReturns201() throws Exception {

        ManagedStore store = seedStore("Refund Gate Store Seven");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Ringr Seven");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Gate Category Seven");
        String product = listProductAs(store, category, "ChainAdmin Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(adminToken(), orderPublicId, refundBodyFor(product));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("A declined (FAILED) order has no money to give back: 409")
    void refundOfFailedOrderReturns409() throws Exception {

        ManagedStore store = seedStore("Refund Gate Store Eight");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Declined Ringr");
        TillOperator manager = employManager(store, "Declined Manager");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Gate Category Eight");
        String product = listProductAs(store, category, "Declined Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = gatewayFailedOrder(store, seller, line(product, 1));

        MvcResult result = refund(manager.token(), orderPublicId, refundBodyFor(product));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("A settled-expired order never paid: 409")
    void refundOfExpiredOrderReturns409() throws Exception {

        ManagedStore store = seedStore("Refund Gate Store Nine");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Expired Ringr");
        TillOperator manager = employManager(store, "Expired Manager");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Gate Category Nine");
        String product = listProductAs(store, category, "Expired Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(seller.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();
        waitForTtl();
        stubVerify(GatewayVerifyStatus.SUCCESS, null);
        assertThat(verifyOrder(seller.token(), orderPublicId).getResponse().getStatus())
                .isEqualTo(200);

        MvcResult result = refund(manager.token(), orderPublicId, refundBodyFor(product));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("A still-pending order has nothing to refund: 409")
    void refundOfPendingOrderReturns409() throws Exception {

        ManagedStore store = seedStore("Refund Gate Store Ten");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Pending Ringr");
        TillOperator manager = employManager(store, "Pending Manager");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Gate Category Ten");
        String product = listProductAs(store, category, "Pending Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(seller.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        MvcResult result = refund(manager.token(), orderPublicId, refundBodyFor(product));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("An empty line list refunds nothing: 400")
    void refundWithEmptyLinesReturns400() throws Exception {

        ManagedStore store = seedStore("Refund Gat Store Eleven");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Empty Ringr");
        TillOperator manager = employManager(store, "Empty Manager");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Gate Category Eleven");
        String product = listProductAs(store, category, "Empty Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(manager.token(), orderPublicId, refundBody("", "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("A product the order never carried: 400")
    void refundWithLineNotOnOrderReturns400() throws Exception {

        ManagedStore store = seedStore("Refund Gate Store Twelve");
        TillOperator seller = employ(store.storeId(), "CASHIER", "GhostLine Ringr");
        TillOperator manager = employManager(store, "GhostLine Manager");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Gate Category Twelve");
        String product = listProductAs(store, category, "GhostLine Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);
        String neverSold = listProductAs(store, category, "NeverSold Milk", "15000.00", "12000.00");
        stockAs(store, neverSold, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(neverSold, null), "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }
}
