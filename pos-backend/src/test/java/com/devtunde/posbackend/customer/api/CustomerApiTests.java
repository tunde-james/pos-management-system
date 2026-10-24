package com.devtunde.posbackend.customer.api;

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
class CustomerApiTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_PASSWORD = "Password123!";
    private static final String WORK_DOMAIN = "postest.com";

    // ── fixtures ────────────────────────────────────────────────────────────

    private String uniqueEmail(String prefix) {

        return prefix + "-" + System.nanoTime() + "@" + WORK_DOMAIN;
    }

    private String uniquePhone() {

        return "+234809" + String.format("%07d", System.nanoTime() % 10_000_000L);
    }

    private JsonNode jsonOf(MvcResult result) throws Exception {

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void signupUser(String email, String fullName) throws Exception {

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content("""
                           {
                                "fullName": "%s",
                                "email": "%s",
                                "password": "%s",
                                "phone": "+2348091234567"
                           }
                           """.formatted(fullName, email, DEFAULT_PASSWORD)))
                .andReturn();
    }

    private String loginForToken(String email) throws Exception {

        return loginResult(email).get("accessToken").asText();
    }

    private String publicIdOf(String email) throws Exception {

        return loginResult(email).get("user").get("publicId").asText();
    }

    private JsonNode loginResult(String email) throws Exception {

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                           {
                                 "email": "%s",
                                 "password": "%s"
                           }
                           """.formatted(email, DEFAULT_PASSWORD)))
                .andReturn();

        return jsonOf(result);
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
                                "brand":"Cust Brand %d",
                                "storeType":"KIOSK",
                                "storeAdminPublicId":"%s",
                                "contact":{"address":"Cust Way",
                                "phone":"08012345678",
                                "email":"%s"
                            }
                        }
                        """.formatted(
                                        System.nanoTime(),
                                        seatAdminPublicId,
                                        "brand-" + System.nanoTime() + "@" + WORK_DOMAIN)))
                .andReturn();

        return jsonOf(result).get("publicId").asText();
    }

    private record SeatFixture(String seatToken, String storeId) {}

    private SeatFixture seatWithStore() throws Exception {

        String seatEmail = uniqueEmail("cust-seat");
        signupUser(seatEmail, "Cust Seat Admin");

        String storeId = createStoreAsAdmin(adminToken(), publicIdOf(seatEmail));

        return new SeatFixture(loginForToken(seatEmail), storeId);
    }

    // ── request helpers ─────────────────────────────────────────────────────

    private String enrollBody(String fullName, String phone, String email) {

        String emailField = email == null ? "" : ",\n      \"email\": \"%s\"".formatted(email);

        return """
                {
                    "fullName": "%s",
                    "phone": "%s"%s
                }
                """.formatted(fullName, phone, emailField);
    }

    private MvcResult enrollAs(String token, String body) throws Exception {

        return mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andReturn();
    }

    private MvcResult lookupByPhone(String token, String phone) throws Exception {

        return mockMvc.perform(get("/api/v1/customers/phone/" + phone).header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private MvcResult getCustomer(String token, String publicId) throws Exception {

        return mockMvc.perform(get("/api/v1/customers/" + publicId).header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private MvcResult listCustomers(String token) throws Exception {

        return mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + token))
                .andReturn();
    }

    // ── enroll ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A seat admin enrolls a fresh customer: 201, 8-digit tag, lower-cased email")
    void seatAdminEnrollsFreshCustomerReturns201WithEightDigitTag() throws Exception {

        SeatFixture seat = seatWithStore();

        MvcResult result =
                enrollAs(seat.seatToken(), enrollBody("Adaeze Obi", uniquePhone(), "Mixed.Case@PostTest.com"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        JsonNode body = jsonOf(result);

        assertThat(body.get("publicId").asText()).isNotBlank();

        assertThat(body.get("loyaltyTag").asText()).matches("\\d{8}");

        assertThat(body.get("fullName").asText()).isEqualTo("Adaeze Obi");

        assertThat(body.get("email").asText()).isEqualTo("mixed.case@posttest.com");
    }

    @Test
    @DisplayName("The chain admin can also enroll: 201")
    void chainAdminEnrollsCustomerReturns201() throws Exception {

        MvcResult result = enrollAs(adminToken(), enrollBody("Chain Admins Friend", uniquePhone(), null));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("Re-enrolling the same phone returns the SAME customer, untouched: 200")
    void reEnrollSamePhoneReturns200WithSameUntouchedCustomer() throws Exception {

        SeatFixture seat = seatWithStore();

        String phone = uniquePhone();

        MvcResult first = enrollAs(seat.seatToken(), enrollBody("Adaeze Obi", phone, "adaeze@postest.com"));

        MvcResult second = enrollAs(seat.seatToken(), enrollBody("Impostor Kid", phone, "impostor@postest.com"));

        assertThat(second.getResponse().getStatus()).isEqualTo(200);

        JsonNode firstBody = jsonOf(first);
        JsonNode secondBody = jsonOf(second);

        assertThat(secondBody.get("publicId").asText())
                .isEqualTo(firstBody.get("publicId").asText());

        assertThat(secondBody.get("loyaltyTag").asText())
                .isEqualTo(firstBody.get("loyaltyTag").asText());

        assertThat(secondBody.get("fullName").asText()).isEqualTo("Adaeze Obi");

        assertThat(secondBody.get("email").asText()).isEqualTo("adaeze@postest.com");
    }

    @Test
    @DisplayName("Email is not a unique key: two customers may share one, distinct records otherwise")
    void twoCustomersShareEmailReturnsDistinctRecords() throws Exception {

        String adminToken = adminToken();
        String sharedEmail = "shared@postest.com";

        MvcResult first = enrollAs(adminToken, enrollBody("First Shopper", uniquePhone(), sharedEmail));
        MvcResult second = enrollAs(adminToken, enrollBody("Second Shopper", uniquePhone(), sharedEmail));

        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        assertThat(second.getResponse().getStatus()).isEqualTo(201);

        JsonNode firstBody = jsonOf(first);
        JsonNode secondBody = jsonOf(second);

        assertThat(secondBody.get("publicId").asText())
                .isNotEqualTo(firstBody.get("publicId").asText());

        assertThat(secondBody.get("loyaltyTag").asText())
                .isNotEqualTo(firstBody.get("loyaltyTag").asText());
    }

    // ── validation ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("A garbage phone is refused: 400")
    void enrollWithGarbagePhoneReturns400() throws Exception {

        SeatFixture seat = seatWithStore();

        MvcResult result = enrollAs(seat.seatToken(), enrollBody("Bad Phone", "not-a-phone", null));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("A blank full name is refused: 400")
    void enrollWithBlankNameReturns400() throws Exception {

        SeatFixture seat = seatWithStore();

        MvcResult result = enrollAs(seat.seatToken(), enrollBody("   ", uniquePhone(), null));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("A malformed email is refused: 400")
    void enrollWithMalformedEmailReturns400() throws Exception {

        SeatFixture seat = seatWithStore();

        MvcResult result = enrollAs(seat.seatToken(), enrollBody("Bad Email", uniquePhone(), "nope-not-an-email"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    // ── lookup and read ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Lookup by phone finds the enrolled customer with matching publicId: 200")
    void lookupByPhoneReturns200WithMatchingPublicId() throws Exception {

        SeatFixture seat = seatWithStore();

        String phone = uniquePhone();

        MvcResult enrolled = enrollAs(seat.seatToken(), enrollBody("Lookup Me", phone, null));
        MvcResult lookedUp = lookupByPhone(seat.seatToken(), phone);

        assertThat(lookedUp.getResponse().getStatus()).isEqualTo(200);

        JsonNode enrolledBody = jsonOf(enrolled);
        JsonNode lookedUpBody = jsonOf(lookedUp);

        assertThat(lookedUpBody.get("publicId").asText())
                .isEqualTo(enrolledBody.get("publicId").asText());

        assertThat(lookedUpBody.get("loyaltyTag").asText())
                .isEqualTo(enrolledBody.get("loyaltyTag").asText());
    }

    @Test
    @DisplayName("Lookup by an unknown phone: 404")
    void lookupByUnknownPhoneReturns404() throws Exception {

        SeatFixture seat = seatWithStore();

        MvcResult result = lookupByPhone(seat.seatToken(), "+2348099999999");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Read by publicId returns the customer: 200")
    void getByPublicIdReturns200() throws Exception {

        SeatFixture seat = seatWithStore();

        MvcResult enrolled = enrollAs(seat.seatToken(), enrollBody("Read Me", uniquePhone(), null));

        String publicId = jsonOf(enrolled).get("publicId").asText();

        MvcResult result = getCustomer(seat.seatToken(), publicId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(jsonOf(result).get("publicId").asText()).isEqualTo(publicId);
    }

    @Test
    @DisplayName("Read by an unknown publicId: 404")
    void getByUnknownPublicIdReturns404() throws Exception {

        SeatFixture seat = seatWithStore();

        MvcResult result = getCustomer(seat.seatToken(), "00000000-0000-0000-0000-000000000000");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    // ── gates ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A regular user (no store, no admin) cannot enroll: 403")
    void regularUserEnrollReturns403() throws Exception {

        String email = uniqueEmail("nobody");
        signupUser(email, "Nobody");
        String token = loginForToken(email);

        MvcResult result = enrollAs(token, enrollBody("Snoop Shopper", uniquePhone(), null));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("A regular user cannot look customers up: 403")
    void regularUserLookupReturns403() throws Exception {

        String email = uniqueEmail("nobody");
        signupUser(email, "Nobody");
        String token = loginForToken(email);

        MvcResult result = lookupByPhone(token, "+2348091234567");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("No token at all: 401")
    void anonymousEnrollReturns401() throws Exception {

        MvcResult result = enrollAs(null, enrollBody("Tokenless Shopper", uniquePhone(), null));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("A seat admin cannot list the chain's customer book: 403")
    void seatAdminListReturns403() throws Exception {

        SeatFixture seat = seatWithStore();

        MvcResult result = listCustomers(seat.seatToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("The chain admin lists customers, newest first: 200")
    void chainAdminListReturns200NewestFirst() throws Exception {

        String adminToken = adminToken();

        enrollAs(adminToken, enrollBody("Older Shopper", uniquePhone(), null));
        MvcResult second = enrollAs(adminToken, enrollBody("Newer Shopper", uniquePhone(), null));

        MvcResult result = listCustomers(adminToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode list = jsonOf(result);

        assertThat(list.size()).isGreaterThanOrEqualTo(2);

        assertThat(list.get(0).get("publicId").asText())
                .isEqualTo(jsonOf(second).get("publicId").asText());
    }

    @Test
    @DisplayName("Closing the store revokes its seat admin's customer access: 403")
    void closedStoreSeatAdminEnrollReturns403() throws Exception {

        String adminToken = adminToken();

        String seatEmail = uniqueEmail("doomed-seat");
        signupUser(seatEmail, "Doomed Seat Admin");

        String storeId = createStoreAsAdmin(adminToken, publicIdOf(seatEmail));
        String seatToken = loginForToken(seatEmail);

        MvcResult closed = mockMvc.perform(
                        delete("/api/v1/stores/" + storeId).header("Authorization", "Bearer " + adminToken))
                .andReturn();

        assertThat(closed.getResponse().getStatus()).isEqualTo(204);

        MvcResult result = enrollAs(seatToken, enrollBody("Ghost Seat Shopper", uniquePhone(), null));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}
