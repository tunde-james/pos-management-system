package com.devtunde.posbackend.auth.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.AbstractIntegrationTest;
import com.devtunde.posbackend.auth.api.exception.InvalidCredentialsException;
import com.devtunde.posbackend.auth.internal.config.AuthProperties;
import com.devtunde.posbackend.auth.internal.domain.User;
import com.devtunde.posbackend.auth.internal.jwt.JwtService;
import com.devtunde.posbackend.auth.internal.persistence.RefreshTokenRepository;
import com.devtunde.posbackend.auth.internal.persistence.UserRepository;
import io.jsonwebtoken.Claims;

public class RefreshTokenServiceTests extends AbstractIntegrationTest {

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    private RefreshTokenService newService(Duration refreshTtl) {
        AuthProperties properties = new AuthProperties(null, null, null, null, new AuthProperties.Refresh(refreshTtl));
        return new RefreshTokenService(refreshTokenRepository, userRepository, jwtService, properties);
    }

    private User createUser() {
        String email = "rtest-" + UUID.randomUUID().toString().substring(0, 8) + "@devtunde.com";

        return userRepository.save(User.register("Refresh Test", email, "08012345678", "unused-hash"));
    }

    @Test
    @DisplayName("createSession issues an access token for the user and a fresh refresh token")
    void createSessionIssuesBothTokens() {
        RefreshTokenService service = newService(Duration.ofDays(7));
        User user = createUser();

        TokenPair pair = service.createSession(user);

        assertThat(UUID.fromString(pair.refreshToken())).isNotNull();
        Claims claims = jwtService.parse(pair.accessToken());

        assertThat(claims.getSubject()).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("refreshing rotates the token — the old one is no longer usable")
    void refreshRotatesTheToken() {
        RefreshTokenService service = newService(Duration.ofDays(7));
        TokenPair first = service.createSession(createUser());

        TokenPair second = service.refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThatThrownBy(() -> service.refresh(first.refreshToken())).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("replaying an old token burns the whole family — the newest token dies too")
    void replayingAnOldTokenBurnsTheFamily() {
        RefreshTokenService service = newService(Duration.ofDays(7));
        TokenPair first = service.createSession(createUser());
        TokenPair second = service.refresh(first.refreshToken());

        assertThatThrownBy(() -> service.refresh(first.refreshToken())).isInstanceOf(InvalidCredentialsException.class);

        assertThatThrownBy(() -> service.refresh(second.refreshToken()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("refreshing with a token we never issued returns 401")
    void unknownTokenReturns401() {
        RefreshTokenService service = newService(Duration.ofDays(7));

        assertThatThrownBy(() -> service.refresh("not-a-real-refresh-token"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("an expired refresh token returns 401")
    void expiredTokenReturns401() throws InterruptedException {
        RefreshTokenService service = newService(Duration.ofMillis(10));
        TokenPair pair = service.createSession(createUser());

        Thread.sleep(30); // one real, tiny sleep — the expiry test needs real time to pass

        assertThatThrownBy(() -> service.refresh(pair.refreshToken())).isInstanceOf(InvalidCredentialsException.class);
    }
}
