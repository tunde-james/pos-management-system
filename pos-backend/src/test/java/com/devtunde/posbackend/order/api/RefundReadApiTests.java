package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefundReadApiTests extends AbstractRefundTest {

    @Test
    @DisplayName("The machine receipt shows the refund after it happens")
    void machineReceiptShowsRefundAfterRefund() throws Exception {

        ManagedStore store = seedStore("Refund Read Store One");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Read Ringr One");
        TillOperator manager = employManager(store, "Read Manager One");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Read Category One");
        String product = listProductAs(store, category, "Receipted Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));
        assertThat(refund(manager.token(), orderPublicId, refundBody(refundLine(product, "DAMAGED"), "CASH"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        MvcResult machine = machineOrder(seller.token(), orderPublicId);

        var refunds = jsonOf(machine).get("refunds");
        assertThat(refunds.size()).isEqualTo(1);

        assertThat(refunds.get(0).get("totalKobo").asLong()).isEqualTo(1200000L);

        assertThat(refunds.get(0).get("displayTotal").asText()).isEqualTo("₦12000.00");

        assertThat(refunds.get(0).get("returnMethod").asText()).isEqualTo("CASH");

        assertThat(refunds.get(0).get("providerRefundReference").isNull()).isTrue();

        assertThat(refunds.get(0).get("refundedAt")).isNotNull();
    }

    @Test
    @DisplayName("One of two lines refunded: the order is still PAID")
    void partialRefundKeepsOrderPaid() throws Exception {

        ManagedStore store = seedStore("Refund Read Store Two");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Read Ringr Two");
        TillOperator manager = employManager(store, "Read Manager Two");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Read Category Two");
        String milk = listProductAs(store, category, "PartialRead Milk", "15000.00", "12000.00");
        String bread = listProductAs(store, category, "PartialRead Bread", "5000.00", "4000.00");
        stockAs(store, milk, 10);
        stockAs(store, bread, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(milk, 1) + ", " + line(bread, 1));
        assertThat(refund(manager.token(), orderPublicId, refundBody(refundLine(milk, null), "CASH"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        var machine = jsonOf(machineOrder(seller.token(), orderPublicId));

        assertThat(machine.get("status").asText()).isEqualTo("PAID");

        assertThat(machine.get("refunds").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("Every line refunded: the machine view says REFUNDED — derived, never written")
    void fullRefundShowsEffectiveRefundedStatus() throws Exception {

        ManagedStore store = seedStore("Refund Read Store Three");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Read Ringr Three");
        TillOperator manager = employManager(store, "Read Manager Three");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Read Category Three");
        String milk = listProductAs(store, category, "FullRead Milk", "15000.00", "12000.00");
        String bread = listProductAs(store, category, "FullRead Bread", "5000.00", "4000.00");
        stockAs(store, milk, 10);
        stockAs(store, bread, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(milk, 1) + ", " + line(bread, 1));
        assertThat(refund(manager.token(), orderPublicId, refundBody(refundLine(milk, null), "CASH"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        assertThat(refund(manager.token(), orderPublicId, refundBody(refundLine(bread, null), "CASH"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        var machine = jsonOf(machineOrder(seller.token(), orderPublicId));

        assertThat(machine.get("status").asText()).isEqualTo("REFUNDED");

        assertThat(machine.get("statusReason").asText()).isEqualTo("FULLY_REFUNDED");

        assertThat(machine.get("refunds").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("A single-line order fully refunded: REFUNDED after one refund")
    void singleLineFullRefundShowsRefunded() throws Exception {

        ManagedStore store = seedStore("Refund Read Store Four");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Read Ringr Four");
        TillOperator manager = employManager(store, "Read Manager Four");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Read Category Four");
        String product = listProductAs(store, category, "SoloRead Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));
        assertThat(refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        var machine = jsonOf(machineOrder(seller.token(), orderPublicId));

        assertThat(machine.get("status").asText()).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("The customer receipt is the SALE's document — refunds leave it untouched")
    void customerReceiptUnchangedAfterFullRefund() throws Exception {

        ManagedStore store = seedStore("Refund Read Store Five");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Read Ringr Five");
        TillOperator manager = employManager(store, "Read Manager Five");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Read Category Five");
        String product = listProductAs(store, category, "CustomerRead Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));
        assertThat(refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        MvcResult receipt = customerReceipt(seller.token(), orderPublicId);

        assertThat(receipt.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(receipt).get("total").asText()).isEqualTo("₦12000.00");

        assertThat(jsonOf(receipt).get("refunds")).isNull();
    }

    @Test
    @DisplayName("Before any refund the machine receipt carries no refunds at all")
    void machineReceiptBeforeAnyRefundHasNoRefunds() throws Exception {

        ManagedStore store = seedStore("Refund Read Store Six");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Read Ringr Six");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Read Category Six");
        String product = listProductAs(store, category, "NoRefund Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        var machine = jsonOf(machineOrder(seller.token(), orderPublicId));

        assertThat(machine.get("refunds")).isNull();

        assertThat(machine.get("status").asText()).isEqualTo("PAID");
    }
}
