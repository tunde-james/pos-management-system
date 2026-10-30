package com.devtunde.posbackend.order.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.devtunde.posbackend.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"app.security.rate-limit.limit=1000"})
@AutoConfigureMockMvc
public abstract class AbstractOrderTest extends AbstractIntegrationTest {

    protected static final String PASSWORD = "Password123!";

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    // ---------- personas ----------

    protected String uniqueEmail() {
        return "order." + UUID.randomUUID() + "@example.com";
    }

    protected void signupUser(String email, String fullName) throws Exception {

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

    protected String loginForToken(String email) throws Exception {

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

        return jsonOf(result).get("accessToken").asText();
    }

    protected String publicIdFor(String email) throws Exception {

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

        return jsonOf(result).get("user").get("publicId").asText();
    }

    protected String adminToken() throws Exception {

        String email = uniqueEmail();
        signupUser(email, "Chain Admin Persona");
        jdbcTemplate.update("update users set role = 'ROLE_ADMIN' where email = ?", email);

        return loginForToken(email);
    }

    // ---------- store + employment ----------

    protected record ManagedStore(String managerToken, String storeId) {}

    protected record TillOperator(String token, String userPublicId) {}

    protected String createStoreAsAdmin(String adminToken, String assignedPublicId, String brand) throws Exception {

        String body = """
                        {
                            "brand": "%s",
                            "description": "Order test store",
                            "storeType": "BRANCH",
                            "storeAdminPublicId": "%s",
                            "contact": {
                                "address": "12 Market Road, Lagos",
                                "phone": "08012345678",
                                "email": "%s"
                            }
                        }
                        """.formatted(brand, assignedPublicId, uniqueEmail());

        MvcResult result = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return jsonOf(result).get("publicId").asText();
    }

    protected ManagedStore seedStore(String brand) throws Exception {

        String email = uniqueEmail();
        signupUser(email, "Store Manager Persona");
        String managerToken = loginForToken(email);
        String managerId = publicIdFor(email);
        String storeId = createStoreAsAdmin(adminToken(), managerId, brand);

        return new ManagedStore(managerToken, storeId);
    }

    /**
     * Employment by database surgery (the 0013 pattern):
     * signing up a real
     * persona via the API, then writing the ACTIVE era
     * directly. Hiring via
     * the employee API would need the provisioned password from
     * Mailtrap.
     */
    protected TillOperator employ(String storeId, String jobTitle, String fullName) throws Exception {

        String email = uniqueEmail();
        signupUser(email, fullName);
        String token = loginForToken(email);
        String userPublicId = publicIdFor(email);

        jdbcTemplate.update("""
                           INSERT INTO store_employees
                               (public_id, store_id, user_public_id, job_title, hired_at, created_at, updated_at)
                           VALUES (?, ?, ?, ?, NOW(6), NOW(6), NOW(6))
                           """, UUID.randomUUID().toString(), storeId, userPublicId, jobTitle);

        return new TillOperator(token, userPublicId);
    }

    // ---------- catalog + stock ----------

    protected String createCategoryAsAdmin(String name) throws Exception {

        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "%s"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();

        return jsonOf(result).get("publicId").asText();
    }

    protected String uniqueSku() {
        return "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    protected String listProductAs(
            ManagedStore store, String categoryPublicId, String name, String mrp, String sellingPrice)
            throws Exception {

        String body = """
                        {
                            "name": "%s",
                            "sku": "%s",
                            "description": "Order suite item",
                            "brand": "TestBrand",
                            "image": "https://cdn.devtunde.com/item.png",
                            "categoryPublicId": "%s",
                            "mrp": %s,
                            "sellingPrice": %s
                        }
                        """.formatted(name, uniqueSku(), categoryPublicId, mrp, sellingPrice);

        MvcResult result = mockMvc.perform(post("/api/v1/products/store/" + store.storeId())
                        .header("Authorization", "Bearer " + store.managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return jsonOf(result).get("publicId").asText();
    }

    protected void stockAs(ManagedStore store, String productPublicId, int quantity) throws Exception {

        String body = """
                        {
                            "productPublicId": "%s",
                            "quantity": %d,
                            "lowStockThreshold": 1
                        }
                        """.formatted(productPublicId, quantity);

        mockMvc.perform(post("/api/v1/stores/" + store.storeId() + "/inventory")
                        .header("Authorization", "Bearer " + store.managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    protected int inventoryQuantity(ManagedStore store, String productPublicId) throws Exception {

        MvcResult result = mockMvc.perform(
                        get("/api/v1/stores/" + store.storeId() + "/inventory/products/" + productPublicId)
                                .header("Authorization", "Bearer " + store.managerToken()))
                .andExpect(status().isOk())
                .andReturn();

        return jsonOf(result).get("quantity").asInt();
    }

    // ---------- sessions + checkout ----------

    protected String openSession(String token) throws Exception {

        MvcResult result = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        return jsonOf(result).get("publicId").asText();
    }

    protected MvcResult postAction(String path, String token) throws Exception {

        return mockMvc.perform(post(path).header("Authorization", "Bearer " + token))
                .andReturn();
    }

    protected String line(String productPublicId, int quantity) {
        return """
                {
                    "productPublicId": "%s",
                    "quantity": %d
                }
                """.formatted(productPublicId, quantity);
    }

    protected String checkoutBody(String linesJson, String paymentMethod, String extrasJson) {

        return """
                {
                    "lines": [%s],
                    "paymentMethod": "%s"%s
                }
                """.formatted(linesJson, paymentMethod, extrasJson);
    }

    protected MvcResult checkout(String token, String body) throws Exception {

        MockHttpServletRequestBuilder request =
                post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body);

        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }

        return mockMvc.perform(request).andReturn();
    }

    // ---------- json ----------

    protected JsonNode jsonOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
