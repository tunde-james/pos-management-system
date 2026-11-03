package com.devtunde.posbackend.order.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.BeforeEach;

import com.devtunde.posbackend.payments.api.GatewayVerifyStatus;

@SpringBootTest(properties = {"app.security.rate-limit.limit=1000", "app.payments.pending-ttl=400ms"})
public abstract class AbstractPaymentsTest extends AbstractOrderTest {

    /** Outlives the pinned 400ms TTL. */
    private static final long TTL_WAIT_MS = 700L;

    @Autowired
    protected FakePaymentGateway fakeGateway;

    @BeforeEach
    void resetFakeGateway() {
        fakeGateway.reset();
    }

    /** Steer the next verify; null amount =
     * whatever was initiated. */
    protected void stubVerify(GatewayVerifyStatus status, Long amountKobo) {
        fakeGateway.setNextVerify(status, amountKobo);
    }

    protected String pendingCheckoutBody(String linesJson, String paymentMethod, String provider) {
        return """
                {
                    "lines": [%s],
                    "paymentMethod": "%s",
                    "paymentProvider": "%s"
                }
                """.formatted(linesJson, paymentMethod, provider);
    }

    protected MvcResult pendingCheckout(String token, String linesJson, String paymentMethod, String provider)
            throws Exception {
        return checkout(token, pendingCheckoutBody(linesJson, paymentMethod, provider));
    }

    protected MvcResult verifyOrder(String token, String orderPublicId) throws Exception {
        return postAction("/api/v1/orders/" + orderPublicId + "/payment/verify", token);
    }

    protected MvcResult reviveOrder(String token, String orderPublicId) throws Exception {
        return postAction("/api/v1/orders/" + orderPublicId + "/payment/revive", token);
    }

    protected MvcResult pendingTray(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/orders/pending").header("Authorization", "Bearer " + token))
                .andReturn();
    }

    protected MvcResult pendingTrayByNumber(String token, String orderNumber) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/orders/pending?number=" + orderNumber).header("Authorization", "Bearer " + token))
                .andReturn();
    }

    protected MvcResult customerReceipt(String token, String orderPublicId) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/orders/" + orderPublicId + "/receipt").header("Authorization", "Bearer " + token))
                .andReturn();
    }

    protected MvcResult machineOrder(String token, String orderPublicId) throws Exception {
        return mockMvc.perform(get("/api/v1/orders/" + orderPublicId).header("Authorization", "Bearer " + token))
                .andReturn();
    }

    protected void waitForTtl() throws InterruptedException {
        Thread.sleep(TTL_WAIT_MS);
    }
}
