package com.devtunde.posbackend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.AbstractIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class AuthBodySizeApiTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("probe: a ~10MB signup body must not create an account or crash the app")
    void oversizedBodyIsRejectedGracefully() throws Exception {
        String body = """
                   {
                       "fullName": "%s",
                       "email": "big-%s@devtunde.com",
                       "phone": "+2348010000001",
                       "password": "Password123!"
                   }
                   """.formatted(
                        "x".repeat(10_000_000), UUID.randomUUID().toString().substring(0, 8));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        int status = result.getResponse().getStatus();
        System.out.println("BODY-SIZE PROBE STATUS: " + status); // paste this back to me

        assertThat(status).isLessThan(500).isNotEqualTo(201);
    }
}
