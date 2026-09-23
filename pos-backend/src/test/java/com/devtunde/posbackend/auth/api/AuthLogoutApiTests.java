package com.devtunde.posbackend.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AuthLogoutApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PASSWORD = "Password123!";

    @Test
    @DisplayName("logout revokes the access token and burns the refresh family")
    void logoutRevokesAccessTokenAndBurnsRefreshFamily() throws Exception {
        String email = "logouttest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";
        String body = """
                   {
                       "fullName": "Logout Test",
                       "email": "%s",
                       "password": "%s",
                       "phone": "08012345678"
                   }
                   """.formatted(email, PASSWORD);

        JsonNode session = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String accessToken = session.get("accessToken").asText();
        String refreshToken = session.get("refreshToken").asText();

        // valid token on a nonexistent route = 404 (authentication passed)
        mockMvc.perform(get("/api/v1/some-protected-route").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());

        String logoutBody = """
                   {
                       "refreshToken": "%s"
                   }
                   """.formatted(refreshToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content(logoutBody))
                .andExpect(status().isNoContent());

        // the access token is now dead
        mockMvc.perform(get("/api/v1/some-protected-route").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        // and the refresh family is burned
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutBody))
                .andExpect(status().isUnauthorized());
    }
}
