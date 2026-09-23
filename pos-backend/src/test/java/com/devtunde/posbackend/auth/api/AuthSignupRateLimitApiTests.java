package com.devtunde.posbackend.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
            "app.security.rate-limit.limit=3",
            "app.security.rate-limit.window=1m",
            "app.security.lockout.max-attempts=10" // high — must not interfere with limiter tests
        })
@AutoConfigureMockMvc
class AuthSignupRateLimitApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private String signupBody(String email) {
        return """
                      {
                          "fullName": "Signup Rate Test",
                          "email": "%s",
                          "phone": "+2348010000001",
                          "password": "Password123!"
                      }
                      """.formatted(email);
    }

    @Test
    @DisplayName("signup requests beyond the per-window limit get 429 with Retry-After")
    void tooManySignupRequestsGet429() throws Exception {

        for (int i = 0; i < 3; i++) {
            String email = "signup-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupBody(email)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(
                                "blocked-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too many requests"))
                .andExpect(header().string("Retry-After", "60"));
    }
}
