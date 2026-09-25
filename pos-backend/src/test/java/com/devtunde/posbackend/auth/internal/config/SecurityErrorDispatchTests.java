package com.devtunde.posbackend.auth.internal.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.AbstractIntegrationTest;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SecurityErrorDispatchTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password123!";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    private String post(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private HttpResponse<String> get(String path, String bearerToken) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET();

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String loginTokenFor(String email) throws Exception {
        String login = post("/api/v1/auth/login", """
                   {
                       "email": "%s",
                       "password": "%s"
                   }
                   """.formatted(email, PASSWORD));

        return extractField(login, "accessToken");
    }

    private String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    @Test
    @DisplayName("A denied request renders 403 with a body through a REAL error dispatch (not a MockMvc shortcut)")
    void deniedRequestRendersErrorPageWithRealDispatch() throws Exception {
        String email = "edtest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";

        post("/api/v1/auth/signup", """
                   {
                       "fullName": "Error Dispatch Test",
                       "email": "%s",
                       "password": "%s",
                       "phone": "08012345678"
                   }
                   """.formatted(email, PASSWORD));

        HttpResponse<String> response = get("/api/v1/users", loginTokenFor(email));

        assertThat(response.statusCode()).isEqualTo(403);

        assertThat(response.body()).contains("\"status\":403").contains("Forbidden");
    }

    @Test
    @DisplayName("An unauthenticated request renders 401 with a body through a REAL error dispatch")
    void unauthenticatedRequestRendersErrorPageWithRealDispatch() throws Exception {
        HttpResponse<String> response = get("/api/v1/users", null);

        assertThat(response.statusCode()).isEqualTo(401);

        assertThat(response.body()).contains("\"status\":401").contains("Unauthorized");
    }

    @Test
    @DisplayName("The /error endpoint itself is reachable (no security mangling of the error page)")
    void errorEndpointItselfIsReachable() throws Exception {
        HttpResponse<String> response = get("/error", null);

        assertThat(response.statusCode()).isNotEqualTo(401);

        assertThat(response.body()).contains("\"error\"");
    }
}
