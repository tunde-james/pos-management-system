package com.devtunde.posbackend.inventory.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
        properties = {"app.security.rate-limit.limit=1000" // high — 20 tests' login volume must not trip the limiter
        })
@AutoConfigureMockMvc
class InventoryApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper(); // Boot 4: no Jackson bean — own instance

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final String PASSWORD = "Password123!";

    // ---------- personas ----------

    private String uniqueEmail() {
        return "itest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";
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
        signupUser(email, "Chain Admin");
        promoteToAdmin(email);

        return loginForToken(email); // token issued AFTER promotion carries ROLE_ADMIN
    }

    private String regularUserToken() throws Exception {

        String email = uniqueEmail();
        signupUser(email, "Regular User");

        return loginForToken(email);
    }

    // ---------- arrangement: store + catalog ----------

    /** A store with its OWN admin — the pairing
     * inventory tests need everywhere. */
    private static final class ManagedStore {
        final String managerToken;
        final String storeId;

        ManagedStore(String managerToken, String storeId) {
            this.managerToken = managerToken;
            this.storeId = storeId;
        }
    }

    private String createStoreAsAdmin(String adminToken, String assignedPublicId, String brand, String contactEmail)
            throws Exception {

        String body = """
                        {
                            "brand": "%s",
                            "description": "Inventory test store",
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

    private ManagedStore seedStore(String brand) throws Exception {

        String email = uniqueEmail();
        signupUser(email, "Store Manager");
        String managerId = publicIdOf(email);
        String managerToken = loginForToken(email);
        String storeId = createStoreAsAdmin(adminToken(), managerId, brand, uniqueStoreEmail());

        return new ManagedStore(managerToken, storeId);
    }

    private String createCategoryAsAdmin(String name) throws Exception {

        String adminToken = adminToken();

        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                    {
                                        "name": "%s"
                                    }
                                    """.formatted(name)))
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

    private String productBody(String name, String sku, String categoryPublicId, String mrp, String sellingPrice) {

        return """
                {
                    "name": "%s",
                    "sku": "%s",
                    "description": "Test item for the inventory suite",
                    "brand": "TestBrand",
                    "image": "https://cdn.devtunde.com/item.png",
                    "categoryPublicId": "%s",
                    "mrp": %s,
                    "sellingPrice": %s
                }
                """.formatted(name, sku, categoryPublicId, mrp, sellingPrice);
    }

    /** Arrangement helper: a store admin lists
     * one product, returns the product publicId. */
    private String listProductAs(ManagedStore store, String categoryPublicId, String name, String sku)
            throws Exception {

        MvcResult result = mockMvc.perform(post("/api/v1/products/store/" + store.storeId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(name, sku, categoryPublicId, "15000.00", "12000.00")))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("publicId")
                .asText();
    }

    // ---------- arrangement: stock ----------

    private String inventoryBody(String productPublicId, int quantity, int lowStockThreshold) {

        return """
                {
                    "productPublicId": "%s",
                    "quantity": %d,
                    "lowStockThreshold": %d
                }
                """.formatted(productPublicId, quantity, lowStockThreshold);
    }

    /** Create stock as the store's own admin —
     * the
     * realistic actor for shelf operations. */
    private void createInventoryAs(String token, String storeId, String productPublicId, int quantity, int threshold)
            throws Exception {

        mockMvc.perform(post("/api/v1/stores/" + storeId + "/inventory")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inventoryBody(productPublicId, quantity, threshold)))
                .andExpect(status().isCreated());
    }

    // ---------- create + listing gate ----------

    @Test
    @DisplayName("A store admin creates stock for a live listing: 201, quantity and threshold echo")
    void createInventoryForExistingListingReturns201() throws Exception {

        ManagedStore store = seedStore("Stocked Store");
        String categoryPublicId = createCategoryAsAdmin("Stock Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Full Milk", uniqueSku());

        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inventoryBody(productPublicId, 100, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productPublicId").value(productPublicId))
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.lowStockThreshold").value(10));
    }

    @Test
    @DisplayName("Creating stock for an unknown product 404s — the listing gate first")
    void createInventoryForUnknownListingReturns404() throws Exception {

        ManagedStore store = seedStore("Gate Store");

        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inventoryBody(UUID.randomUUID().toString(), 100, 10)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    @Test
    @DisplayName("Creating stock twice for the same pair 409s")
    void createInventoryDuplicatePairReturns409() throws Exception {

        ManagedStore store = seedStore("Duplicate Pair Store");

        String categoryPublicId = createCategoryAsAdmin("Duplicate Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Duplicated Milk", uniqueSku());

        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inventoryBody(productPublicId, 50, 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Inventory already exists"));
    }

    @Test
    @DisplayName("A closed store is invisible even to the chain admin: stock ops 404")
    void createInventoryForClosedStoreReturns404() throws Exception {

        ManagedStore store = seedStore("Doomed Inventory Store");

        String categoryPublicId = createCategoryAsAdmin("Closing Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Last Milk", uniqueSku());

        // DELETE /api/v1/stores/{id} is the one closing path (0010)
        mockMvc.perform(delete("/api/v1/stores/" + store.storeId).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inventoryBody(productPublicId, 100, 10)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Store not found"));
    }

    // ---------- pair reads + stocktake ----------

    @Test
    @DisplayName("GET by pair returns the current state")
    void getInventoryByPairReturnsState() throws Exception {

        ManagedStore store = seedStore("Readable Store");

        String categoryPublicId = createCategoryAsAdmin("Readable Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Readable Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        mockMvc.perform(get("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.lowStockThreshold").value(10));
    }

    @Test
    @DisplayName("GET by pair with a live listing but no stock row 404s as Inventory not found")
    void getInventoryUnknownPairReturns404() throws Exception {

        ManagedStore store = seedStore("Empty Shelf Store");

        String categoryPublicId = createCategoryAsAdmin("Empty Shelf Category");
        String productPublicId =
                listProductAs(store, categoryPublicId, "Unstocked Milk", uniqueSku()); // listed, never stocked

        mockMvc.perform(get("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Inventory not found"));
    }

    @Test
    @DisplayName("Stocktake sets the absolute quantity and records an ADJUSTMENT movement")
    void stocktakeSetsQuantityAndRecordsAdjustmentMovement() throws Exception {

        ManagedStore store = seedStore("Counted Store");

        String categoryPublicId = createCategoryAsAdmin("Counted Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Counted Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        mockMvc.perform(put("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(60));

        // the ledger: OPENING then ADJUSTMENT with an honest before/after chain
        mockMvc.perform(get("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/movements")
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("OPENING"))
                .andExpect(jsonPath("$[0].beforeQuantity").value(0))
                .andExpect(jsonPath("$[0].afterQuantity").value(100))
                .andExpect(jsonPath("$[1].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$[1].beforeQuantity").value(100))
                .andExpect(jsonPath("$[1].afterQuantity").value(60));
    }

    @Test
    @DisplayName("Stocktake with a negative quantity 400s with a field error")
    void stocktakeWithNegativeQuantityReturns400() throws Exception {

        ManagedStore store = seedStore("Negative Store");

        String categoryPublicId = createCategoryAsAdmin("Negative Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Negative Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        mockMvc.perform(put("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("quantity")));
    }

    // ---------- received + damage ----------

    @Test
    @DisplayName("Received stock adds to the count and records a RECEIVED movement")
    void receivedStockAddsAndRecordsMovement() throws Exception {

        ManagedStore store = seedStore("Delivery Store");

        String categoryPublicId = createCategoryAsAdmin("Delivery Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Delivered Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/received")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(150));

        mockMvc.perform(get("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/movements")
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].type").value("RECEIVED"))
                .andExpect(jsonPath("$[1].quantityDelta").value(50))
                .andExpect(jsonPath("$[1].beforeQuantity").value(100))
                .andExpect(jsonPath("$[1].afterQuantity").value(150));
    }

    @Test
    @DisplayName("Receiving zero units 400s with a field error — additions are strictly positive")
    void receivedWithZeroQuantityReturns400() throws Exception {

        ManagedStore store = seedStore("Zero Delivery Store");

        String categoryPublicId = createCategoryAsAdmin("Zero Delivery Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Zero Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/received")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("quantity")));
    }

    @Test
    @DisplayName("Damage reduces the count and records a DAMAGE movement with the reason")
    void damageStockReducesAndRecordsReason() throws Exception {

        ManagedStore store = seedStore("Broken Store");

        String categoryPublicId = createCategoryAsAdmin("Broken Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Broken Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/damage")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 5, \"reason\": \"Cracked on delivery\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(95));

        mockMvc.perform(get("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/movements")
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].type").value("DAMAGE"))
                .andExpect(jsonPath("$[1].quantityDelta").value(-5))
                .andExpect(jsonPath("$[1].beforeQuantity").value(100))
                .andExpect(jsonPath("$[1].afterQuantity").value(95))
                .andExpect(jsonPath("$[1].reason").value("Cracked on delivery"));
    }

    @Test
    @DisplayName("Reporting more damage than the shelf holds 400s — state-dependent rule")
    void damageBelowCurrentStockReturns400() throws Exception {

        ManagedStore store = seedStore("Overclaim Store");

        String categoryPublicId = createCategoryAsAdmin("Overclaim Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Overclaimed Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/damage")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 200, \"reason\": \"Flood\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid stock adjustment"));
    }

    @Test
    @DisplayName("Damage on an unknown pair 404s before any stock math — guard order")
    void damageUnknownPairReturns404() throws Exception {

        ManagedStore store = seedStore("Guard Order Store");

        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory/products/" + UUID.randomUUID() + "/damage")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 5, \"reason\": \"Test\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    // ---------- threshold ----------

    @Test
    @DisplayName("Threshold update changes only the threshold — the ledger stays untouched")
    void thresholdUpdateChangesThresholdOnly() throws Exception {

        ManagedStore store = seedStore("Threshold Store");

        String categoryPublicId = createCategoryAsAdmin("Threshold Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Threshold Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        mockMvc.perform(put("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/threshold")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lowStockThreshold\": 25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockThreshold").value(25))
                .andExpect(jsonPath("$.quantity").value(100)); // quantity untouched

        mockMvc.perform(get("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/movements")
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))); // no movement — thresholds are not stock
    }

    @Test
    @DisplayName("A negative threshold 400s with a field error")
    void thresholdWithNegativeValueReturns400() throws Exception {

        ManagedStore store = seedStore("Bad Threshold Store");

        String categoryPublicId = createCategoryAsAdmin("Bad Threshold Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Bad Threshold Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        mockMvc.perform(put("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/threshold")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lowStockThreshold\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("lowStockThreshold")));
    }

    // ---------- list + low-stock tiers ----------

    @Test
    @DisplayName("The store inventory list contains its stocked pairs, newest first")
    void listInventoryForStoreReturnsSeededPairs() throws Exception {

        ManagedStore store = seedStore("Listed Store");

        String categoryPublicId = createCategoryAsAdmin("List Category");
        String olderId = listProductAs(store, categoryPublicId, "Listed Milk", uniqueSku());

        createInventoryAs(store.managerToken, store.storeId, olderId, 100, 10);

        String newerId = listProductAs(store, categoryPublicId, "Fresher Milk", uniqueSku());

        createInventoryAs(store.managerToken, store.storeId, newerId, 40, 5);

        mockMvc.perform(get("/api/v1/stores/" + store.storeId + "/inventory")
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productPublicId", hasItem(olderId)))
                .andExpect(jsonPath("$[0].productPublicId").value(newerId))
                .andExpect(jsonPath("$[1].productPublicId").value(olderId));
    }

    @Test
    @DisplayName("Low-stock returns LOW and OUT tiers with severity dedupe and readable names")
    void lowStockReturnsLowAndOutTiersWithDedupe() throws Exception {

        ManagedStore store = seedStore("Tier Store");

        String categoryPublicId = createCategoryAsAdmin("Tier Category");
        String healthyId = listProductAs(store, categoryPublicId, "Healthy Milk", uniqueSku());
        String lowId = listProductAs(store, categoryPublicId, "Low Milk", uniqueSku());
        String outId = listProductAs(store, categoryPublicId, "Out Milk", uniqueSku());
        String zeroThresholdId = listProductAs(store, categoryPublicId, "Edge Milk", uniqueSku());

        createInventoryAs(store.managerToken, store.storeId, healthyId, 100, 10); // healthy: nowhere
        createInventoryAs(store.managerToken, store.storeId, lowId, 8, 10); // LOW tier
        createInventoryAs(store.managerToken, store.storeId, outId, 0, 10); // OUT tier
        createInventoryAs(store.managerToken, store.storeId, zeroThresholdId, 0, 0); // OUT only — dedupe pin

        mockMvc.perform(get("/api/v1/stores/" + store.storeId + "/inventory/low-stock")
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.low[*].productPublicId", hasItem(lowId)))
                .andExpect(jsonPath("$.low[*].name", hasItem("Low Milk")))
                .andExpect(jsonPath("$.out[*].productPublicId", hasItem(outId)))
                .andExpect(jsonPath("$.out[*].productPublicId", hasItem(zeroThresholdId)))
                .andExpect(jsonPath("$.low[*].productPublicId", not(hasItem(zeroThresholdId))))
                .andExpect(jsonPath("$.low[*].productPublicId", not(hasItem(healthyId))))
                .andExpect(jsonPath("$.out[*].productPublicId", not(hasItem(healthyId))));
    }

    // ---------- movements ledger ----------

    @Test
    @DisplayName("The movements ledger reads chronologically with an honest before/after chain")
    void movementsListIsChronologicalLedger() throws Exception {

        ManagedStore store = seedStore("Ledger Store");

        String categoryPublicId = createCategoryAsAdmin("Ledger Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Ledger Milk", uniqueSku());
        createInventoryAs(store.managerToken, store.storeId, productPublicId, 100, 10);

        // received 50 -> 150, damage 5 -> 145, stocktake -> 60
        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/received")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 50}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/damage")
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 5, \"reason\": \"Cracked on delivery\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 60}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/stores/" + store.storeId + "/inventory/products/" + productPublicId + "/movements")
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].type").value("OPENING"))
                .andExpect(jsonPath("$[0].beforeQuantity").value(0))
                .andExpect(jsonPath("$[0].afterQuantity").value(100))
                .andExpect(jsonPath("$[1].type").value("RECEIVED"))
                .andExpect(jsonPath("$[1].quantityDelta").value(50))
                .andExpect(jsonPath("$[2].type").value("DAMAGE"))
                .andExpect(jsonPath("$[2].quantityDelta").value(-5))
                .andExpect(jsonPath("$[2].afterQuantity").value(145))
                .andExpect(jsonPath("$[3].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$[3].beforeQuantity").value(145))
                .andExpect(jsonPath("$[3].afterQuantity").value(60))
                .andExpect(jsonPath("$[3].createdAt", notNullValue()));
    }

    // ---------- authorization ----------

    @Test
    @DisplayName("A plain regular user cannot create stock: 403")
    void regularUserCannotCreateInventory() throws Exception {

        ManagedStore store = seedStore("Guarded Inventory Store");

        String categoryPublicId = createCategoryAsAdmin("Guarded Category");
        String productPublicId = listProductAs(store, categoryPublicId, "Guarded Milk", uniqueSku());

        mockMvc.perform(post("/api/v1/stores/" + store.storeId + "/inventory")
                        .header("Authorization", "Bearer " + regularUserToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inventoryBody(productPublicId, 100, 10)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A different store's admin gets 403 on a live store — never 404 for identity")
    void foreignStoreAdminCannotReadInventory() throws Exception {

        ManagedStore target = seedStore("Target Store");

        String categoryPublicId = createCategoryAsAdmin("Target Category");
        String productPublicId = listProductAs(target, categoryPublicId, "Target Milk", uniqueSku());

        createInventoryAs(target.managerToken, target.storeId, productPublicId, 100, 10);

        ManagedStore foreign = seedStore("Foreign Inventory Store"); // its admin owns a DIFFERENT store

        mockMvc.perform(get("/api/v1/stores/" + target.storeId + "/inventory")
                        .header("Authorization", "Bearer " + foreign.managerToken))
                .andExpect(status().isForbidden());
    }
}
