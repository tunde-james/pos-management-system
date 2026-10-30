package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CheckoutApiTests extends AbstractOrderTest {

    private static final String CANONICAL_PHONE = "+2348093030303";
    private static final String LOCAL_PHONE = "08093030303";

    @Test
    @DisplayName("Cash checkout: 201, PAID, frozen kobo snapshot, session + operator attached")
    void cashCheckoutReturns201PaidWithSnapshot() throws Exception {

        ManagedStore store = seedStore("Checkout Store One");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Snapshot Cashier");
        String sessionId = openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category One");
        String product = listProductAs(store, category, "Full Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = checkout(cashier.token(), checkoutBody(line(product, 2), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("status").asText()).isEqualTo("PAID");

        assertThat(jsonOf(result).get("paymentMethod").asText()).isEqualTo("CASH");

        assertThat(jsonOf(result).get("totalKobo").asLong()).isEqualTo(2400000L);

        assertThat(jsonOf(result).get("displayTotal").asText()).isEqualTo("₦24000.00");

        assertThat(jsonOf(result).get("sessionPublicId").asText()).isEqualTo(sessionId);
        assertThat(jsonOf(result).get("paidAt")).isNotNull();

        assertThat(jsonOf(result).get("customerPublicId")).isNull();

        assertThat(jsonOf(result).get("lines").get(0).get("productName").asText())
                .isEqualTo("Full Milk");

        assertThat(jsonOf(result).get("lines").get(0).get("unitPriceKobo").asLong())
                .isEqualTo(1200000L);

        assertThat(jsonOf(result).get("lines").get(0).get("quantity").asInt()).isEqualTo(2);

        assertThat(jsonOf(result).get("lines").get(0).get("lineTotalKobo").asLong())
                .isEqualTo(2400000L);

        assertThat(jsonOf(result).get("lines").get(0).get("displayUnitPrice").asText())
                .isEqualTo("₦12000.00");

        assertThat(jsonOf(result).get("lines").get(0).get("displayLineTotal").asText())
                .isEqualTo("₦24000.00");
    }

    @Test
    @DisplayName("The ledger proves the sale: stock decremented, SALE movement written")
    void saleMovementAndStockDecrementProveTheLedger() throws Exception {

        ManagedStore store = seedStore("Checkout Store Two");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Ledger Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Two");
        String product = listProductAs(store, category, "Ledger Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        checkout(cashier.token(), checkoutBody(line(product, 3), "CASH", ""));

        assertThat(inventoryQuantity(store, product)).isEqualTo(7);

        MvcResult movements = mockMvc.perform(MockMvcRequestBuilders.get(
                                "/api/v1/stores/" + store.storeId() + "/inventory/products/" + product + "/movements")
                        .header("Authorization", "Bearer " + store.managerToken()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(jsonOf(movements).size()).isEqualTo(2);

        assertThat(jsonOf(movements).get(1).get("type").asText()).isEqualTo("SALE");

        assertThat(jsonOf(movements).get(1).get("quantityDelta").asInt()).isEqualTo(-3);
    }

    @Test
    @DisplayName("Card checkout with a terminal reference: 201, reference echoed")
    void cardCheckoutWithReferenceEchoes201() throws Exception {

        ManagedStore store = seedStore("Checkout Store Three");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Card Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Three");
        String product = listProductAs(store, category, "Card Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = checkout(
                cashier.token(), checkoutBody(line(product, 1), "CARD", ",\n \"paymentReference\": \"TERM-991\""));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("paymentMethod").asText()).isEqualTo("CARD");

        assertThat(jsonOf(result).get("paymentReference").asText()).isEqualTo("TERM-991");
    }

    @Test
    @DisplayName("Transfer checkout with a bank reference: 201, reference echoed")
    void transferCheckoutWithReferenceReturns201() throws Exception {

        ManagedStore store = seedStore("Checkout Store Four");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Transfer Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Four");
        String product = listProductAs(store, category, "Transfer Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = checkout(
                cashier.token(), checkoutBody(line(product, 1), "TRANSFER", ",\n \"paymentReference\": \"TRF-77321\""));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("paymentMethod").asText()).isEqualTo("TRANSFER");

        assertThat(jsonOf(result).get("paymentReference").asText()).isEqualTo("TRF-77321");
    }

    @Test
    @DisplayName("A canonical phone attaches the enrolled customer to the sale: 201")
    void checkoutAttachesCustomerByCanonicalPhone() throws Exception {

        ManagedStore store = seedStore("Checkout Store Five");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Loyal Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Five");
        String product = listProductAs(store, category, "Loyalty Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult enroll = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/customers")
                        .header("Authorization", "Bearer " + store.managerToken())
                        .header("Content-Type", "application/json")
                        .content("""
                                {
                                    "fullName": "Loyal Shopper",
                                    "phone": "%s"
                                }
                                """.formatted(CANONICAL_PHONE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        String customerPublicId = jsonOf(enroll).get("publicId").asText();

        MvcResult result = checkout(
                cashier.token(),
                checkoutBody(
                        line(product, 1), "CASH", ",\n      \"customerPhone\": \"%s\"".formatted(CANONICAL_PHONE)));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("customerPublicId").asText()).isEqualTo(customerPublicId);
    }

    @Test
    @DisplayName("The till may type the LOCAL phone format — normalized at the boundary (Q8): 201")
    void checkoutAttachesCustomerByLocalFormatPhone() throws Exception {

        ManagedStore store = seedStore("Checkout Store Six");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Local Format Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Six");
        String product = listProductAs(store, category, "Local Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult enroll = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/customers")
                        .header("Authorization", "Bearer " + store.managerToken())
                        .header("Content-Type", "application/json")
                        .content("""
                                {
                                    "fullName": "Local Format Shopper",
                                    "phone": "%s"
                                }
                                """.formatted(CANONICAL_PHONE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        String customerPublicId = jsonOf(enroll).get("publicId").asText();

        MvcResult result = checkout(
                cashier.token(),
                checkoutBody(line(product, 1), "CASH", ",\n      \"customerPhone\": \"%s\"".formatted(LOCAL_PHONE)));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("customerPublicId").asText()).isEqualTo(customerPublicId);
    }

    @Test
    @DisplayName("An unknown customer phone refuses the sale: 400 — never silently unattached")
    void checkoutWithUnknownCustomerPhoneReturns400() throws Exception {

        ManagedStore store = seedStore("Checkout Store Seven");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Strict Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Seven");
        String product = listProductAs(store, category, "Strict Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = checkout(
                cashier.token(),
                checkoutBody(line(product, 1), "CASH", ",\n      \"customerPhone\": \"+2348099990001\""));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(inventoryQuantity(store, product)).isEqualTo(10);
    }

    @Test
    @DisplayName("A delisted product may not be sold: 400")
    void delistedProductInBasketReturns400() throws Exception {

        ManagedStore store = seedStore("Checkout Store Eight");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Archive Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Eight");
        String product = listProductAs(store, category, "Delisted Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/products/store/" + store.storeId() + "/" + product)
                        .header("Authorization", "Bearer " + store.managerToken()))
                .andReturn();

        MvcResult result = checkout(cashier.token(), checkoutBody(line(product, 1), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("An unknown product in the basket: 400")
    void unknownProductInBasketReturns400() throws Exception {

        ManagedStore store = seedStore("Checkout Store Nine");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Careful Cashier");
        openSession(cashier.token());

        MvcResult result =
                checkout(cashier.token(), checkoutBody(line("00000000-0000-0000-0000-000000000009", 1), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Insufficient stock: 409 with per-line availability — nothing committed, no holds")
    void insufficientStockReturns409WithAvailabilityNothingCommitted() throws Exception {

        ManagedStore store = seedStore("Checkout Store Ten");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Shortfall Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Ten");
        String product = listProductAs(store, category, "Rare Milk", "15000.00", "12000.00");
        stockAs(store, product, 2);

        MvcResult result = checkout(cashier.token(), checkoutBody(line(product, 4), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);

        assertThat(jsonOf(result)
                        .get("shortfalls")
                        .get(0)
                        .get("productPublicId")
                        .asText())
                .isEqualTo(product);

        assertThat(jsonOf(result).get("shortfalls").get(0).get("requested").asInt())
                .isEqualTo(4);

        assertThat(jsonOf(result).get("shortfalls").get(0).get("available").asInt())
                .isEqualTo(2);
        assertThat(inventoryQuantity(store, product)).isEqualTo(2);
    }

    @Test
    @DisplayName("One short line voids the WHOLE basket: every line's stock untouched (atomicity)")
    void multiLineBasketOneShortReturns409AndNothingMoves() throws Exception {

        ManagedStore store = seedStore("Checkout Store Eleven");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Atomic Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Eleven");
        String milk = listProductAs(store, category, "Atomic Milk", "15000.00", "12000.00");
        String bread = listProductAs(store, category, "Atomic Bread", "15000.00", "12000.00");
        stockAs(store, milk, 10);
        stockAs(store, bread, 1);

        MvcResult result =
                checkout(cashier.token(), checkoutBody(line(milk, 2) + ",\n      " + line(bread, 4), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(inventoryQuantity(store, milk)).isEqualTo(10);
        assertThat(inventoryQuantity(store, bread)).isEqualTo(1);
    }

    @Test
    @DisplayName("Quantity zero: 400")
    void quantityZeroReturns400() throws Exception {

        ManagedStore store = seedStore("Checkout Store Twelve");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Zero Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Twelve");
        String product = listProductAs(store, category, "Zero Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = checkout(cashier.token(), checkoutBody(line(product, 0), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Quantity above 999: 400")
    void quantityAbove999Returns400() throws Exception {

        ManagedStore store = seedStore("Checkout Store Thirteen");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Bulk Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Thirteen");
        String product = listProductAs(store, category, "Bulk Milk", "15000.00", "12000.00");
        stockAs(store, product, 1000);

        MvcResult result = checkout(cashier.token(), checkoutBody(line(product, 1000), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("An empty basket: 400")
    void emptyBasketReturns400() throws Exception {

        ManagedStore store = seedStore("Checkout Store Fourteen");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Empty Cashier");
        openSession(cashier.token());

        MvcResult result = checkout(cashier.token(), checkoutBody("", "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Missing payment method: 400")
    void missingPaymentMethodReturns400() throws Exception {

        ManagedStore store = seedStore("Checkout Store Fifteen");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Forgetful Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Fifteen");
        String product = listProductAs(store, category, "Forgetful Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        String body = """
                   {
                       "lines": [%s]
                   }
                   """.formatted(line(product, 1));

        MvcResult result = checkout(cashier.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("A payment method outside CASH/CARD/TRANSFER: 400")
    void garbagePaymentMethodReturns400() throws Exception {

        ManagedStore store = seedStore("Checkout Store Sixteen");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Cheque Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Checkout Category Sixteen");
        String product = listProductAs(store, category, "Cheque Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = checkout(cashier.token(), checkoutBody(line(product, 1), "CHEQUE", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Checkout with no session at all: 409 — open a session first")
    void checkoutWithNoSessionReturns409() throws Exception {

        ManagedStore store = seedStore("Checkout Store Seventeen");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Sessionless Cashier");

        MvcResult result =
                checkout(cashier.token(), checkoutBody(line("00000000-0000-0000-0000-000000000009", 1), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Checkout against a CLOSED session: 409")
    void checkoutWithClosedSessionReturns409() throws Exception {

        ManagedStore store = seedStore("Checkout Store Eighteen");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Closed Session Cashier");
        String sessionId = openSession(cashier.token());
        postAction("/api/v1/sessions/" + sessionId + "/close", cashier.token());

        MvcResult result =
                checkout(cashier.token(), checkoutBody(line("00000000-0000-0000-0000-000000000009", 1), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Checkout against a LOCKED session (restroom break): 409")
    void checkoutAgainstLockedSessionReturns409() throws Exception {

        ManagedStore store = seedStore("Checkout Store Nineteen");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Restroom Break Cashier");
        String sessionId = openSession(cashier.token());
        postAction("/api/v1/sessions/" + sessionId + "/lock", cashier.token());

        MvcResult result =
                checkout(cashier.token(), checkoutBody(line("00000000-0000-0000-0000-000000000009", 1), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("A SALES_ATTENDANT may not check out: 403 (title gate)")
    void attendantCheckoutReturns403() throws Exception {

        ManagedStore store = seedStore("Checkout Store Twenty");
        TillOperator attendant = employ(store.storeId(), "SALES_ATTENDANT", "Checkout Attendant");

        MvcResult result =
                checkout(attendant.token(), checkoutBody(line("00000000-0000-0000-0000-000000000009", 1), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Anonymous checkout: 401")
    void anonymousCheckoutReturns401() throws Exception {

        MvcResult result = checkout(null, checkoutBody(line("00000000-0000-0000-0000-000000000009", 1), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
}
