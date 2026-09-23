package com.devtunde.posbackend.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.AbstractIntegrationTest;

@SpringBootTest(
        properties = {
            "app.security.lockout.max-attempts=2",
            "app.security.lockout.lock-duration=15m",
            "app.security.rate-limit.limit=20" // high — the limiter must not interfere with lockout tests
        })
@AutoConfigureMockMvc
class AuthLockoutApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private static final String PASSWORD = "Password123!";

    private String uniqueEmail() {
        return "locktest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";
    }

    private void signup(String email) throws Exception {
        String body = """
                   {
                       "fullName": "Lockout Test",
                       "email": "%s",
                       "password": "%s",
                       "phone": "08012345678"
                   }
                   """.formatted(email, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private void badLogin(String email) throws Exception {
        String body = """
                   {
                       "email": "%s",
                       "password": "WrongPassword123!"
                   }
                   """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("too many failed logins lock the account — even the correct password gets 423")
    void tooManyFailedLoginsLockTheAccount() throws Exception {
        String email = uniqueEmail();
        signup(email);

        badLogin(email);
        badLogin(email);

        String body = """
                   {
                       "email": "%s",
                       "password": "%s"
                   }
                   """.formatted(email, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.status").value(423))
                .andExpect(jsonPath("$.title").value("Account locked"))
                .andExpect(jsonPath("$.type").value("https://pos.devtunde.com/errors/account-locked"));
    }

    @Test
    @DisplayName("a successful signup clears the failure count for that email")
    void signupClearsTheFailureCount() throws Exception {
        String email = uniqueEmail();

        badLogin(email);
        badLogin(email);

        signup(email);

        String body = """
                   {
                       "email": "%s",
                       "password": "%s"
                   }
                   """.formatted(email, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
