package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.UUID;

import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class CustomerReceiptApiTests extends AbstractOrderTest {

    private record RungSale(String orderPublicId, ManagedStore store, TillOperator cashier, String brand) {}

    private RungSale ringSale() throws Exception {

        String brand = "Receipt View Store " + UUID.randomUUID();
        ManagedStore store = seedStore(brand);
        TillOperator cashier = employ(store.storeId(), "CASHIER", "View Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Receipt View Category " + UUID.randomUUID());
        String product = listProductAs(store, category, "View Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = checkout(cashier.token(), checkoutBody(line(product, 2), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        return new RungSale(jsonOf(result).get("publicId").asText(), store, cashier, brand);
    }

    private MvcResult readCustomerReceipt(String token, String orderPublicId) throws Exception {

        return mockMvc.perform(
                        get("/api/v1/orders/" + orderPublicId + "/receipt").header("Authorization", "Bearer " + token))
                .andReturn();
    }

    @Test
    @DisplayName("The customer receipt: items, quantities, amounts, total — no kobo, no product ids")
    void customerReceiptShowsItemsAndTotalOnly() throws Exception {

        RungSale sale = ringSale();

        MvcResult result = readCustomerReceipt(sale.cashier().token(), sale.orderPublicId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode body = jsonOf(result);

        assertThat(body.get("storeName").asText()).isEqualTo(sale.brand());

        assertThat(body.get("cashierName").asText()).isEqualTo("View Cashier");

        assertThat(body.get("paymentMethod").asText()).isEqualTo("CASH");
        assertThat(body.get("paidAt")).isNotNull();

        assertThat(body.get("total").asText()).isEqualTo("₦24000.00");

        JsonNode line = body.get("lines").get(0);

        assertThat(line.get("name").asText()).isEqualTo("View Milk");

        assertThat(line.get("quantity").asInt()).isEqualTo(2);

        assertThat(line.get("amount").asText()).isEqualTo("₦24000.00");

        // The customer view carries NO machine fields.
        assertThat(body.get("totalKobo")).isNull();

        assertThat(line.get("productPublicId")).isNull();

        assertThat(line.get("unitPriceKobo")).isNull();
    }

    @Test
    @DisplayName("The chain admin reads the customer receipt: 200")
    void chainAdminReadsCustomerReceipt() throws Exception {

        RungSale sale = ringSale();

        MvcResult result = readCustomerReceipt(adminToken(), sale.orderPublicId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("The store's seat admin reads the customer receipt: 200")
    void seatAdminReadsCustomerReceipt() throws Exception {

        RungSale sale = ringSale();

        MvcResult result = readCustomerReceipt(sale.store().managerToken(), sale.orderPublicId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("A cashier who did not serve the sale: 403")
    void otherCashierReadsCustomerReceiptReturns403() throws Exception {

        RungSale sale = ringSale();
        TillOperator stranger = employ(sale.store().storeId(), "CASHIER", "Stranger View Cashier");

        MvcResult result = readCustomerReceipt(stranger.token(), sale.orderPublicId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("An unknown order's customer receipt: 404")
    void unknownOrderCustomerReceiptReturns404() throws Exception {

        MvcResult result = readCustomerReceipt(adminToken(), "00000000-0000-0000-0000-000000000009");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }
}
