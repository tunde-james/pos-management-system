package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class RefundStockApiTests extends AbstractRefundTest {

    @Test
    @DisplayName("A restocked line comes back to the shelf and writes a REFUND movement")
    void restockedLineReturnsStockAndWritesRefundMovement() throws Exception {

        ManagedStore store = seedStore("Refund Stock Store One");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Stock Ringr One");
        TillOperator manager = employManager(store, "Stock Manager One");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Stock Category One");
        String product = listProductAs(store, category, "BackOnShelf Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 4));
        assertThat(inventoryQuantity(store, product)).isEqualTo(6);

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(inventoryQuantity(store, product)).isEqualTo(10);

        JsonNode refundMove = findMovement(movements(store, product), "REFUND");
        assertThat(refundMove).isNotNull();

        assertThat(refundMove.get("quantityDelta").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("A spoilt (DAMAGED) line stays off the shelf — and writes NO movement")
    void damagedLineLeavesStockAndWritesNoMovement() throws Exception {

        ManagedStore store = seedStore("Refund Stock Store Two");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Stock Ringr Two");
        TillOperator manager = employManager(store, "Stock Manager Two");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Stock Category Two");
        String product = listProductAs(store, category, "Spoilt Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 4));
        assertThat(inventoryQuantity(store, product)).isEqualTo(6);

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(product, "DAMAGED"), "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(inventoryQuantity(store, product)).isEqualTo(6);
        assertThat(findMovement(movements(store, product), "REFUND")).isNull();
    }

    @Test
    @DisplayName("Two lines, two fates: each line follows its own disposition")
    void mixedLinesRefundEachByTheirDisposition() throws Exception {

        ManagedStore store = seedStore("Refund Stock Store Three");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Stock Ringr Three");
        TillOperator manager = employManager(store, "Stock Manager Three");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Stock Category Three");
        String milk = listProductAs(store, category, "Fine Milk", "15000.00", "12000.00");
        String bread = listProductAs(store, category, "Spoilt Bread", "5000.00", "4000.00");
        stockAs(store, milk, 10);
        stockAs(store, bread, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(milk, 2) + ", " + line(bread, 2));
        assertThat(inventoryQuantity(store, milk)).isEqualTo(8);
        assertThat(inventoryQuantity(store, bread)).isEqualTo(8);

        MvcResult result = refund(
                manager.token(),
                orderPublicId,
                refundBody(refundLine(milk, null) + ", " + refundLine(bread, "DAMAGED"), "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(inventoryQuantity(store, milk)).isEqualTo(10);
        assertThat(inventoryQuantity(store, bread)).isEqualTo(8);
    }

    @Test
    @DisplayName("The listing was delisted since the sale — the archived row still receives the stock")
    void delistedListingStillReceivesReturnedStock() throws Exception {

        ManagedStore store = seedStore("Refund Stock Store Four");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Stock Ringr Four");
        TillOperator manager = employManager(store, "Stock Manager Four");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Stock Category Four");
        String product = listProductAs(store, category, "Delisted Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 4));

        delist(store, product);

        MvcResult result = refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        Integer quantity = jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventories WHERE store_id = ? AND product_public_id = ?",
                Integer.class,
                store.storeId(),
                product);
        assertThat(quantity).isEqualTo(10);

        Boolean archived = jdbcTemplate.queryForObject(
                "SELECT archived_at IS NOT NULL FROM inventories WHERE store_id = ? AND product_public_id = ?",
                Boolean.class,
                store.storeId(),
                product);
        assertThat(archived).isTrue();
    }

    @Test
    @DisplayName("The REFUND movement obeys the ledger: after = before + delta")
    void refundMovementObeysLedgerCheck() throws Exception {

        ManagedStore store = seedStore("Refund Stock Store Five");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Stock Ringr Five");
        TillOperator manager = employManager(store, "Stock Manager Five");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Stock Category Five");
        String product = listProductAs(store, category, "Ledger Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 4));

        assertThat(refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        JsonNode refundMove = findMovement(movements(store, product), "REFUND");
        assertThat(refundMove).isNotNull();
        int before = refundMove.get("beforeQuantity").asInt();
        int delta = refundMove.get("quantityDelta").asInt();
        int after = refundMove.get("afterQuantity").asInt();
        assertThat(after).isEqualTo(before + delta);
        assertThat(after).isEqualTo(10);
    }

    @Test
    @DisplayName("Returned stock is SELLABLE again: the next checkout can sell it")
    void restockedStockIsSellableAgain() throws Exception {

        ManagedStore store = seedStore("Refund Stock Store Six");
        TillOperator seller = employ(store.storeId(), "CASHIER", "Stock Ringr Six");
        TillOperator manager = employManager(store, "Stock Manager Six");
        openSession(seller.token());
        String category = createCategoryAsAdmin("Refund Stock Category Six");
        String product = listProductAs(store, category, "Resale Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String orderPublicId = counterPaidOrder(store, seller, line(product, 4));
        assertThat(refund(manager.token(), orderPublicId, refundBody(refundLine(product, null), "CASH"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        assertThat(inventoryQuantity(store, product)).isEqualTo(10);

        MvcResult resell = checkout(seller.token(), checkoutBody(line(product, 4), "CASH", ""));

        assertThat(resell.getResponse().getStatus()).isEqualTo(201);
        assertThat(inventoryQuantity(store, product)).isEqualTo(6);
    }

    private JsonNode findMovement(MvcResult movementsResult, String type) throws Exception {

        for (JsonNode move : jsonOf(movementsResult)) {
            if (move.get("type").asText().equals(type)) {
                return move;
            }
        }
        return null;
    }
}
