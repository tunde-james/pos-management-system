package com.devtunde.posbackend.order.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.devtunde.posbackend.payments.api.GatewayVerifyStatus;

public abstract class AbstractRefundTest extends AbstractPaymentsTest {

    protected static final String UNKNOWN_ORDER = "00000000-0000-0000-0000-000000000001";

    protected TillOperator employManager(ManagedStore store, String fullName) throws Exception {
        return employ(store.storeId(), "MANAGER", fullName);
    }

    /** CASH checkout — the order is born PAID. */
    protected String counterPaidOrder(ManagedStore store, TillOperator cashier, String linesJson) throws Exception {

        MvcResult paid = checkout(cashier.token(), checkoutBody(linesJson, "CASH", ""));
        if (paid.getResponse().getStatus() != 201) {
            throw new IllegalStateException(
                    "expected a 201 counter checkout, got " + paid.getResponse().getStatus());
        }
        return jsonOf(paid).get("publicId").asText();
    }

    /** Pending slip + bank says yes — the order settles PAID
     * via the gateway. */
    protected String gatewayPaidOrder(ManagedStore store, TillOperator cashier, String linesJson, String provider)
            throws Exception {

        MvcResult slip = pendingCheckout(cashier.token(), linesJson, "TRANSFER", provider);
        if (slip.getResponse().getStatus() != 202) {
            throw new IllegalStateException(
                    "expected a 202 pending checkout, got " + slip.getResponse().getStatus());
        }
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        stubVerify(GatewayVerifyStatus.SUCCESS, null);
        MvcResult paid = verifyOrder(cashier.token(), orderPublicId);
        if (paid.getResponse().getStatus() != 200) {
            throw new IllegalStateException(
                    "expected a 200 verify, got " + paid.getResponse().getStatus());
        }
        return orderPublicId;
    }

    /** Gateway declines — the order settles FAILED. */
    protected String gatewayFailedOrder(ManagedStore store, TillOperator cashier, String linesJson) throws Exception {

        MvcResult slip = pendingCheckout(cashier.token(), linesJson, "TRANSFER", "PAYSTACK");
        String orderPublicId = jsonOf(slip).get("publicId").asText();

        stubVerify(GatewayVerifyStatus.FAILED, null);
        MvcResult failed = verifyOrder(cashier.token(), orderPublicId);
        if (failed.getResponse().getStatus() != 200) {
            throw new IllegalStateException(
                    "expected a 200 verify, got " + failed.getResponse().getStatus());
        }
        return orderPublicId;
    }

    /** The listing price changes after the sale — refunds must
     * not see it. */
    protected void changeSellingPrice(ManagedStore store, String productPublicId, String sellingPrice)
            throws Exception {

        mockMvc.perform(patch("/api/v1/products/store/" + store.storeId() + "/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellingPrice\": %s}".formatted(sellingPrice)))
                .andExpect(status().isOk());
    }

    protected void delist(ManagedStore store, String productPublicId) throws Exception {

        mockMvc.perform(delete("/api/v1/products/store/" + store.storeId() + "/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken()))
                .andExpect(status().isNoContent());
    }

    /** One refund line; disposition omitted = RESTOCKED. */
    protected String refundLine(String productPublicId, String disposition) {

        String disp = disposition == null ? "" : ", \"disposition\": \"%s\"".formatted(disposition);
        return """
                   {
                       "productPublicId": "%s"%s
                   }
                   """.formatted(productPublicId, disp);
    }

    /** A refund body; returnMethod omitted =
     * counter-settled-by-cash tests fail it. */
    protected String refundBody(String linesJson, String returnMethod) {

        String method = returnMethod == null ? "" : ", \"returnMethod\": \"%s\"".formatted(returnMethod);
        return """
                   {
                       "lines": [%s]%s
                   }
                   """.formatted(linesJson, method);
    }

    protected MvcResult refund(String token, String orderPublicId, String body) throws Exception {

        return mockMvc.perform(post("/api/v1/orders/" + orderPublicId + "/refunds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    protected MvcResult movements(ManagedStore store, String productPublicId) throws Exception {

        return mockMvc.perform(get("/api/v1/stores/" + store.storeId()
                                + "/inventory/products/" + productPublicId + "/movements")
                        .header("Authorization", "Bearer " + store.managerToken()))
                .andExpect(status().isOk())
                .andReturn();
    }
}
