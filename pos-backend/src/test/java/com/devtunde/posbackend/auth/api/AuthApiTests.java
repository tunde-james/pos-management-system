package com.devtunde.posbackend.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.AbstractIntegrationTest;

@AutoConfigureMockMvc
class AuthApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private static final String PASSWORD = "Password123!";

    private String signupBody(String email, String phone) {
        return """
                {
                    "fullName": "Integration Test",
                    "email": "%s",
                    "password": "%s",
                    "phone": "%s"
                }
                """.formatted(email, PASSWORD, phone);
    }

    private String uniqueEmail() {
        return "itest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";
    }

    @Test
    @DisplayName("Signup with local Nigerian format (080...) returns 201 and stores phone as E.164")
    void signupReturns201AndNormalizedE164Phone() throws Exception {
        String email = uniqueEmail();
        String body = signupBody(email, "08012345678");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.phone").value("+2348012345678"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.message").value("Signup successful"));
    }

    @Test
    @DisplayName("Signup with international format (+234 ...) returns 201 and stores phone as E.164")
    void signupInternationalFormatAlsoNormalizesToE164() throws Exception {
        String email = uniqueEmail();
        String body = signupBody(email, "+234 801 234 5679");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.phone").value("+2348012345679"));
    }

    @Test
    @DisplayName("Duplicate email signup returns 409 with full RFC 7807 problem detail")
    void signupDuplicateEmailReturns409WithProblemDetail() throws Exception {
        String email = uniqueEmail();
        String body = signupBody(email, "08012345678");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Email already registered"))
                .andExpect(jsonPath("$.type").value("https://pos.devtunde.com/errors/email-already-registered"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/signup"));
    }

    @Test
    @DisplayName("Invalid signup body (blank name, bad email, short password, junk phone) returns 400")
    void signupInvalidBodyReturns400() throws Exception {
        String body = """
                {
                    "fullName": "",
                    "email": "not-an-email",
                    "password": "short",
                    "phone": "123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login with valid credentials returns 200 and a JWT")
    void loginWithValidCredentialsReturns200AndToken() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(email, "08012345678")))
                .andExpect(status().isCreated());

        String loginBody = """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    @DisplayName("Protected route without Bearer token returns 401")
    void anyApiV1RouteWithoutBearerTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/some-protected-route")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected route with garbage Bearer token returns 401")
    void anyApiV1RouteWithGarbageBearerTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/some-protected-route").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login with wrong password returns 401")
    void loginWithWrongPasswordReturns401() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(email, "08012345678")))
                .andExpect(status().isCreated());

        String loginBody = """
                {
                    "email": "%s",
                    "password": "WrongPassword123!"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());
    }
}
