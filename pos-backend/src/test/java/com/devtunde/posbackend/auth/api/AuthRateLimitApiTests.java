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
            "app.security.lockout.max-attempts=10" // high — lockout must not interfere with limiter tests
        })
@AutoConfigureMockMvc
class AuthRateLimitApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private String loginBody(String email) {
        return """
                   {
                       "email": "%s",
                       "password": "WrongPassword123!"
                   }
                   """.formatted(email);
    }

    @Test
    @DisplayName("login requests beyond the per-window limit get 429 with Retry-After")
    void tooManyLoginRequestsGet429WithRetryAfter() throws Exception {
        String email = "ratetest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";

        for (int i = 0; i < 3; i++) {

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email)))
                    .andExpect(status().isUnauthorized()); // unknown email, not locked yet (max-attempts=10)
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too many requests"))
                .andExpect(jsonPath("$.type").value("https://pos.devtunde.com/errors/too-many-requests"))
                .andExpect(header().string("Retry-After", "60"));
    }
}
