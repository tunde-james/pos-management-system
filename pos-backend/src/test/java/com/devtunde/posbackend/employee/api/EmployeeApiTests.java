package com.devtunde.posbackend.employee.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"app.security.rate-limit.limit=1000"})
@AutoConfigureMockMvc
class EmployeeApiTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_PASSWORD = "Password123!";
    private static final String WORK_DOMAIN = "postest.com";
    private static final String PERSONAL_DOMAIN = "gmail.com";
    private static final String GOOD_PHONE = "+2348091234567";

    // ── fixtures ────────────────────────────────────────────────────────────

    private String uniqueEmail(String prefix) {

        return prefix + "-" + System.nanoTime() + "@" + WORK_DOMAIN;
    }

    private void signupUser(String email, String fullName) throws Exception {

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content("""
                        {
                              "fullName": "%s",
                              "email": "%s",
                              "password": "%s",
                              "phone": "%s"
                        }
                        """.formatted(fullName, email, DEFAULT_PASSWORD, GOOD_PHONE)))
                .andReturn();
    }

    private String loginForToken(String email) throws Exception {

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                        {
                              "email": "%s",
                              "password": "%s"
                        }
                        """.formatted(email, DEFAULT_PASSWORD)))
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private String publicIdOf(String email) throws Exception {

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                        {
                              "email": "%s",
                              "password": "%s"
                        }
                        """.formatted(email, DEFAULT_PASSWORD)))
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("user")
                .get("publicId")
                .asText();
    }

    private String adminToken() throws Exception {

        String email = uniqueEmail("chain-admin");

        signupUser(email, "Chain Admin");
        jdbcTemplate.update("update users set role = 'ROLE_ADMIN' where email = ?", email);

        return loginForToken(email); // token minted AFTER the promotion carries ROLE_ADMIN
    }

    private String createStoreAsAdmin(String adminToken, String seatAdminPublicId) throws Exception {

        MvcResult result = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                              {
                                    "brand":"Emp Brand %d",
                                    "storeType":"KIOSK",
                                    "storeAdminPublicId":"%s",
                                    "contact":{"address":"Emp Way",
                                    "phone":"08012345678",
                                    "email":"%s"
                              }}
                              """.formatted(
                                        System.nanoTime(),
                                        seatAdminPublicId,
                                        "brand-" + System.nanoTime() + "@" + WORK_DOMAIN)))
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("publicId")
                .asText();
    }

    /** A store plus the login of the user who
     * runs it: returns {storeId, seatToken}. */
    record StoreFixture(String storeId, String seatToken) {}

    private StoreFixture managedStore() throws Exception {

        String seatEmail = uniqueEmail("seat-admin");

        signupUser(seatEmail, "Seat Admin");

        String seatToken = loginForToken(seatEmail);
        String storeId = createStoreAsAdmin(adminToken(), publicIdOf(seatEmail));

        return new StoreFixture(storeId, seatToken);
    }

    private String hireBody(String email, String fullName, String phone, String jobTitle) {

        return """
            {
                  "fullName":"%s",
                  "email":"%s",
                  "password":"%s",
                  "phone":"%s",
                  "jobTitle":"%s"
            }
            """.formatted(fullName, email, DEFAULT_PASSWORD, phone, jobTitle);
    }

    private String hireBody(String email, String jobTitle) {

        return hireBody(email, "Hired Person", GOOD_PHONE, jobTitle);
    }

    private MvcResult hireAs(String token, String storeId, String body) throws Exception {

        return mockMvc.perform(post("/api/v1/stores/" + storeId + "/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andReturn();
    }

    private String hiredEmployeeId(String token, String storeId, String email, String jobTitle) throws Exception {

        MvcResult result = hireAs(token, storeId, hireBody(email, jobTitle));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        return employeeIdOf(result);
    }

    private String employeeIdOf(MvcResult hireResult) throws Exception {

        return objectMapper
                .readTree(hireResult.getResponse().getContentAsString())
                .get("publicId")
                .asText();
    }

    // ── hire: the three doors ───────────────────────────────────────────────

    @Test
    @DisplayName(
            "Door 1 — unknown work email: chain admin hires, 201, provisioned ROLE_USER user, no secrets on the wire")
    void chainAdminHiresNewEmployeeReturns201WithProvisionedUser() throws Exception {

        StoreFixture store = managedStore();

        String email = uniqueEmail("chidi");

        MvcResult result = hireAs(adminToken(), store.storeId(), hireBody(email, "CASHIER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.get("publicId")).isNotNull();
        assertThat(body.get("storeId").asText()).isEqualTo(store.storeId());
        assertThat(body.get("jobTitle").asText()).isEqualTo("CASHIER");
        assertThat(body.get("hiredAt")).isNotNull();
        assertThat(body.get("user").get("email").asText()).isEqualTo(email);
        assertThat(body.get("user").get("role").asText()).isEqualTo("ROLE_USER");
        assertThat(body.has("password")).isFalse();
        assertThat(body.has("accessToken")).isFalse();
        assertThat(body.has("refreshToken")).isFalse();
        assertThat(body.get("user").has("password")).isFalse();
    }

    @Test
    @DisplayName("Door 1 — the store's own seat admin can hire for their store: 201")
    void seatAdminHiresForTheirOwnStoreReturns201() throws Exception {

        StoreFixture store = managedStore();

        MvcResult result =
                hireAs(store.seatToken(), store.storeId(), hireBody(uniqueEmail("hired"), "SALES_ATTENDANT"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("Door 2 — email already has an active job: 409, even at a different store")
    void hireWithRegisteredEmailOfActivelyEmployedUserReturns409() throws Exception {

        StoreFixture storeA = managedStore();
        StoreFixture storeB = managedStore();

        String email = uniqueEmail("busy");
        hiredEmployeeId(storeA.seatToken(), storeA.storeId(), email, "CASHIER");

        MvcResult result = hireAs(storeB.seatToken(), storeB.storeId(), hireBody(email, "MANAGER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Door 3 — former staff rehired via their old work email: 201, SAME account, NEW chapter")
    void rehireViaOldWorkEmailReemploysTheSameAccountWithANewEra() throws Exception {

        StoreFixture store = managedStore();
        String email = uniqueEmail("returning");

        String firstEraEmployeeId = hiredEmployeeId(store.seatToken(), store.storeId(), email, "CASHIER");
        String firstEraUserPublicId = publicIdOf(email);

        MvcResult fired = mockMvc.perform(
                        delete("/api/v1/stores/" + store.storeId() + "/employees/" + firstEraEmployeeId)
                                .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        assertThat(fired.getResponse().getStatus()).isEqualTo(204);

        MvcResult rehired = hireAs(store.seatToken(), store.storeId(), hireBody(email, "MANAGER"));

        assertThat(rehired.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(rehired.getResponse().getContentAsString());
        assertThat(body.get("publicId").asText()).isNotEqualTo(firstEraEmployeeId); // new chapter
        assertThat(body.get("user").get("publicId").asText()).isEqualTo(firstEraUserPublicId); // same mailbox
        assertThat(body.get("jobTitle").asText()).isEqualTo("MANAGER"); // era carries its own title
    }

    @Test
    @DisplayName("Door 3 variant — rehired with a brand-new work email: new account, and the old chapter row survives")
    void rehireWithBrandNewEmailKeepsTheOldEraRow() throws Exception {

        StoreFixture store = managedStore();

        String oldEmail = uniqueEmail("old-chidi");
        String oldUserPublicId = publicIdOf(hiredEmail(store, oldEmail)); // see helper below
        String firstEraEmployeeId = hiredEmployeeId(store.seatToken(), store.storeId(), oldEmail, "CASHIER");

        mockMvc.perform(delete("/api/v1/stores/" + store.storeId() + "/employees/" + firstEraEmployeeId)
                        .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        String freshEmail = uniqueEmail("new-chidi");
        MvcResult rehired = hireAs(store.seatToken(), store.storeId(), hireBody(freshEmail, "SALES_ATTENDANT"));

        assertThat(rehired.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(rehired.getResponse().getContentAsString());

        assertThat(body.get("user").get("publicId").asText()).isNotEqualTo(oldUserPublicId); // new mailbox

        // the old chapter was never wiped — the era row still sits in the table
        Integer closedEras = jdbcTemplate.queryForObject(
                "select count(*) from store_employees where user_public_id = ? and terminated_at is not null",
                Integer.class,
                oldUserPublicId);
        assertThat(closedEras).isEqualTo(1);
    }

    private String hiredEmail(StoreFixture store, String email) throws Exception {

        signupUser(email, "Hired Person");
        return email;
    }

    // ── hire: the gate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("A regular signed-in user cannot hire: 403")
    void regularUserCannotHireReturns403() throws Exception {

        StoreFixture store = managedStore();
        String nobody = uniqueEmail("nobody");

        signupUser(nobody, "Nobody");

        MvcResult result = hireAs(loginForToken(nobody), store.storeId(), hireBody(uniqueEmail("victim"), "CASHIER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("A seat admin of ANOTHER store cannot hire here: 403")
    void otherSeatAdminCannotHireIntoSomeoneElsesStoreReturns403() throws Exception {

        StoreFixture storeA = managedStore();
        StoreFixture storeB = managedStore();

        // B's admin is a real seat admin — just elsewhere

        MvcResult result = hireAs(storeB.seatToken(), storeA.storeId(), hireBody(uniqueEmail("hired"), "CASHIER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Unauthenticated hire: 401")
    void unauthenticatedHireReturns401() throws Exception {

        StoreFixture store = managedStore();

        MvcResult result = hireAs("no-token", store.storeId(), hireBody(uniqueEmail("hired"), "CASHIER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Hiring into an unknown store: 404")
    void hireIntoUnknownStoreReturns404() throws Exception {

        MvcResult result =
                hireAs(adminToken(), "00000000-0000-0000-0000-000000000000", hireBody(uniqueEmail("hired"), "CASHIER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Hiring into a CLOSED store: 404 — dead stores stay hidden")
    void hireIntoClosedStoreReturns404() throws Exception {

        String adminToken = adminToken();
        String seatEmail = uniqueEmail("seat");
        signupUser(seatEmail, "Seat Admin");

        String storeId = createStoreAsAdmin(adminToken, publicIdOf(seatEmail));

        MvcResult closed = mockMvc.perform(
                        delete("/api/v1/stores/" + storeId).header("Authorization", "Bearer " + adminToken))
                .andReturn();

        assertThat(closed.getResponse().getStatus()).isEqualTo(204);

        MvcResult result = hireAs(loginForToken(seatEmail), storeId, hireBody(uniqueEmail("hired"), "CASHIER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    // ── hire: validation ────────────────────────────────────────────────────

    @Test
    @DisplayName("A personal-domain email (gmail) is refused at the boundary: 400")
    void hireWithPersonalEmailDomainReturns400() throws Exception {

        StoreFixture store = managedStore();

        MvcResult result =
                hireAs(store.seatToken(), store.storeId(), hireBody("shopper@" + PERSONAL_DOMAIN, "CASHIER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("An invalid job title (CEO) is refused: 400")
    void hireWithInvalidJobTitleReturns400() throws Exception {

        StoreFixture store = managedStore();

        MvcResult result = hireAs(store.seatToken(), store.storeId(), hireBody(uniqueEmail("ceo"), "CEO"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("A garbage phone number is refused: 400 (provisioning reuses signup's E.164 rules)")
    void hireWithBadPhoneReturns400() throws Exception {

        StoreFixture store = managedStore();

        MvcResult result = hireAs(
                store.seatToken(),
                store.storeId(),
                hireBody(uniqueEmail("phone"), "Hired Person", "not-a-phone", "CASHIER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Blank required fields are refused: 400")
    void hireWithBlankFieldsReturns400() throws Exception {

        StoreFixture store = managedStore();

        MvcResult result = hireAs(
                store.seatToken(), store.storeId(), hireBody(uniqueEmail("blank"), "   ", GOOD_PHONE, "CASHIER"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    // ── list ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("The seat admin lists their roster: the hired show, the terminated don't")
    void seatAdminListsTheirRoster() throws Exception {

        StoreFixture store = managedStore();

        String kept = uniqueEmail("kept");
        String gone = uniqueEmail("gone");
        hiredEmployeeId(store.seatToken(), store.storeId(), kept, "CASHIER");
        String firedId = hiredEmployeeId(store.seatToken(), store.storeId(), gone, "CASHIER");

        mockMvc.perform(delete("/api/v1/stores/" + store.storeId() + "/employees/" + firedId)
                        .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        MvcResult result = mockMvc.perform(get("/api/v1/stores/" + store.storeId() + "/employees")
                        .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode roster = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(roster.size()).isEqualTo(1);

        assertThat(roster.get(0).get("user").get("email").asText()).isEqualTo(kept);
    }

    @Test
    @DisplayName("Listing can filter by job title")
    void listFiltersByJobTitle() throws Exception {

        StoreFixture store = managedStore();

        hiredEmployeeId(store.seatToken(), store.storeId(), uniqueEmail("till"), "CASHIER");
        hiredEmployeeId(store.seatToken(), store.storeId(), uniqueEmail("floor"), "SALES_ATTENDANT");

        MvcResult result = mockMvc.perform(get("/api/v1/stores/" + store.storeId() + "/employees")
                        .param("jobTitle", "CASHIER")
                        .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode roster = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(roster.size()).isEqualTo(1);

        assertThat(roster.get(0).get("jobTitle").asText()).isEqualTo("CASHIER");
    }

    @Test
    @DisplayName("An empty roster is an empty array, not an error")
    void listEmptyRosterReturnsEmptyArray() throws Exception {

        StoreFixture store = managedStore();

        MvcResult result = mockMvc.perform(get("/api/v1/stores/" + store.storeId() + "/employees")
                        .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .size())
                .isZero();
    }

    @Test
    @DisplayName("Listing an unknown store: 404")
    void listUnknownStoreReturns404() throws Exception {

        MvcResult result = mockMvc.perform(get("/api/v1/stores/00000000-0000-0000-0000-000000000000/employees")
                        .header("Authorization", "Bearer " + adminToken()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("A regular user cannot list a roster: 403")
    void regularUserCannotListReturns403() throws Exception {

        StoreFixture store = managedStore();

        String nobody = uniqueEmail("nobody");
        signupUser(nobody, "Nobody");

        MvcResult result = mockMvc.perform(get("/api/v1/stores/" + store.storeId() + "/employees")
                        .header("Authorization", "Bearer " + loginForToken(nobody)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    // ── get one ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("The seat admin reads one employment by its id: 200, same shape as the hire response")
    void getEmployeeByIdReturnsTheEmployment() throws Exception {

        StoreFixture store = managedStore();

        String email = uniqueEmail("one");
        String employeeId = hiredEmployeeId(store.seatToken(), store.storeId(), email, "MANAGER");

        MvcResult result = mockMvc.perform(get("/api/v1/stores/" + store.storeId() + "/employees/" + employeeId)
                        .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.get("publicId").asText()).isEqualTo(employeeId);

        assertThat(body.get("user").get("email").asText()).isEqualTo(email);
    }

    @Test
    @DisplayName("Reading an unknown employment id: 404")
    void getUnknownEmployeeReturns404() throws Exception {

        StoreFixture store = managedStore();

        MvcResult result = mockMvc.perform(
                        get("/api/v1/stores/" + store.storeId() + "/employees/00000000-0000-0000-0000-000000000000")
                                .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("A seat admin cannot read a store they do not run: 403")
    void getEmployeeOfStoreYouDoNotRunReturns403() throws Exception {

        StoreFixture storeA = managedStore();
        StoreFixture storeB = managedStore();

        String employeeId = hiredEmployeeId(storeA.seatToken(), storeA.storeId(), uniqueEmail("theirs"), "CASHIER");

        MvcResult result = mockMvc.perform(get("/api/v1/stores/" + storeA.storeId() + "/employees/" + employeeId)
                        .header("Authorization", "Bearer " + storeB.seatToken()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("A terminated employment reads as 404 — dead is dead")
    void terminatedEmployeeGetReturns404() throws Exception {

        StoreFixture store = managedStore();

        String employeeId = hiredEmployeeId(store.seatToken(), store.storeId(), uniqueEmail("fired"), "CASHIER");

        mockMvc.perform(delete("/api/v1/stores/" + store.storeId() + "/employees/" + employeeId)
                        .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        MvcResult result = mockMvc.perform(get("/api/v1/stores/" + store.storeId() + "/employees/" + employeeId)
                        .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    // ── terminate ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A regular user cannot terminate: 403")
    void regularUserCannotTerminateReturns403() throws Exception {

        StoreFixture store = managedStore();

        String employeeId = hiredEmployeeId(store.seatToken(), store.storeId(), uniqueEmail("protected"), "CASHIER");
        String nobody = uniqueEmail("nobody");
        signupUser(nobody, "Nobody");

        MvcResult result = mockMvc.perform(delete("/api/v1/stores/" + store.storeId() + "/employees/" + employeeId)
                        .header("Authorization", "Bearer " + loginForToken(nobody)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Terminating an unknown employment: 404")
    void terminateUnknownEmployeeReturns404() throws Exception {

        StoreFixture store = managedStore();

        MvcResult result = mockMvc.perform(
                        delete("/api/v1/stores/" + store.storeId() + "/employees/00000000-0000-0000-0000-000000000000")
                                .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Terminating an already-terminated employment: 404")
    void terminateAlreadyTerminatedReturns404() throws Exception {

        StoreFixture store = managedStore();
        String employeeId = hiredEmployeeId(store.seatToken(), store.storeId(), uniqueEmail("twice"), "CASHIER");

        mockMvc.perform(delete("/api/v1/stores/" + store.storeId() + "/employees/" + employeeId)
                        .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        MvcResult result = mockMvc.perform(delete("/api/v1/stores/" + store.storeId() + "/employees/" + employeeId)
                        .header("Authorization", "Bearer " + store.seatToken()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }
}
