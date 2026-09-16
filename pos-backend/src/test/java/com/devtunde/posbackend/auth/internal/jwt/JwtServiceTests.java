package com.devtunde.posbackend.auth.internal.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.auth.internal.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

class JwtServiceTests {

    // 64 chars = 512 bits — mirrors production HS512 key size (JWT header is "HS512")
    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";
    private static final String ISSUER = "pos-backend";
    private static final long EXPIRATION_MINUTES = 15;

    private JwtService newService(String secret, String issuer) {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Jwt(secret, EXPIRATION_MINUTES, issuer),
                new AuthProperties.Argon2(16, 32, 1, 16384, 2));
        return new JwtService(properties);
    }

    @Test
    @DisplayName("generateToken then parse roundtrips subject, email, authorities, issuer and expiry")
    void roundtripCarriesExpectedClaims() {
        JwtService service = newService(SECRET, ISSUER);

        String token = service.generateToken(
                "user@devtunde.com",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")));

        Claims claims = service.parse(token);

        assertThat(claims.getSubject()).isEqualTo("user@devtunde.com");
        assertThat(claims.get("email", String.class)).isEqualTo("user@devtunde.com");
        assertThat(claims.get("authorities", String.class)).isEqualTo("ROLE_USER,ROLE_ADMIN");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration().toInstant())
                .isEqualTo(claims.getIssuedAt().toInstant().plusSeconds(EXPIRATION_MINUTES * 60));
    }

    @Test
    @DisplayName("parse rejects a token whose payload was modified after signing")
    void rejectsTamperedToken() {
        JwtService service = newService(SECRET, ISSUER);
        String token = service.generateToken("user@devtunde.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        String tampered = flipLastPayloadChar(token);

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parse rejects a token issued by a different issuer, even with the same key")
    void rejectsForeignIssuer() {
        JwtService issuingService = newService(SECRET, "someone-else");
        JwtService verifyingService = newService(SECRET, ISSUER);

        String token =
                issuingService.generateToken("user@devtunde.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThatThrownBy(() -> verifyingService.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("constructor rejects a secret shorter than 32 characters")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> newService("too-short-secret", ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_JWT_SECRET");
    }

    private String flipLastPayloadChar(String token) {
        String[] parts = token.split("\\.");
        String payload = parts[1];
        char last = payload.charAt(payload.length() - 1);
        char flipped = (last == 'A') ? 'B' : 'A';
        String tamperedPayload = payload.substring(0, payload.length() - 1) + flipped;
        return parts[0] + "." + tamperedPayload + "." + parts[2];
    }
}
