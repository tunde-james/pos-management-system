package com.devtunde.posbackend.inventory.internal.application;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.AbstractIntegrationTest;
import com.devtunde.posbackend.common.api.EmailService;
import com.devtunde.posbackend.common.api.email.LowStockAlert;
import com.devtunde.posbackend.common.internal.SmtpEmailService;
import com.devtunde.posbackend.inventory.internal.scheduler.LowStockAlertScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(
        properties = {
            "app.security.rate-limit.limit=1000" // high — lifecycle tests' login volume must not trip the limiter
        })
@AutoConfigureMockMvc
class InventoryLifecycleTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper(); // Boot 4: no Jackson bean — own instance

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StoreListingDelistedEventListener listener;

    @Autowired
    LowStockAlertScheduler scheduler;

    @MockitoBean
    EmailService emailService;

    private static final String PASSWORD = "Password123!";

    // ---------- personas ----------

    private String uniqueEmail() {
        return "ltest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";
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

    private String loginForToken(String email) throws Exception {
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

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private String publicIdOf(String email) throws Exception {
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

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("user")
                .get("publicId")
                .asText();
    }

    private void promoteToAdmin(String email) {
        jdbcTemplate.update("update users set role = 'ROLE_ADMIN' where email = ?", email);
    }

    private String adminToken() throws Exception {
        String email = uniqueEmail();
        signupUser(email, "Chain Admin");
        promoteToAdmin(email);
        return loginForToken(email);
    }

    private String createStoreAsAdmin(String adminToken, String assignedPublicId, String brand, String contactEmail)
            throws Exception {
        String body = """
                    {
                        "brand": "%s",
                        "description": "Lifecycle test store",
                        "storeType": "BRANCH",
                        "storeAdminPublicId": "%s",
                        "contact": {
                            "address": "12 Market Road, Lagos",
                            "phone": "08012345678",
                            "email": "%s"
                        }
                    }
                    """.formatted(brand, assignedPublicId, contactEmail);

        MvcResult result = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("publicId")
                .asText();
    }

    private static final class ManagedStore {
        final String managerToken;
        final String managerEmail;
        final String storeId;

        ManagedStore(String managerToken, String managerEmail, String storeId) {
            this.managerToken = managerToken;
            this.managerEmail = managerEmail;
            this.storeId = storeId;
        }
    }

    private ManagedStore seedStore(String brand) throws Exception {
        String email = uniqueEmail();
        signupUser(email, "Store Manager");
        String managerId = publicIdOf(email);
        String managerToken = loginForToken(email);
        String storeId = createStoreAsAdmin(adminToken(), managerId, brand, uniqueStoreEmail());
        return new ManagedStore(managerToken, email, storeId);
    }

    private String createCategoryAsAdmin(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("publicId")
                .asText();
    }

    private String uniqueSku() {
        return "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String listProductAs(ManagedStore store, String categoryPublicId, String name, String sku)
            throws Exception {
        String body = """
                    {
                        "name": "%s",
                        "sku": "%s",
                        "description": "Lifecycle test item",
                        "brand": "TestBrand",
                        "image": "https://cdn.devtunde.com/item.png",
                        "categoryPublicId": "%s",
                        "mrp": 15000.00,
                        "sellingPrice": 12000.00
                    }
                    """.formatted(name, sku, categoryPublicId);

        MvcResult result = mockMvc.perform(post("/api/v1/products/store/" + store.storeId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("publicId")
                .asText();
    }

    private void createInventoryAs(String token, String storeId, String productPublicId, int quantity, int threshold)
            throws Exception {
        mockMvc.perform(post("/api/v1/stores/" + storeId + "/inventory")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                    {
                                        "productPublicId": "%s",
                                        "quantity": %d,
                                        "lowStockThreshold": %d
                                    }
                                    """.formatted(productPublicId, quantity, threshold)))
                .andExpect(status().isCreated());
    }

    // ---------- event + archive lifecycle ----------

    @Test
    @DisplayName("Delisting archives the stock row end to end — event, executor, registry")
    void delistArchivesStockRowEndToEnd() throws Exception {
        ManagedStore store = seedStore("Event Archive Store");
        String categoryPublicId = createCategoryAsAdmin("Event Archive Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Archived Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        // the real path: DELETE publishes the event AFTER_COMMIT; the listener runs on the task executor

        mockMvc.perform(delete("/api/v1/products/store/" + store.storeId + "/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isNoContent());

        // async archive lands eventually — await, don't assume
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertNotNull(jdbcTemplate.queryForObject(
                        "select archived_at from inventories where store_id = ? and product_public_id = ?",
                        java.sql.Timestamp.class,
                        store.storeId,
                        productPublicId)));
    }

    @Test
    @DisplayName("The store inventory list filters archived rows at the DB level")
    void listFiltersArchivedRows() throws Exception {
        ManagedStore store = seedStore("Filter Store");
        String categoryPublicId = createCategoryAsAdmin("Filter Category");
        String keptId = listProductAs(store, categoryPublicId, "Kept Milk", uniqueSku());
        String archivedId = listProductAs(store, categoryPublicId, "Vanishing Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, keptId, 100, 10);
        createInventoryAs(store.managerToken, store.storeId, archivedId, 100, 10);

        jdbcTemplate.update(
                "update inventories set archived_at = NOW() where store_id = ? and product_public_id = ?",
                store.storeId,
                archivedId);

        mockMvc.perform(get("/api/v1/stores/" + store.storeId + "/inventory")
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productPublicId", hasItem(keptId)))
                .andExpect(jsonPath("$[*].productPublicId", not(hasItem(archivedId))));
    }

    @Test
    @DisplayName("Re-listing un-archives with an ADJUSTMENT movement — 201, never 409")
    void relistUnarchivesRowWithAdjustmentMovement() throws Exception {

        ManagedStore store = seedStore("Relist Store");
        String categoryPublicId = createCategoryAsAdmin("Relist Category");
        String sku = uniqueSku();
        String productPublicId = listProductAs(store, categoryPublicId, "Relisted Milk", sku);

        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        // the real closing path: delist (the async listener archives too — idempotent with the SQL below)
        mockMvc.perform(delete("/api/v1/products/store/" + store.storeId + "/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isNoContent());

        jdbcTemplate.update(
                "update inventories set archived_at = NOW() where store_id = ? and product_public_id = ?",
                store.storeId,
                productPublicId);

        // re-list the same master: same SKU finds it, the fresh listing is the only row

        mockMvc.perform(post("/api/v1/products/store/" + store.storeId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Relisted Milk",
                                    "sku": "%s",
                                    "description":
                                    "Lifecycle test item",
                                    "brand": "TestBrand",
                                    "image": "https://cdn.devtunde.com/item.png",
                                    "categoryPublicId": "%s",
                                    "mrp": 15000.00,
                                    "sellingPrice": 12000.00
                                }
                                """.formatted(sku, categoryPublicId)))
                .andExpect(status().isCreated());

        // restock the un-archived row: 201 with a new explicit count
        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productPublicId": "%s",
                                    "quantity": 80,
                                    "lowStockThreshold": 20
                                }
                                """.formatted(productPublicId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(80));

        mockMvc.perform(get("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/movements")
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("OPENING"))
                .andExpect(jsonPath("$[1].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$[1].beforeQuantity").value(100))
                .andExpect(jsonPath("$[1].afterQuantity").value(80));
    }

    // ---------- database-level suspenders (Q3) ----------

    @Test
    @DisplayName("The CHECK constraint rejects negative quantity even via direct SQL")
    void databaseRejectsNegativeQuantity() {
        DataAccessException ex = assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        "insert into inventories (store_id, product_public_id, quantity, low_stock_threshold, created_at, updated_at) values (?, ?, -5, 10, NOW(), NOW())",
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString()));

        // MySQL 3819 predates Spring's translation tables — pin that it IS the CHECK
        assertTrue(ex.getMessage().contains("Check constraint"));
    }

    @Test
    @DisplayName("The ledger CHECK rejects any row where before + delta != after")
    void databaseRejectsIncoherentLedgerRow() {
        DataAccessException ex = assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        "insert into stock_movements (store_id, product_public_id, movement_type, quantity_delta, before_quantity, after_quantity, reason, performed_by, created_at) values (?, ?, 'ADJUSTMENT', 5, 10, 20, null, ?, NOW())",
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString()));

        assertTrue(ex.getMessage().contains("Check constraint"));
    }

    // ---------- the morning scan ----------

    @Test
    @DisplayName("The scan emails the store admin one digest: LOW and OUT items, healthy excluded")
    void scanAlertsStoreAdminForLowAndOutStock() throws Exception {
        ManagedStore store = seedStore("Scan Brand Store");
        String categoryPublicId = createCategoryAsAdmin("Scan Category");
        listProductAs(store, categoryPublicId, "Healthy Milk", uniqueSku());
        createInventoryAs(
                store.managerToken,
                store.storeId,
                listProductAs(store, categoryPublicId, "Low Milk", uniqueSku()),
                8,
                10);
        createInventoryAs(
                store.managerToken,
                store.storeId,
                listProductAs(store, categoryPublicId, "Out Milk", uniqueSku()),
                0,
                10);

        scheduler.scanAndAlert();

        verify(emailService, times(1))
                .sendLowStockAlert(
                        eq(store.managerEmail),
                        eq("Scan Brand Store"),
                        argThat(alert -> alert.low().size() == 1
                                && alert.out().size() == 1
                                && "Low Milk".equals(alert.low().get(0).productName())
                                && "Out Milk".equals(alert.out().get(0).productName())));
    }

    @Test
    @DisplayName("The scan skips closed stores — the trading gate, applied to email")
    void scanSkipsClosedStores() throws Exception {
        ManagedStore alive = seedStore("Alive Scan Store");
        ManagedStore doomed = seedStore("Doomed Scan Store");
        String aliveCategory = createCategoryAsAdmin("Alive Scan Category");
        String doomedCategory = createCategoryAsAdmin("Doomed Scan Category");
        createInventoryAs(
                alive.managerToken,
                alive.storeId,
                listProductAs(alive, aliveCategory, "Alive Low Milk", uniqueSku()),
                8,
                10);
        createInventoryAs(
                doomed.managerToken,
                doomed.storeId,
                listProductAs(doomed, doomedCategory, "Doomed Low Milk", uniqueSku()),
                8,
                10);

        mockMvc.perform(delete("/api/v1/stores/" + doomed.storeId).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());

        scheduler.scanAndAlert();

        verify(emailService, times(1))
                .sendLowStockAlert(eq(alive.managerEmail), eq("Alive Scan Store"), org.mockito.ArgumentMatchers.any());
    }

    // ---------- email fail-soft ----------

    @Test
    @DisplayName("SmtpEmailService fails soft when no mail host is configured")
    @SuppressWarnings("unchecked")
    void emailServiceFailsSoftWithoutMailHost() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);

        when(provider.getIfAvailable()).thenReturn(null);

        SmtpEmailService service = new SmtpEmailService(provider);

        assertDoesNotThrow(() ->
                service.sendLowStockAlert("boss@devtunde.com", "Any Store", new LowStockAlert(List.of(), List.of())));
    }
}
