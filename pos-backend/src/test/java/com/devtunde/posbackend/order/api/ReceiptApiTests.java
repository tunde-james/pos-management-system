package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReceiptApiTests extends AbstractOrderTest {

    /** One full rung sale: store + cashier +
     * open session + stocked shelf. */
    private record RungSale(String orderPublicId, ManagedStore store, TillOperator cashier) {}

    private RungSale ringSale() throws Exception {

        ManagedStore store = seedStore("Receipt Store " + UUID.randomUUID());
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Receipt Cashier");
        openSession(cashier.token());
        String category = createCategoryAsAdmin("Receipt Category " + UUID.randomUUID());
        String product = listProductAs(store, category, "Receipt Milk", "15000.00", "12000.00");
        stockAs(store, product, 10);

        MvcResult result = checkout(cashier.token(), checkoutBody(line(product, 2), "CASH", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String orderPublicId = jsonOf(result).get("publicId").asText();

        return new RungSale(orderPublicId, store, cashier);
    }

    private MvcResult readReceipt(String token, String orderPublicId) throws Exception {

        return mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/orders/" + orderPublicId)
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    @Test
    @DisplayName("The chain admin reads a receipt: kobo truth AND naira display strings")
    void chainAdminReadsReceiptWithKoboAndNaira() throws Exception {

        RungSale sale = ringSale();

        MvcResult result = readReceipt(adminToken(), sale.orderPublicId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(result).get("status").asText()).isEqualTo("PAID");

        assertThat(jsonOf(result).get("totalKobo").asLong()).isEqualTo(2400000L);

        assertThat(jsonOf(result).get("displayTotal").asText()).isEqualTo("₦24000.00");

        assertThat(jsonOf(result).get("lines").get(0).get("unitPriceKobo").asLong())
                .isEqualTo(1200000L);

        assertThat(jsonOf(result).get("lines").get(0).get("displayUnitPrice").asText())
                .isEqualTo("₦12000.00");

        assertThat(jsonOf(result).get("lines").get(0).get("displayLineTotal").asText())
                .isEqualTo("₦24000.00");
    }

    @Test
    @DisplayName("The session operator reads their own receipt: 200")
    void sessionOperatorReadsOwnReceipt() throws Exception {

        RungSale sale = ringSale();

        MvcResult result = readReceipt(sale.cashier().token(), sale.orderPublicId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(result).get("publicId").asText()).isEqualTo(sale.orderPublicId());
    }

    @Test
    @DisplayName("The store's seat admin reads the receipt: 200")
    void seatAdminReadsReceipt() throws Exception {

        RungSale sale = ringSale();

        MvcResult result = readReceipt(sale.store().managerToken(), sale.orderPublicId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("A cashier from the same store but a different session: 403")
    void otherCashierReadsReceiptReturns403() throws Exception {

        RungSale sale = ringSale();
        TillOperator stranger = employ(sale.store().storeId(), "CASHIER", "Stranger Receipt Cashier");

        MvcResult result = readReceipt(stranger.token(), sale.orderPublicId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Anonymous receipt read: 401")
    void anonymousReadsReceiptReturns401() throws Exception {

        RungSale sale = ringSale();

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/orders/" + sale.orderPublicId()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("An unknown order: 404")
    void unknownOrderReturns404() throws Exception {

        MvcResult result = readReceipt(adminToken(), "00000000-0000-0000-0000-000000000009");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }
}
