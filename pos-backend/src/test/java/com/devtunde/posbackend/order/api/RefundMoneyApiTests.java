package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefundMoneyApiTests extends AbstractRefundTest {

    @Test
    @DisplayName("One line comes back for exactly the frozen line total")
    void singleLineRefundReturnsFrozenLineTotal() throws Exception {

        ManagedStore store = seedStore("Refund Money Store One");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Money Ringr One");
        TillOperator manager = employManager(store, "Money Manager One");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Money Category One");
        String product = listProductAs(store, category, "Frozen Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("totalKobo").asLong()).isEqualTo(1200000L);
    }

    @Test
    @DisplayName("Several lines come back for the sum of their frozen totals")
    void multiLineRefundSumsFrozenTotals() throws Exception {

        ManagedStore store = seedStore("Refund Money Store Two");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Money Ringr Two");
        TillOperator manager = employManager(store, "Money Manager Two");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Money Category Two");
        String milk = listProductAs(store, category, "Sum Milk", "15000.00", "12000.00");
        String bread = listProductAs(store, category, "Sum Bread", "5000.00", "4000.00");
        stockAs(store, milk, 10);
        stockAs(store, bread, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(milk, 1) + ", " + line(bread, 2));

        MvcResult result = refund(
                manager.token(),
                orderPublicId,
                refundBody(refundLine(milk, null) + ", " + refundLine(bread, null), "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        // 1 x ₦12,000.00 + 2 x ₦4,000.00 = ₦20,000.00 = 2,000,000 kobo

        assertThat(jsonOf(result).get("totalKobo").asLong()).isEqualTo(2000000L);
    }

    @Test
    @DisplayName("A second refund of ANOTHER line is legal")
    void secondRefundOfAnotherLineSucceeds() throws Exception {

        ManagedStore store = seedStore("Refund Money Store Three");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Money Ringr Three");
        TillOperator manager = employManager(store, "Money Manager Three");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Money Category Three");
        String milk = listProductAs(store, category, "TwoTrip Milk", "15000.00", "12000.00");
        String bread = listProductAs(store, category, "TwoTrip Bread", "5000.00", "4000.00");
        stockAs(store, milk, 10);
        stockAs(store, bread, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(milk, 1) + ", " + line(bread, 1));

        assertThat(refund(manager.token(), orderPublicId, refundBody(refundLine(milk, null), "CASH"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        MvcResult second = refund(manager.token(), orderPublicId, refundBody(refundLine(bread, null), "BANK_TRANSFER"));

        assertThat(second.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(second).get("totalKobo").asLong()).isEqualTo(400000L);
    }

    @Test
    @DisplayName("The same line twice: the database says no, 409")
    void doubleRefundOfSameLineReturns409() throws Exception {

        ManagedStore store = seedStore("Refund Money Store Four");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Money Ringr Four");
        TillOperator manager = employManager(store, "Money Manager Four");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Money Category Four");
        String product = listProductAs(store, category, "Twice Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 2));

        assertThat(refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        MvcResult second = refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"));

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Every line already refunded: nothing left to refund, 409")
    void refundAfterAllLinesRefundedReturns409() throws Exception {

        ManagedStore store = seedStore("Refund Money Store Five");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Money Ringr Five");
        TillOperator manager = employManager(store, "Money Manager Five");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Money Category Five");
        String milk = listProductAs(store, category, "AllGone Milk", "15000.00", "12000.00");
        String bread = listProductAs(store, category, "AllGone Bread", "5000.00", "4000.00");
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

        MvcResult third = refund(manager.token(), orderPublicId, refundBody(refundLine(milk, null), "CASH"));

        assertThat(third.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("The shelf re-prices after the sale — the refund still pays the frozen price")
    void refundIgnoresLaterPriceChanges() throws Exception {

        ManagedStore store = seedStore("Refund Money Store Six");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Money Ringr Six");
        TillOperator manager = employManager(store, "Money Manager Six");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Money Category Six");
        String product = listProductAs(store, category, "Repriced Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));
        changeSellingPrice(store, product, "5000.00");

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        // ₦12,000.00 was paid, so ₦12,000.00 comes back — not the new ₦5,000.00.

        assertThat(jsonOf(result).get("totalKobo").asLong()).isEqualTo(1200000L);
    }

    @Test
    @DisplayName("The response carries the lines, their kobo, and their dispositions")
    void refundResponseCarriesLinesAndKobo() throws Exception {

        ManagedStore store = seedStore("Refund Money Store Seven");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Money Ringr Seven");
        TillOperator manager = employManager(store, "Money Manager Seven");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Money Category Seven");
        String product = listProductAs(store, category, "Response Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 1));

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(product, "DAMAGED"), "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("displayTotal").asText()).isEqualTo("₦12000.00");

        assertThat(jsonOf(result).get("refundedAt")).isNotNull();

        assertThat(jsonOf(result).get("returnMethod").asText()).isEqualTo("CASH");

        var lines = jsonOf(result).get("lines");
        assertThat(lines.size()).isEqualTo(1);

        assertThat(lines.get(0).get("productPublicId").asText()).isEqualTo(product);

        assertThat(lines.get(0).get("lineTotalKobo").asLong()).isEqualTo(1200000L);

        assertThat(lines.get(0).get("disposition").asText()).isEqualTo("DAMAGED");
    }
}
