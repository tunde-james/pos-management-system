package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.payments.api.GatewayVerifyStatus;

class TrayAndReceiptApiTests extends AbstractPaymentsTest {

    @Test
    @DisplayName("The tray shows MY live slips, newest first — never another cashier's")
    void trayListsOwnAlivePendingsNewestFirstAndNotOthers() throws Exception {

        ManagedStore store = seedStore("Tray Store One");
        TillOperator first = employ(store.storeId(), "CASHIER", "Tray Owner");
        TillOperator other = employ(store.storeId(), "CASHIER", "Tray Neighbor");
        openSession(first.token());
        openSession(other.token());
        String category = createCategoryAsAdmin("Tray Category One");
        String milk = listProductAs(store, category, "Tray Milk A", "15000.00", "12000.00");
        String bread = listProductAs(store, category, "Tray Bread A", "15000.00", "12000.00");
        String juice = listProductAs(store, category, "Tray Juice A", "15000.00", "12000.00");
        stockAs(store, milk, 20);
        stockAs(store, bread, 20);
        stockAs(store, juice, 20);

        String firstSlip = jsonOf(pendingCheckout(first.token(), line(milk, 1), "TRANSFER", "PAYSTACK"))
                .get("publicId")
                .asText();
        String secondSlip = jsonOf(pendingCheckout(first.token(), line(bread, 1), "TRANSFER", "PAYSTACK"))
                .get("publicId")
                .asText();
        String othersSlip = jsonOf(pendingCheckout(other.token(), line(juice, 1), "TRANSFER", "PAYSTACK"))
                .get("publicId")
                .asText();

        MvcResult tray = pendingTray(first.token());

        assertThat(tray.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(tray).size()).isEqualTo(2);

        assertThat(jsonOf(tray).get(0).get("publicId").asText()).isEqualTo(secondSlip);

        assertThat(jsonOf(tray).get(1).get("publicId").asText()).isEqualTo(firstSlip);

        assertThat(jsonOf(tray).toString()).doesNotContain(othersSlip);
    }

    @Test
    @DisplayName("A stale slip drops out of the tray (but stays findable by its id)")
    void trayDropsExpiredSlipAfterTtl() throws Exception {

        ManagedStore store = seedStore("Tray Store Two");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Stale Tray Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Tray Category Two");
        String product = listProductAs(store, category, "StaleTray Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        waitForTtl();

        assertThat(jsonOf(pendingTray(cashier.token())).size()).isEqualTo(0);

        MvcResult detail = machineOrder(cashier.token(), orderPublicId);

        assertThat(detail.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(detail).get("status").asText()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("Settled slips (PAID or FAILED) leave the tray")
    void trayExcludesPaidAndFailed() throws Exception {

        ManagedStore store = seedStore("Tray Store Three");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Settled Tray Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Tray Category Three");
        String paidProduct = listProductAs(store, category, "SettledA Milk", "15000.00", "12000.00");
        String failedProduct = listProductAs(store, category, "SettledB Milk", "15000.00", "12000.00");
        stockAs(store, paidProduct, 10);
        stockAs(store, failedProduct, 10);

        MvcResult toPay = pendingCheckout(cashier.token(), line(paidProduct, 1), "TRANSFER", "PAYSTACK");
        stubVerify(GatewayVerifyStatus.SUCCESS, null);
        assertThat(verifyOrder(cashier.token(), jsonOf(toPay).get("publicId").asText())
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        MvcResult toFail = pendingCheckout(cashier.token(), line(failedProduct, 1), "TRANSFER", "PAYSTACK");
        stubVerify(GatewayVerifyStatus.FAILED, null);
        assertThat(verifyOrder(cashier.token(), jsonOf(toFail).get("publicId").asText())
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        assertThat(jsonOf(pendingTray(cashier.token())).size()).isEqualTo(0);
    }

    @Test
    @DisplayName("The customer quoted their number: the filter surfaces exactly that slip")
    void trayNumberFilterReturnsExactSlip() throws Exception {

        ManagedStore store = seedStore("Tray Store Four");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Numbered Tray Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Tray Category Four");
        String product = listProductAs(store, category, "NumberedTray Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();
        String orderNumber = jsonOf(slip).get("orderNumber").asText();

        MvcResult tray = pendingTrayByNumber(cashier.token(), orderNumber);

        assertThat(tray.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(tray).size()).isEqualTo(1);

        assertThat(jsonOf(tray).get(0).get("publicId").asText()).isEqualTo(orderPublicId);
    }

    @Test
    @DisplayName("A number from ANOTHER cashier's till is not found (no cross-till peeking): 404")
    void trayNumberFilterOtherCashiersNumberReturns404() throws Exception {

        ManagedStore store = seedStore("Tray Store Five");
        TillOperator first = employ(store.storeId(), "CASHIER", "Peek Owner");
        TillOperator other = employ(store.storeId(), "CASHIER", "Peek Neighbor");
        openSession(first.token());
        openSession(other.token());
        String category = createCategoryAsAdmin("Tray Category Five");
        String product = listProductAs(store, category, "Peek Milk", "15000.00", "12000.00");
        stockAs(store, product, 20);

        String othersNumber = jsonOf(pendingCheckout(other.token(), line(product, 1), "TRANSFER", "PAYSTACK"))
                .get("orderNumber")
                .asText();

        MvcResult tray = pendingTrayByNumber(first.token(), othersNumber);

        assertThat(tray.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("The tray is a till act: no active session = 409")
    void trayWithoutActiveSessionReturns409() throws Exception {

        ManagedStore store = seedStore("Tray Store Six");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Sessionless Tray Cashier");

        MvcResult tray = pendingTray(cashier.token());

        assertThat(tray.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("The customer receipt stays quiet while the sale is pending: 409")
    void customerReceiptForPendingOrderReturns409() throws Exception {

        ManagedStore store = seedStore("Tray Store Seven");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Quiet Paper Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Tray Category Seven");
        String product = listProductAs(store, category, "QuietPaper Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        MvcResult result = customerReceipt(cashier.token(), orderPublicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Once the bank says yes, the customer receipt prints like any other sale")
    void customerReceiptWorksAfterGatewayPaid() throws Exception {

        ManagedStore store = seedStore("Tray Store Eight");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Print At Last Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Tray Category Eight");
        String product = listProductAs(store, category, "PrintAtLast Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        stubVerify(GatewayVerifyStatus.SUCCESS, null);
        assertThat(verifyOrder(cashier.token(), orderPublicId).getResponse().getStatus())
                .isEqualTo(200);

        MvcResult receipt = customerReceipt(cashier.token(), orderPublicId);

        assertThat(receipt.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(receipt).get("displayTotal").asText()).isEqualTo("₦12000.00");
    }

    @Test
    @DisplayName("The machine view speaks the effective status: PENDING now, EXPIRED after the clock")
    void machineReceiptShowsPendingThenEffectiveExpired() throws Exception {

        ManagedStore store = seedStore("Tray Store Nine");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Effective Status Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Tray Category Nine");
        String product = listProductAs(store, category, "EffectiveStatus Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        assertThat(jsonOf(machineOrder(cashier.token(), orderPublicId))
                        .get("status")
                        .asText())
                .isEqualTo("PENDING_PAYMENT");

        waitForTtl();

        MvcResult after = machineOrder(cashier.token(), orderPublicId);

        assertThat(after.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(after).get("status").asText()).isEqualTo("EXPIRED");

        assertThat(jsonOf(after).get("statusReason").asText()).isEqualTo("TTL_TIMEOUT");
    }

    @Test
    @DisplayName("A gateway-paid machine receipt carries the provider reference (Refund's trail)")
    void machineReceiptAfterGatewayPaidCarriesProviderReference() throws Exception {

        ManagedStore store = seedStore("Tray Store Ten");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Paper Trail Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Tray Category Ten");
        String product = listProductAs(store, category, "PaperTrail Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult slip = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();
        String providerReference = jsonOf(slip).get("providerReference").asText();

        stubVerify(GatewayVerifyStatus.SUCCESS, null);
        assertThat(verifyOrder(cashier.token(), orderPublicId).getResponse().getStatus())
                .isEqualTo(200);

        MvcResult machine = machineOrder(cashier.token(), orderPublicId);

        assertThat(jsonOf(machine).get("paymentReference").asText()).isEqualTo(providerReference);
    }
}
