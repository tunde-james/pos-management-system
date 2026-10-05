package com.devtunde.posbackend.store.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(
        properties = {"app.security.rate-limit.limit=1000" // high — 19 tests' login volume must not trip the limiter
        })
@AutoConfigureMockMvc
class StoreApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper(); // Boot 4: no Jackson bean — own instance

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final String PASSWORD = "Password123!";

    // ---------- personas ----------

    private String uniqueEmail() {
        return "stest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";
    }

    private String uniqueStoreEmail() {
        return "store-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";
    }

    private void signupUser(String email, String fullName) throws Exception {
        String body = """
                      {
                        "fullName": "%s",
                        "email": "%s",
                        "password": "%s",
                        "phone": "08012345678"
                      }
                      """.formatted(fullName, email, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private String loginJson(String email) throws Exception {
        String body = """
                    {
                        "email": "%s",
                        "password": "%s"
                    }
                    """.formatted(email, PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return result.getResponse().getContentAsString();
    }

    private String loginForToken(String email) throws Exception {
        return objectMapper.readTree(loginJson(email)).get("accessToken").asText();
    }

    private String publicIdOf(String email) throws Exception {
        return objectMapper
                .readTree(loginJson(email))
                .get("user")
                .get("publicId")
                .asText();
    }

    private void promoteToAdmin(String email) {
        jdbcTemplate.update("update users set role = 'ROLE_ADMIN' where email = ?", email);
    }

    private String adminToken() throws Exception {
        String email = uniqueEmail();
        signupUser(email, "Store Admin");
        promoteToAdmin(email);
        return loginForToken(email); // token issued AFTER promotion carries ROLE_ADMIN
    }

    private String regularUserToken() throws Exception {
        String email = uniqueEmail();
        signupUser(email, "Regular User");
        return loginForToken(email);
    }

    /** A REAL user id to assign as store admin — ghost
     * UUIDs would 400 (Q7), so tests create reality. */
    private String anyManagerId() throws Exception {
        String email = uniqueEmail();
        signupUser(email, "Store Manager");
        return publicIdOf(email);
    }

    // ---------- store payloads ----------

    private String createStoreBody(String brand, String storeAdminPublicId, String contactEmail) {
        return """
                {
                    "brand": "%s",
                    "description": "Test store for the POS backend",
                    "storeType": "BRANCH",
                    "storeAdminPublicId": "%s",
                    "contact": {
                        "address": "12 Market Road, Lagos",
                        "phone": "08012345678",
                        "email": "%s"
                    }
                }
                """.formatted(brand, storeAdminPublicId, contactEmail);
    }

    /** Arrangement helper for non-create tests: creates
     * a store and returns its publicId. */
    private String createStoreAsAdmin(String adminToken, String assignedPublicId, String brand, String contactEmail)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStoreBody(brand, assignedPublicId, contactEmail)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("publicId")
                .asText();
    }

    // ---------- POST /api/v1/stores ----------

    @Test
    @DisplayName("POST /api/v1/stores without a token returns 401")
    void createWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStoreBody("Lone Store", UUID.randomUUID().toString(), uniqueStoreEmail())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/stores is forbidden for a regular user")
    void createForbiddenForRegularUser() throws Exception {
        String token = regularUserToken();

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStoreBody("Nope Store", UUID.randomUUID().toString(), uniqueStoreEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An admin creates a store: 201, ACTIVE status, server-set fields, normalized phone")
    void adminCreatesStoreReturns201WithActiveStatus() throws Exception {
        String ada = uniqueEmail();
        signupUser(ada, "Ada Admin");
        promoteToAdmin(ada);
        String adminToken = loginForToken(ada);

        String bolaId = anyManagerId();

        String contactEmail = uniqueStoreEmail();
        String body = """
                {
                    "brand": "Lekki Flagship",
                    "description": "The main store of the chain",
                    "storeType": "FLAGSHIP",
                    "storeAdminPublicId": "%s",
                    "contact": {
                        "address": "1 Admiralty Way, Lekki",
                        "phone": "08012345678",
                        "email": "%s"
                    }
                }
                """.formatted(bolaId, contactEmail);

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId", notNullValue()))
                .andExpect(jsonPath("$.brand").value("Lekki Flagship"))
                .andExpect(jsonPath("$.description").value("The main store of the chain"))
                .andExpect(jsonPath("$.storeType").value("FLAGSHIP"))
                .andExpect(jsonPath("$.status").value("ACTIVE")) // Q3: creation is the approval
                .andExpect(jsonPath("$.storeAdminPublicId").value(bolaId))
                .andExpect(jsonPath("$.contact.phone").value("+2348012345678")) // normalized via shared common code
                .andExpect(jsonPath("$.contact.email").value(contactEmail))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    @DisplayName("Creating a second store with the same brand returns 409")
    void createWithDuplicateBrandReturns409() throws Exception {
        String adminToken = adminToken();

        // first create — fine
        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStoreBody("Duplicate Brand Co", anyManagerId(), uniqueStoreEmail())))
                .andExpect(status().isCreated());

        // same brand, everything else different — still a duplicate
        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStoreBody("Duplicate Brand Co", anyManagerId(), uniqueStoreEmail())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Store already exists"));
    }

    @Test
    @DisplayName("Creating a second store with the same contact email returns 409")
    void createWithDuplicateContactEmailReturns409() throws Exception {
        String adminToken = adminToken();
        String sharedEmail = uniqueStoreEmail();

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStoreBody("First Email Store", anyManagerId(), sharedEmail)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStoreBody("Second Email Store", anyManagerId(), sharedEmail)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Store already exists"));
    }

    @Test
    @DisplayName("Creating a store with an unknown storeAdminPublicId returns 400")
    void createWithUnknownAdminIdReturns400() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStoreBody(
                                "Ghost Admin Store", UUID.randomUUID().toString(), uniqueStoreEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Store admin not found"));
    }

    @Test
    @DisplayName("Creating a second store for an admin who already runs one returns 409")
    void createWithAlreadyAssignedAdminReturns409() throws Exception {
        String adminToken = adminToken();
        String managerId = anyManagerId();

        createStoreAsAdmin(adminToken, managerId, "First Manager Store", uniqueStoreEmail());

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStoreBody("Second Manager Store", managerId, uniqueStoreEmail())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Store already exists"));
    }

    @Test
    @DisplayName("Creating a store with a blank brand returns 400 with a field error")
    void createWithBlankBrandReturns400() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStoreBody("", anyManagerId(), uniqueStoreEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("brand")));
    }

    @Test
    @DisplayName("Creating a store with an unknown storeType returns 400, never 500")
    void createWithUnknownStoreTypeReturns400() throws Exception {
        String adminToken = adminToken();

        String body = """
                {
                    "brand": "Mall of Nonsense",
                    "description": "Type is not a legal enum value",
                    "storeType": "MALL",
                    "storeAdminPublicId": "%s",
                    "contact": {
                        "address": "Nowhere Street",
                        "phone": "08012345678",
                        "email": "%s"
                    }
                }
                """.formatted(anyManagerId(), uniqueStoreEmail());

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET by id / list ----------

    @Test
    @DisplayName("GET /api/v1/stores/{id} returns the store for an admin")
    void getStoreByIdReturnsStoreForAdmin() throws Exception {
        String adminToken = adminToken();

        String publicId = createStoreAsAdmin(adminToken, anyManagerId(), "Gettable Store", uniqueStoreEmail());

        mockMvc.perform(get("/api/v1/stores/" + publicId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("Gettable Store"));
    }

    @Test
    @DisplayName("GET /api/v1/stores/{id} with an unknown id returns 404")
    void getByIdUnknownReturns404() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(get("/api/v1/stores/" + UUID.randomUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/stores (list) is forbidden for a regular user")
    void listStoresForbiddenForRegularUser() throws Exception {
        String token = regularUserToken();

        mockMvc.perform(get("/api/v1/stores").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/stores returns created stores for an admin")
    void listReturnsCreatedStoresForAdmin() throws Exception {
        String adminToken = adminToken();

        createStoreAsAdmin(adminToken, anyManagerId(), "Listable Store", uniqueStoreEmail());

        mockMvc.perform(get("/api/v1/stores").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[*].brand", hasItem("Listable Store")));
    }

    // ---------- GET /api/v1/stores/my-store ----------

    @Test
    @DisplayName("GET my-store returns the assigned manager's own store (one store per admin, Q9)")
    void myStoreReturnsOwnStoreForAssignedManager() throws Exception {
        String adminToken = adminToken();

        String managerEmail = uniqueEmail();
        signupUser(managerEmail, "Manager Tunde");
        String managerId = publicIdOf(managerEmail);
        String managerToken = loginForToken(managerEmail);

        createStoreAsAdmin(adminToken, managerId, "Managed Branch", uniqueStoreEmail());

        mockMvc.perform(get("/api/v1/stores/my-store").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("Managed Branch"))
                .andExpect(jsonPath("$.storeAdminPublicId").value(managerId));
    }

    @Test
    @DisplayName("GET my-store returns 404 for a caller who has no store")
    void myStoreReturns404WhenCallerHasNoStore() throws Exception {
        String token = regularUserToken();

        mockMvc.perform(get("/api/v1/stores/my-store").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ---------- PUT /api/v1/stores/{id} ----------

    @Test
    @DisplayName("Updating only the brand leaves every other field untouched (nulls are ignored)")
    void updateBrandOnlyLeavesOtherFieldsUntouched() throws Exception {
        String adminToken = adminToken();
        String contactEmail = uniqueStoreEmail();
        String publicId = createStoreAsAdmin(adminToken, anyManagerId(), "Old Brand", contactEmail);

        String body = """
                {
                    "brand": "Rebranded Store"
                }
                """.formatted();

        mockMvc.perform(put("/api/v1/stores/" + publicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("Rebranded Store"))
                .andExpect(jsonPath("$.description").value("Test store for the POS backend"))
                .andExpect(jsonPath("$.storeType").value("BRANCH"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.contact.phone").value("+2348012345678"))
                .andExpect(jsonPath("$.contact.email").value(contactEmail));
    }

    @Test
    @DisplayName("An admin can block a store via update status")
    void updateCanBlockStoreViaStatus() throws Exception {
        String adminToken = adminToken();
        String publicId = createStoreAsAdmin(adminToken, anyManagerId(), "Blockable Store", uniqueStoreEmail());

        String body = """
                {
                    "status": "BLOCKED"
                }
                """.formatted();

        mockMvc.perform(put("/api/v1/stores/" + publicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    @DisplayName("Updating a store that does not exist returns 404")
    void updateNonExistentStoreReturns404() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(put("/api/v1/stores/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Updating with a whitespace-only brand returns 400, but null still means unchanged")
    void updateWithWhitespaceBrandReturns400() throws Exception {
        String adminToken = adminToken();
        String publicId = createStoreAsAdmin(adminToken, anyManagerId(), "Whitespace Guard Store", uniqueStoreEmail());

        String body = """
                  {
                      "brand": "   "
                  }
                  """.formatted();

        mockMvc.perform(put("/api/v1/stores/" + publicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("brand")));
    }

    @Test
    @DisplayName("Updating the contact normalizes the phone to E.164, same as create")
    void updateContactNormalizesPhoneToE164() throws Exception {
        String adminToken = adminToken();
        String publicId = createStoreAsAdmin(adminToken, anyManagerId(), "Phone Format Store", uniqueStoreEmail());

        String body = """
                  {
                      "contact": {
                          "address": "45 New Address, Ikeja",
                          "phone": "08087654321",
                          "email": "%s"
                      }
                  }
                  """.formatted(uniqueStoreEmail());

        mockMvc.perform(put("/api/v1/stores/" + publicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.phone").value("+2348087654321"));
    }

    // ---------- DELETE /api/v1/stores/{id} ----------

    @Test
    @DisplayName("DELETE soft-deletes: 204, then GET shows status DELETED, not 404")
    void deleteSetsStatusToDeletedNotRemovesRow() throws Exception {
        String adminToken = adminToken();
        String publicId = createStoreAsAdmin(adminToken, anyManagerId(), "Doomed Store", uniqueStoreEmail());

        mockMvc.perform(delete("/api/v1/stores/" + publicId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // soft delete: the row survives for history — BLOCKED vs DELETED made real
        mockMvc.perform(get("/api/v1/stores/" + publicId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"));
    }

    @Test
    @DisplayName("DELETE is forbidden for a regular user")
    void deleteForbiddenForRegularUser() throws Exception {
        String adminToken = adminToken();
        String publicId = createStoreAsAdmin(adminToken, anyManagerId(), "Guarded Store", uniqueStoreEmail());
        String token = regularUserToken();

        mockMvc.perform(delete("/api/v1/stores/" + publicId).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
