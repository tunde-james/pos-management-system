package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Pattern;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingCheckoutApiTests extends AbstractPaymentsTest {

    private static final String CANONICAL_PHONE = "+2348093030303";
    private static final Pattern ORDER_NUMBER = Pattern.compile("O-\\d{6}");

    @Test
    @DisplayName("Paystack checkout: 202, PENDING_PAYMENT, frozen kobo snapshot, slip shows how to pay")
    void paystackCheckoutReturns202PendingWithSlip() throws Exception {

        ManagedStore store = seedStore("Pending Store One");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Pending Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Pending Category One");
        String product = listProductAs(store, category, "Pending Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");

        assertThat(result.getResponse().getStatus()).isEqualTo(202);

        assertThat(jsonOf(result).get("status").asText()).isEqualTo("PENDING_PAYMENT");

        assertThat(jsonOf(result).get("provider").asText()).isEqualTo("PAYSTACK");

        assertThat(jsonOf(result).get("providerReference").asText()).startsWith("FAKE-PAYSTACK-");

        assertThat(jsonOf(result).get("paystack").get("authorizationUrl").asText())
                .startsWith("https://fake.paystack.test/checkout/");

        assertThat(jsonOf(result).get("monnify")).isNull();

        assertThat(jsonOf(result).get("totalKobo").asLong()).isEqualTo(1200000L);

        assertThat(jsonOf(result).get("displayTotal").asText()).isEqualTo("₦12000.00");

        assertThat(jsonOf(result).get("orderNumber").asText()).matches(ORDER_NUMBER.pattern());

        assertThat(jsonOf(result).get("expiresAt")).isNotNull();

        assertThat(jsonOf(result).get("paidAt")).isNull();

        assertThat(jsonOf(result).get("lines").get(0).get("lineTotalKobo").asLong())
                .isEqualTo(1200000L);
    }

    @Test
    @DisplayName("Monnify checkout: 202, slip carries the reserved account, no paystack block")
    void monnifyCheckoutReturns202PendingWithReservedAccount() throws Exception {

        ManagedStore store = seedStore("Pending Store Two");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Monnify Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Pending Category Two");
        String product = listProductAs(store, category, "Monnify Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "MONNIFY");

        assertThat(result.getResponse().getStatus()).isEqualTo(202);

        assertThat(jsonOf(result).get("provider").asText()).isEqualTo("MONNIFY");

        assertThat(jsonOf(result).get("providerReference").asText()).startsWith("FAKE-MONNIFY-");

        assertThat(jsonOf(result).get("monnify").get("accountNumber").asText()).isEqualTo("0123456789");

        assertThat(jsonOf(result).get("monnify").get("bankName").asText()).isEqualTo("Fake Bank");

        assertThat(jsonOf(result).get("monnify").get("bankCode").asText()).isEqualTo("001");

        assertThat(jsonOf(result).get("paystack")).isNull();
    }

    @Test
    @DisplayName("The pending slip does NOT touch the shelf: stock intact, no SALE movement")
    void pendingCheckoutDoesNotTouchShelfOrWriteMovements() throws Exception {

        ManagedStore store = seedStore("Pending Store Three");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "NoHolds Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Pending Category Three");
        String product = listProductAs(store, category, "NoHolds Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        pendingCheckout(cashier.token(), line(product, 4), "TRANSFER", "PAYSTACK");

        assertThat(inventoryQuantity(store, product)).isEqualTo(10);

        MvcResult movements = mockMvc.perform(MockMvcRequestBuilders.get(
                                "/api/v1/stores/" + store.storeId() + "/inventory/products/" + product + "/movements")
                        .header("Authorization", "Bearer " + store.managerToken()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(jsonOf(movements).size()).isEqualTo(1);

        assertThat(jsonOf(movements).get(0).get("type").asText()).isEqualTo("OPENING");
    }

    @Test
    @DisplayName("No holds: the very next customer can buy what the pending slip wants")
    void pendingCheckoutReservesNothingNextSaleStillSucceeds() throws Exception {

        ManagedStore store = seedStore("Pending Store Four");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Second Customer Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Pending Category Four");
        String product = listProductAs(store, category, "Contested Milk", "15000.00", "12000.00");
        stockAs(store, product, 4);

        MvcResult pending = pendingCheckout(cashier.token(), line(product, 4), "TRANSFER", "PAYSTACK");

        assertThat(pending.getResponse().getStatus()).isEqualTo(202);

        MvcResult cashSale = checkout(cashier.token(), checkoutBody(line(product, 4), "CASH", ""));

        assertThat(cashSale.getResponse().getStatus()).isEqualTo(201);
        assertThat(inventoryQuantity(store, product)).isEqualTo(0);
    }

    @Test
    @DisplayName("Coexistence pin: no provider = today's counter-settled 201 PAID, byte-for-byte")
    void counterCheckoutWithoutProviderUnchanged() throws Exception {

        ManagedStore store = seedStore("Pending Store Five");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Counter Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Pending Category Five");
        String product = listProductAs(store, category, "Counter Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = checkout(cashier.token(), checkoutBody(line(product, 1), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(jsonOf(result).get("status").asText()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("Cash has no gateway: provider + CASH = 400")
    void cashWithProviderReturns400() throws Exception {

        ManagedStore store = seedStore("Pending Store Six");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Cash Only Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Pending Category Six");
        String product = listProductAs(store, category, "CashOnly Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = pendingCheckout(cashier.token(), line(product, 1), "CASH", "PAYSTACK");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("A provider outside PAYSTACK/MONNIFY: 400")
    void garbageProviderReturns400() throws Exception {

        ManagedStore store = seedStore("Pending Store Seven");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Garbage Provider Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Pending Category Seven");
        String product = listProductAs(store, category, "Garbage Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "FLUTTERWAVE");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Gateway checkout with no session at all: 409")
    void providerCheckoutWithoutSessionReturns409() throws Exception {

        ManagedStore store = seedStore("Pending Store Eight");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Sessionless Gateway Cashier");
        String category = createCategoryAsAdmin("Pending Category Eight");
        String product = listProductAs(store, category, "Sessionless Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Gateway checkout against a LOCKED session: 409")
    void providerCheckoutAgainstLockedSessionReturns409() throws Exception {

        ManagedStore store = seedStore("Pending Store Nine");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Locked Gateway Cashier");
        String sessionId = openSession(cashier.token());
        postAction("/api/v1/sessions/" + sessionId + "/lock", cashier.token());
        String category = createCategoryAsAdmin("Pending Category Nine");
        String product = listProductAs(store, category, "Locked Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = pendingCheckout(cashier.token(), line(product, 1), "TRANSFER", "PAYSTACK");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("The title gate applies to the gateway door too: SALES_ATTENDANT = 403")
    void attendantProviderCheckoutReturns403() throws Exception {

        ManagedStore store = seedStore("Pending Store Ten");
        TillOperator attendant = employ(store.storeId(), "SALES_ATTENDANT", "Gateway Attendant");
        String category = createCategoryAsAdmin("Pending Category Ten");
        String product = listProductAs(store, category, "Attendant Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = pendingCheckout(attendant.token(), line(product, 1), "TRANSFER", "PAYSTACK");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Anonymous gateway checkout: 401")
    void anonymousProviderCheckoutReturns401() throws Exception {

        MvcResult result =
                pendingCheckout(null, line("00000000-0000-0000-0000-000000000009", 1), "TRANSFER", "PAYSTACK");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("A linked customer rides the order by id — the slip shows no customer identity")
    void pendingCheckoutAttachesCustomerByIdOnly() throws Exception {

        ManagedStore store = seedStore("Pending Store Eleven");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Loyal Gateway Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Pending Category Eleven");
        String product = listProductAs(store, category, "Loyal Gateway Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult enroll = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/customers")
                        .header("Authorization", "Bearer " + store.managerToken())
                        .header("Content-Type", "application/json")
                        .content("""
                                   {
                                       "fullName":
 "Loyal Gateway Shopper",
                                       "phone": "%s"
                                   }

 """.formatted(CANONICAL_PHONE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        String customerPublicId = jsonOf(enroll).get("publicId").asText();

        String body = """
                   {
                       "lines": [%s],
                       "paymentMethod": "TRANSFER",
                       "paymentProvider":
 "PAYSTACK",
                       "customerPhone": "%s"
                   }
                   """.formatted(line(product, 1), CANONICAL_PHONE);

        MvcResult result = checkout(cashier.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(202);

        assertThat(jsonOf(result).get("customerPublicId").asText()).isEqualTo(customerPublicId);

        assertThat(jsonOf(result).get("customerName")).isNull();

        assertThat(jsonOf(result).get("customer")).isNull();
    }

    @Test
    @DisplayName("Every order number is unique, formatted, and speakable")
    void pendingOrderNumbersAreUniqueAndFormatted() throws Exception {

        ManagedStore store = seedStore("Pending Store Twelve");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Numbered Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Pending Category Twelve");
        String first = listProductAs(store, category, "Numbered Milk A", "15000.00", "12000.00");
        String second = listProductAs(store, category, "Numbered Milk B", "15000.00", "12000.00");
        stockAs(store, first, 10);
        stockAs(store, second, 10);

        String firstNumber = jsonOf(pendingCheckout(cashier.token(), line(first, 1), "TRANSFER", "PAYSTACK"))
                .get("orderNumber")
                .asText();
        String secondNumber = jsonOf(pendingCheckout(cashier.token(), line(second, 1), "TRANSFER", "PAYSTACK"))
                .get("orderNumber")
                .asText();

        assertThat(firstNumber).matches(ORDER_NUMBER.pattern());

        assertThat(secondNumber).matches(ORDER_NUMBER.pattern());

        assertThat(firstNumber).isNotEqualTo(secondNumber);
    }
}
