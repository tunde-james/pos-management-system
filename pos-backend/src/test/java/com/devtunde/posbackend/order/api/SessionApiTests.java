package com.devtunde.posbackend.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionApiTests extends AbstractOrderTest {

    @Test
    @DisplayName("A cashier opens a session: 201, ACTIVE, at their employing store")
    void cashierOpensSessionReturns201ActiveAtTheirStore() throws Exception {

        ManagedStore store = seedStore("Session Store One");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Ada the Cashier");

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/sessions")
                                .header("Authorization", "Bearer " + cashier.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.operatorPublicId").value(cashier.userPublicId()))
                .andExpect(jsonPath("$.storePublicId").value(store.storeId()))
                .andReturn();

        assertThat(jsonOf(result).get("closedAt")).isNull();
    }

    @Test
    @DisplayName("A manager may also open a session: 201")
    void managerOpensSessionReturns201() throws Exception {

        ManagedStore store = seedStore("Session Store Two");
        TillOperator manager = employ(store.storeId(), "MANAGER", "Musa the Manager");

        mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("A second open while the first session is non-CLOSED: 409")
    void secondOpenWhileNonClosedReturns409() throws Exception {

        ManagedStore store = seedStore("Session Store Three");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Double Cashier");

        openSession(cashier.token());

        mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer " + cashier.token()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("After closing, the same operator may open a fresh session: 201")
    void reopenAfterCloseReturns201() throws Exception {

        ManagedStore store = seedStore("Session Store Four");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Returning Cashier");

        String sessionId = openSession(cashier.token());
        postAction("/api/v1/sessions/" + sessionId + "/close", cashier.token());

        mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer " + cashier.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("A SALES_ATTENDANT may not open a session: 403 (title gate)")
    void salesAttendantOpenReturns403() throws Exception {

        ManagedStore store = seedStore("Session Store Five");
        TillOperator attendant = employ(store.storeId(), "SALES_ATTENDANT", "Shelf Attendant");

        mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer " + attendant.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A user with no employment anywhere may not open a session: 403")
    void noEmploymentOpenReturns403() throws Exception {

        String email = uniqueEmail();
        signupUser(email, "Walk-in Stranger");
        String token = loginForToken(email);

        mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anonymous session open: 401")
    void anonymousOpenReturns401() throws Exception {

        mockMvc.perform(post("/api/v1/sessions")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Locking your own ACTIVE session: 200 and LOCKED")
    void lockOwnActiveSessionReturns200Locked() throws Exception {

        ManagedStore store = seedStore("Session Store Six");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Locking Cashier");

        String sessionId = openSession(cashier.token());

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/lock")
                        .header("Authorization", "Bearer " + cashier.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    @DisplayName("Resuming your own LOCKED session: 200 and ACTIVE")
    void resumeOwnLockedSessionReturns200Active() throws Exception {

        ManagedStore store = seedStore("Session Store Seven");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Restroom Cashier");

        String sessionId = openSession(cashier.token());
        postAction("/api/v1/sessions/" + sessionId + "/lock", cashier.token());

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/resume")
                        .header("Authorization", "Bearer " + cashier.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Locking a LOCKED session again: 409")
    void doubleLockReturns409() throws Exception {

        ManagedStore store = seedStore("Session Store Eight");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Repeat Lock Cashier");

        String sessionId = openSession(cashier.token());
        postAction("/api/v1/sessions/" + sessionId + "/lock", cashier.token());

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/lock")
                        .header("Authorization", "Bearer " + cashier.token()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Resuming a session that is already ACTIVE: 409")
    void resumeActiveSessionReturns409() throws Exception {

        ManagedStore store = seedStore("Session Store Nine");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Eager Cashier");

        String sessionId = openSession(cashier.token());

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/resume")
                        .header("Authorization", "Bearer " + cashier.token()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Closing your own ACTIVE session: 200, CLOSED, closedAt stamped")
    void closeActiveSessionReturns200Closed() throws Exception {

        ManagedStore store = seedStore("Session Store Ten");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Closing Cashier");

        String sessionId = openSession(cashier.token());

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(
                        post("/api/v1/sessions/" + sessionId + "/close")
                                .header("Authorization", "Bearer " + cashier.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andReturn();

        assertThat(jsonOf(result).get("closedAt")).isNotNull();
    }

    @Test
    @DisplayName("Closing a LOCKED session: 409 (resume first, then close)")
    void closeLockedSessionReturns409() throws Exception {

        ManagedStore store = seedStore("Session Store Eleven");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Clasped Cashier");

        String sessionId = openSession(cashier.token());
        postAction("/api/v1/sessions/" + sessionId + "/lock", cashier.token());

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/close")
                        .header("Authorization", "Bearer " + cashier.token()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Locking another operator's session: 403")
    void lockAnotherOperatorsSessionReturns403() throws Exception {

        ManagedStore store = seedStore("Session Store Twelve");
        TillOperator owner = employ(store.storeId(), "CASHIER", "Session Owner");
        TillOperator intruder = employ(store.storeId(), "CASHIER", "Intruder Cashier");

        String sessionId = openSession(owner.token());

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/lock")
                        .header("Authorization", "Bearer " + intruder.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The chain admin reads a session: 200 with operator and store")
    void chainAdminReadsSessionReturns200() throws Exception {

        ManagedStore store = seedStore("Session Store Thirteen");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Readable Cashier");

        String sessionId = openSession(cashier.token());

        mockMvc.perform(get("/api/v1/sessions/" + sessionId).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.operatorPublicId").value(cashier.userPublicId()));
    }

    @Test
    @DisplayName("The session operator reads their own session: 200")
    void operatorReadsOwnSessionReturns200() throws Exception {

        ManagedStore store = seedStore("Session Store Fourteen");
        TillOperator cashier = employ(store.storeId(), "CASHIER", "Own Session Cashier");

        String sessionId = openSession(cashier.token());

        mockMvc.perform(get("/api/v1/sessions/" + sessionId).header("Authorization", "Bearer " + cashier.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(sessionId));
    }

    @Test
    @DisplayName("A different cashier reads another operator's session: 403")
    void otherOperatorReadsSessionReturns403() throws Exception {

        ManagedStore store = seedStore("Session Store Fifteen");
        TillOperator owner = employ(store.storeId(), "CASHIER", "Private Session Owner");
        TillOperator stranger = employ(store.storeId(), "CASHIER", "Stranger Cashier");

        String sessionId = openSession(owner.token());

        mockMvc.perform(get("/api/v1/sessions/" + sessionId).header("Authorization", "Bearer " + stranger.token()))
                .andExpect(status().isForbidden());
    }
}
