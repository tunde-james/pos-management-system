package com.devtunde.posbackend.product.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        properties = {"app.security.rate-limit.limit=1000" // high — 34 tests' login volume must not trip the limiter
        })
@AutoConfigureMockMvc
class ProductApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper(); // Boot 4: no Jackson bean — own instance

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final String PASSWORD = "Password123!";

    // ---------- personas ----------

    private String uniqueEmail() {
        return "ptest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";
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

    // ---------- store + catalog arrangement ----------

    private String createStoreAsAdmin(String adminToken, String assignedPublicId, String brand, String contactEmail)
            throws Exception {
        String body = """
                   {
                       "brand": "%s",
                       "description": "Catalog test store",
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

    /** A store with its OWN admin — the pairing
     * product tests need everywhere. */
    private static final class ManagedStore {
        final String managerToken;
        final String storeId;

        ManagedStore(String managerToken, String storeId) {
            this.managerToken = managerToken;
            this.storeId = storeId;
        }
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
                       "description": "Test item for the catalog",
                       "brand": "TestBrand",
                       "image": "https://cdn.devtunde.com/item.png",
                       "categoryPublicId": "%s",
                       "mrp": %s,
                       "sellingPrice": %s
                   }
                   """.formatted(name, sku, categoryPublicId, mrp, sellingPrice);
    }

    /** Arrangement helper: a store admin lists one
     * product, returns the product publicId. */
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

    // ---------- categories ----------

    @Test
    @DisplayName("POST /api/v1/categories without a token returns 401")
    void createCategoryWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Beverages\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/categories is forbidden for a regular user")
    void createCategoryForbiddenForRegularUser() throws Exception {
        String token = regularUserToken();

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"NopeCategory\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An admin creates a category: 201, publicId, server-set timestamps")
    void adminCreatesCategoryReturns201() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Fresh Produce\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId", notNullValue()))
                .andExpect(jsonPath("$.name").value("Fresh Produce"))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    @DisplayName("Creating a category with a blank name returns 400 with a field error")
    void createCategoryWithBlankNameReturns400() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")));
    }

    @Test
    @DisplayName("GET /api/v1/categories is forbidden for a regular user (not a store admin, not chain admin)")
    void listCategoriesForbiddenForRegularUser() throws Exception {
        String token = regularUserToken();

        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/categories works for ANY store admin — the isStoreAdmin gate")
    void listCategoriesForAnyStoreAdminReturns200() throws Exception {
        createCategoryAsAdmin("Visible Vocabulary");
        // so the list has something to find

        ManagedStore store = seedStore("Category Reader Store");

        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Visible Vocabulary")));
    }

    @Test
    @DisplayName("An admin renames a category: 200, new name")
    void adminRenamesCategoryReturns200() throws Exception {
        String adminToken = adminToken();
        String publicId = createCategoryAsAdmin("Old Category Name");

        mockMvc.perform(patch("/api/v1/categories/" + publicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Renamed Category\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Category"));
    }

    @Test
    @DisplayName("Creating a category with an existing name returns 409")
    void createCategoryDuplicateNameReturns409() throws Exception {
        String adminToken = adminToken();
        createCategoryAsAdmin("Clash Vocabulary");

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Clash Vocabulary\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Category already exists"));
    }

    @Test
    @DisplayName("A non-UUID storeId in the path returns 400, not 500")
    void createProductWithGarbageStoreIdReturns400() throws Exception {

        mockMvc.perform(post("/api/v1/products/store/not-a-uuid")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(
                                "Bad Path Cola",
                                uniqueSku(),
                                "11111111-1111-1111-1111-111111111111",
                                "15000.00",
                                "12000.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Renaming a category to an existing name returns 409")
    void renameCategoryToExistingNameReturns409() throws Exception {
        String adminToken = adminToken();
        String target = createCategoryAsAdmin("First Vocabulary");
        createCategoryAsAdmin("Second Vocabulary");

        mockMvc.perform(patch("/api/v1/categories/" + target)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Second Vocabulary\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("An empty master patch returns 400 — a patch must change something")
    void masterUpdateEmptyBodyReturns400() throws Exception {
        String adminToken = adminToken();
        String categoryPublicId = createCategoryAsAdmin("Empty Patch Category");
        ManagedStore store = seedStore("Empty Patch Master Store");
        String productPublicId = listProductAs(store, categoryPublicId, "Unpatched Item", uniqueSku());

        mockMvc.perform(patch("/api/v1/products/" + productPublicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("An empty price patch returns 400 — a patch must change something")
    void listingPriceUpdateEmptyBodyReturns400() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Empty Price Category");
        ManagedStore store = seedStore("Empty Patch Price Store");
        String productPublicId = listProductAs(store, categoryPublicId, "Unpriced Item", uniqueSku());

        mockMvc.perform(patch("/api/v1/products/store/" + store.storeId + "/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Updating an unknown category returns 404")
    void updateCategoryUnknownReturns404() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(patch("/api/v1/categories/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ghost\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deleting a category with no products returns 204")
    void deleteEmptyCategoryReturns204() throws Exception {
        String adminToken = adminToken();
        String publicId = createCategoryAsAdmin("Empty Category");

        mockMvc.perform(delete("/api/v1/categories/" + publicId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Deleting a category still referenced by a product returns 409, no cascade")
    void deleteCategoryInUseReturns409() throws Exception {
        String adminToken = adminToken();
        String categoryPublicId = createCategoryAsAdmin("In Use Category");
        ManagedStore store = seedStore("Category In Use Store");

        listProductAs(store, categoryPublicId, "Guarded Cola", uniqueSku());

        mockMvc.perform(delete("/api/v1/categories/" + categoryPublicId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Category in use"));
    }

    // ---------- create listing: upsert master by sku + insert listing ----------

    @Test
    @DisplayName("POST /api/v1/products/store/{storeId} without a token returns 401")
    void createProductWithoutTokenReturns401() throws Exception {

        mockMvc.perform(post("/api/v1/products/store/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(
                                "Cola", uniqueSku(), UUID.randomUUID().toString(), "15000.00", "12000.00")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A store admin lists a product: 201, prices, nested category ref, discount defaults to 0.00")
    void storeAdminListsProductReturns201() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Beverages");
        ManagedStore store = seedStore("Lekki Branch");

        mockMvc.perform(post("/api/v1/products/store/" + store.storeId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(
                                "Original Cola 50cl", uniqueSku(), categoryPublicId, "15000.00", "12000.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId", notNullValue()))
                .andExpect(jsonPath("$.name").value("Original Cola 50cl"))
                .andExpect(jsonPath("$.description").value("Test item for the catalog"))
                .andExpect(jsonPath("$.brand").value("TestBrand"))
                .andExpect(jsonPath("$.image").value("https://cdn.devtunde.com/item.png"))
                .andExpect(jsonPath("$.mrp").value(15000.00))
                .andExpect(jsonPath("$.sellingPrice").value(12000.00))
                .andExpect(jsonPath("$.discountPercentage").value(0.00))
                .andExpect(jsonPath("$.category.publicId").value(categoryPublicId))
                .andExpect(jsonPath("$.category.name").value("Beverages"))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    @DisplayName("The chain admin may list a product in ANY store (ROLE_ADMIN bypass)")
    void chainAdminListsInAnyStoreReturns201() throws Exception {
        String adminToken = adminToken();
        String categoryPublicId = createCategoryAsAdmin("Groceries");
        ManagedStore store = seedStore("Chain Overseen Store");
        String sku = uniqueSku();

        mockMvc.perform(post("/api/v1/products/store/" + store.storeId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("Chain Listed Item", sku, categoryPublicId, "15000.00", "12000.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(sku));
    }

    @Test
    @DisplayName("A store admin of ANOTHER store may not list here (data-level predicate)")
    void otherStoreAdminForbiddenReturns403() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Snacks");
        ManagedStore ownedStore = seedStore("Home Store");
        ManagedStore otherStore = seedStore("Foreign Store");

        mockMvc.perform(post("/api/v1/products/store/" + ownedStore.storeId)
                        .header("Authorization", "Bearer " + otherStore.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("Foreign Cola", uniqueSku(), categoryPublicId, "15000.00", "12000.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Listing into an unknown store returns 404 — store's own exception propagates")
    void createProductWithUnknownStoreReturns404() throws Exception {
        String adminToken = adminToken();
        String categoryPublicId = createCategoryAsAdmin("Dairy");

        mockMvc.perform(post("/api/v1/products/store/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                productBody("Ghost Store Item", uniqueSku(), categoryPublicId, "15000.00", "12000.00")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An unknown categoryPublicId in the payload returns 400 — payload ids are 400, not 404")
    void createProductWithUnknownCategoryReturns400() throws Exception {
        ManagedStore store = seedStore("Bad Category Store");

        mockMvc.perform(post("/api/v1/products/store/" + store.storeId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(
                                "Categorized Item",
                                uniqueSku(),
                                UUID.randomUUID().toString(),
                                "15000.00",
                                "12000.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Category not found"));
    }

    @Test
    @DisplayName("Listing the same product twice in one store returns 409")
    void createProductDuplicateListingReturns409() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Household");
        ManagedStore store = seedStore("Duplicate Store");
        String sku = uniqueSku();

        listProductAs(store, categoryPublicId, "Once Only Cola", sku);

        mockMvc.perform(post("/api/v1/products/store/" + store.storeId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("Once Only Cola", sku, categoryPublicId, "15000.00", "12000.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Product already listed"));
    }

    @Test
    @DisplayName("A second store listing the same SKU reuses the MASTER: same publicId, master's name wins")
    void secondStoreReusesMasterBySkuReturns201() throws Exception {

        String categoryPublicId = createCategoryAsAdmin("Master Electronics");
        ManagedStore storeA = seedStore("Master Home Store");
        ManagedStore storeB = seedStore("Master Reuse Store");
        String sku = uniqueSku();

        String masterPublicId = listProductAs(storeA, categoryPublicId, "Master Television", sku);

        // Store B sends a DIFFERENT name for the same sku — the master's identity wins

        mockMvc.perform(post("/api/v1/products/store/" + storeB.storeId)
                        .header("Authorization", "Bearer " + storeB.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("Sneaky Renamed TV", sku, categoryPublicId, "15000.00", "12000.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value(masterPublicId)) // ONE master, not two
                .andExpect(jsonPath("$.name").value("Master Television")); // master's fields, not the payload's
    }

    @Test
    @DisplayName("A blank product name returns 400 with a field error")
    void createProductWithBlankNameReturns400() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Baking");
        ManagedStore store = seedStore("Blank Name Store");

        mockMvc.perform(post("/api/v1/products/store/" + store.storeId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("   ", uniqueSku(), categoryPublicId, "15000.00", "12000.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")));
    }

    @Test
    @DisplayName("A negative price returns 400 with a field error (money is guarded)")
    void createProductWithNegativePriceReturns400() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Frozen");
        ManagedStore store = seedStore("Negative Price Store");

        mockMvc.perform(post("/api/v1/products/store/" + store.storeId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("Cheap Cola", uniqueSku(), categoryPublicId, "-15000.00", "12000.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("mrp")));
    }

    // ---------- catalog reads: same predicate as writes ----------

    @Test
    @DisplayName("GET /api/v1/products/store/{id} returns the store's catalog for its admin")
    void listCatalogForStoreReturnsProducts() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Drinks");
        ManagedStore store = seedStore("Catalog Store");

        listProductAs(store, categoryPublicId, "Listable Cola", uniqueSku());
        listProductAs(store, categoryPublicId, "Listable Fanta", uniqueSku());

        mockMvc.perform(get("/api/v1/products/store/" + store.storeId)
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[*].name", hasItem("Listable Cola")))
                .andExpect(jsonPath("$[*].name", hasItem("Listable Fanta")));
    }

    @Test
    @DisplayName("Search by keyword finds by sku — and the result carries prices + category name (join-fetch proof)")
    void searchByKeywordReturnsMatchingProductsWithFullShape() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Electronics");
        ManagedStore store = seedStore("Search Store");
        String uniqueSku = uniqueSku();

        listProductAs(store, categoryPublicId, "Searchable Television", uniqueSku);
        listProductAs(store, categoryPublicId, "Unrelated Kettle", uniqueSku());

        mockMvc.perform(get("/api/v1/products/store/" + store.storeId + "/search")
                        .param("keyword", uniqueSku)
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Searchable Television"))
                .andExpect(jsonPath("$[0].sellingPrice").value(12000.00))
                .andExpect(jsonPath("$[0].category.name").value("Electronics"));
    }

    @Test
    @DisplayName("Reading another store's catalog is forbidden")
    void catalogReadForbiddenForOtherStoreAdmin() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Toys");
        ManagedStore ownedStore = seedStore("Private Catalog Store");
        ManagedStore otherStore = seedStore("Prying Store");

        mockMvc.perform(get("/api/v1/products/store/" + ownedStore.storeId)
                        .header("Authorization", "Bearer " + otherStore.managerToken))
                .andExpect(status().isForbidden());
    }

    // ---------- listing price update: prices only, store-scoped ----------

    @Test
    @DisplayName("A store admin updates listing prices: 200, new prices, name untouched")
    void updateListingPricesAsStoreAdminReturns200() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Hardware");
        ManagedStore store = seedStore("Price Change Store");
        String productPublicId = listProductAs(store, categoryPublicId, "Price Change Cola", uniqueSku());

        String body = """
                   {
                       "mrp": 20000.00,
                       "sellingPrice": 18000.00
                   }
                   """.formatted();

        mockMvc.perform(patch("/api/v1/products/store/" + store.storeId + "/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mrp").value(20000.00))
                .andExpect(jsonPath("$.sellingPrice").value(18000.00))
                .andExpect(jsonPath("$.name").value("Price Change Cola"));
    }

    @Test
    @DisplayName("Updating only one price leaves the other untouched (nulls ignored)")
    void updateListingWithOnePriceIgnoresTheOther() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Cleaning");
        ManagedStore store = seedStore("Partial Update Store");
        String productPublicId = listProductAs(store, categoryPublicId, "Partial Cola", uniqueSku());

        mockMvc.perform(patch("/api/v1/products/store/" + store.storeId + "/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellingPrice\": 9000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellingPrice").value(9000.00))
                .andExpect(jsonPath("$.mrp").value(15000.00)); // untouched
    }

    @Test
    @DisplayName("Another store's admin may not update my listing prices")
    void updateListingAsOtherStoreAdminReturns403() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Garden");
        ManagedStore ownedStore = seedStore("Owned Pricing Store");
        ManagedStore otherStore = seedStore("Rival Pricing Store");
        String productPublicId = listProductAs(ownedStore, categoryPublicId, "Guarded Cola", uniqueSku());

        mockMvc.perform(patch("/api/v1/products/store/" + ownedStore.storeId + "/" + productPublicId)
                        .header("Authorization", "Bearer " + otherStore.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellingPrice\": 1000.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Updating prices for a product this store does not list returns 404")
    void updateListingUnknownProductReturns404() throws Exception {
        ManagedStore store = seedStore("Ghost Listing Store");

        mockMvc.perform(patch("/api/v1/products/store/" + store.storeId + "/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellingPrice\": 1000.00}"))
                .andExpect(status().isNotFound());
    }

    // ---------- delist ----------

    @Test
    @DisplayName("Delist: 204, and the product leaves the catalog")
    void delistRemovesProductFromCatalog() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Stationery");
        ManagedStore store = seedStore("Delist Store");
        String sku = uniqueSku();
        String productPublicId = listProductAs(store, categoryPublicId, "Doomed Cola", sku);

        mockMvc.perform(delete("/api/v1/products/store/" + store.storeId + "/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isNoContent());

        // gone from the catalog: search by its unique sku finds nothing

        mockMvc.perform(get("/api/v1/products/store/" + store.storeId + "/search")
                        .param("keyword", sku)
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Delisting twice returns an honest 404 — the row is gone")
    void delistTwiceReturns404() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Pet Care");
        ManagedStore store = seedStore("Double Delist Store");
        String productPublicId = listProductAs(store, categoryPublicId, "Once Delisted", uniqueSku());

        mockMvc.perform(delete("/api/v1/products/store/" + store.storeId + "/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/products/store/" + store.storeId + "/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isNotFound());
    }

    // ---------- master update: chain surface, ROLE_ADMIN ----------

    @Test
    @DisplayName(
            "The chain admin updates the MASTER: 200, new name, and NO prices in the response (ProductMasterResponse)")
    void masterUpdateByAdminReturns200WithoutPrices() throws Exception {
        String adminToken = adminToken();
        String categoryPublicId = createCategoryAsAdmin("Confectionery");
        ManagedStore store = seedStore("Master Update Store");
        String productPublicId = listProductAs(store, categoryPublicId, "Old Master Name", uniqueSku());

        mockMvc.perform(patch("/api/v1/products/" + productPublicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"New Master Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(productPublicId))
                .andExpect(jsonPath("$.name").value("New Master Name"))
                .andExpect(jsonPath("$.sellingPrice").doesNotExist())
                // chain view: no store, no price

                .andExpect(jsonPath("$.mrp").doesNotExist());
    }

    @Test
    @DisplayName("A store admin may NOT touch master fields — chain data is the core office's")
    void masterUpdateForbiddenForStoreAdmin() throws Exception {
        String categoryPublicId = createCategoryAsAdmin("Bakery");
        ManagedStore store = seedStore("Master Guard Store");
        String productPublicId = listProductAs(store, categoryPublicId, "Guarded Master", uniqueSku());

        mockMvc.perform(patch("/api/v1/products/" + productPublicId)
                        .header("Authorization", "Bearer " + store.managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Hijacked Name\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sku is structurally immutable: sending it in a master update is silently dropped")
    void masterUpdateWithSkuFieldIsIgnored() throws Exception {
        String adminToken = adminToken();
        String categoryPublicId = createCategoryAsAdmin("Spices");
        ManagedStore store = seedStore("Sku Guard Store");
        String sku = uniqueSku();
        String productPublicId = listProductAs(store, categoryPublicId, "Sku Pinned Item", sku);

        mockMvc.perform(patch("/api/v1/products/" + productPublicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\": \"HIJACK-1\", \"brand\": \"Hijack Proof Brand\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(sku)) // identity never changes
                .andExpect(jsonPath("$.brand").value("Hijack Proof Brand")); // the real field applied
    }

    @Test
    @DisplayName("Updating an unknown product master returns 404")
    void masterUpdateUnknownReturns404() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(patch("/api/v1/products/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ghost Item\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Search without a keyword returns 400, not 500")
    void searchWithoutKeywordReturns400() throws Exception {

        ManagedStore store = seedStore("Keywordless Search Store");

        mockMvc.perform(get("/api/v1/products/store/" + store.storeId + "/search")
                        .header("Authorization", "Bearer " + store.managerToken))
                .andExpect(status().isBadRequest());
    }
}
