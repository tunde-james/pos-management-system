package com.devtunde.posbackend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class AuthRefreshApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PASSWORD = "Password123!";

    private String uniqueEmail() {
        return "refreshtest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";
    }

    private JsonNode signup(String email) throws Exception {
        String body = """
                   {
                       "fullName": "Refresh Test",
                       "email": "%s",
                       "password": "%s",
                       "phone": "08012345678"
                   }
                   """.formatted(email, PASSWORD);

        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = """
                   {
                       "email": "%s",
                       "password": "%s"
                   }
                   """.formatted(email, password);

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String body = """
                   {
                       "refreshToken": "%s"
                   }
                   """.formatted(refreshToken);

        String response = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    private void expectUnauthorizedRefresh(String refreshToken) throws Exception {
        String body = """
                   {
                       "refreshToken": "%s"
                   }
                   """.formatted(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("signup returns an access token and a refresh token")
    void signupReturnsBothTokens() throws Exception {
        JsonNode body = signup(uniqueEmail());

        assertThat(body.get("accessToken").asText()).isNotEmpty();

        assertThat(body.get("refreshToken").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("login returns a refresh token alongside the access token")
    void loginReturnsBothTokens() throws Exception {
        String email = uniqueEmail();
        signup(email);

        JsonNode body = login(email, PASSWORD);

        assertThat(body.get("accessToken").asText()).isNotEmpty();

        assertThat(body.get("refreshToken").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("refreshing rotates the pair — the old refresh token then fails with 401")
    void refreshRotatesAndOldTokenDies() throws Exception {
        JsonNode first = signup(uniqueEmail());
        String firstRefresh = first.get("refreshToken").asText();

        JsonNode second = refresh(firstRefresh);

        assertThat(second.get("refreshToken").asText()).isNotEqualTo(firstRefresh);

        assertThat(second.get("accessToken").asText()).isNotEmpty();

        expectUnauthorizedRefresh(firstRefresh);
    }

    @Test
    @DisplayName("replaying an old refresh token burns the family — the newest token also gets 401")
    void replayBurnsTheFamily() throws Exception {
        JsonNode first = signup(uniqueEmail());
        String firstRefresh = first.get("refreshToken").asText();
        JsonNode second = refresh(firstRefresh);
        String secondRefresh = second.get("refreshToken").asText();

        expectUnauthorizedRefresh(
                firstRefresh); // replay detected — family burned
        expectUnauthorizedRefresh(
                secondRefresh); // the newest token is dead too
    }

    @Test
    @DisplayName("refreshing with a garbage token returns 401")
    void garbageRefreshTokenReturns401() throws Exception {
        expectUnauthorizedRefresh("not-a-real-refresh-token");
    }
}
