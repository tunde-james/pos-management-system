package com.devtunde.posbackend.users.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        properties = {"app.security.rate-limit.limit=100" // high — limiter must not interfere with users tests
        })
@AutoConfigureMockMvc
class UsersApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final String PASSWORD = "Password123!";

    private String uniqueEmail() {
        return "utest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";
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

    @Test
    @DisplayName("GET /api/v1/users/profile without a token returns 401")
    void profileWithoutTokenReturns401() throws Exception {

        mockMvc.perform(get("/api/v1/users/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users/profile with a garbage token returns 401")
    void profileWithGarbageTokenReturns401() throws Exception {

        mockMvc.perform(get("/api/v1/users/profile").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST to the read-only profile route returns 405 with an Allow header, not 500")
    void wrongMethodToProfileReturns405() throws Exception {
        String email = uniqueEmail();
        signupUser(email, "Method Not Allowed");

        String token = loginForToken(email);

        mockMvc.perform(post("/api/v1/users/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Method not allowed"))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    @DisplayName("GET /api/v1/users/profile returns the current user's own view, never the password hash")
    void profileReturnsCurrentUser() throws Exception {
        String email = uniqueEmail();
        signupUser(email, "Users Slice Tester");

        String token = loginForToken(email);

        mockMvc.perform(get("/api/v1/users/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Users Slice Tester"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("A logged-out access token can no longer read /api/v1/users/profile (jti revocation, end to end)")
    void profileForbiddenAfterLogout() throws Exception {
        String email = uniqueEmail();
        signupUser(email, "Logged Out Tester");

        String login = loginJson(email);
        String accessToken = objectMapper.readTree(login).get("accessToken").asText();
        String refreshToken = objectMapper.readTree(login).get("refreshToken").asText();

        String logoutBody = """
                   {
                       "refreshToken": "%s"
                   }
                   """.formatted(refreshToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/profile").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users/{id} is forbidden for a regular user")
    void getByIdForbiddenForRegularUser() throws Exception {
        String ada = uniqueEmail();
        String bola = uniqueEmail();
        signupUser(ada, "Ada Obi");
        signupUser(bola, "Bola Ade");

        String token = loginForToken(ada);
        String bolaId = publicIdOf(bola);

        mockMvc.perform(get("/api/v1/users/" + bolaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/{id} returns the target user for an admin")
    void getByIdReturnsUserForAdmin() throws Exception {
        String ada = uniqueEmail();
        String bola = uniqueEmail();
        signupUser(ada, "Ada Admin");
        signupUser(bola, "Bola Ade");

        promoteToAdmin(ada);
        String adminToken = loginForToken(ada); // token issued AFTER promotion carries ROLE_ADMIN
        String bolaId = publicIdOf(bola);

        mockMvc.perform(get("/api/v1/users/" + bolaId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(bola))
                .andExpect(jsonPath("$.fullName").value("Bola Ade"));
    }

    @Test
    @DisplayName("GET /api/v1/users/{id} with an unknown id returns 404")
    void getByIdUnknownReturns404() throws Exception {
        String ada = uniqueEmail();
        signupUser(ada, "Ada Admin");
        promoteToAdmin(ada);
        String adminToken = loginForToken(ada);

        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/users (list) is forbidden for a regular user")
    void listForbiddenForRegularUser() throws Exception {
        String ada = uniqueEmail();
        signupUser(ada, "Ada Obi");
        String token = loginForToken(ada);

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users returns every user for an admin")
    void listReturnsAllForAdmin() throws Exception {
        String ada = uniqueEmail();
        String bola = uniqueEmail();
        signupUser(ada, "Ada Admin");
        signupUser(bola, "Bola Ade");

        promoteToAdmin(ada);
        String adminToken = loginForToken(ada);

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[*].email", hasItem(ada)))
                .andExpect(jsonPath("$[*].email", hasItem(bola)));
    }
}
