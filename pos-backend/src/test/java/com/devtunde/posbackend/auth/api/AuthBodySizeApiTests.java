package com.devtunde.posbackend.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest
@AutoConfigureMockMvc
class AuthBodySizeApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("signup rejects fields above their schema-matched @Size caps (400, before Argon2/DB)")
    void oversizeFieldValuesAreRejectedByValidation() throws Exception {
        String body =
                """
                      {
                          "fullName": "%s",
                          "email": "big-%s@devtunde.com",
                          "phone": "+2348010000001",
                          "password": "Password123!"
                      }
                      """.formatted("x".repeat(200), UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
