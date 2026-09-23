package com.devtunde.posbackend.auth.internal.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.auth.api.dto.LoginRequest;
import com.devtunde.posbackend.auth.api.exception.InvalidCredentialsException;
import com.devtunde.posbackend.auth.internal.domain.User;
import com.devtunde.posbackend.auth.internal.jwt.JwtService;
import com.devtunde.posbackend.auth.internal.mapper.UserMapper;
import com.devtunde.posbackend.auth.internal.persistence.UserRepository;

class AuthServiceImplTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);

    private AuthServiceImpl newService() {

        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");

        return new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                mock(JwtService.class),
                mock(UserMapper.class),
                mock(PhoneNormalizer.class),
                loginAttemptService,
                mock(RefreshTokenService.class),
                mock(TokenRevocationService.class));
    }

    @Test
    @DisplayName("login with an unknown email still runs the Argon2 check — no timing tell")
    void unknownEmailStillPaysPasswordHashCost() {
        AuthServiceImpl service = newService();

        when(userRepository.findByEmail("ghost@devtunde.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@devtunde.com", "WrongPassword123!")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder).matches("WrongPassword123!", "dummy-hash");

        verify(loginAttemptService).recordFailure("ghost@devtunde.com");
    }

    @Test
    @DisplayName("login with a wrong password for a real account still fails and records the attempt")
    void wrongPasswordForRealAccountRecordsFailure() {
        AuthServiceImpl service = newService();
        User user = User.register("Real User", "real@devtunde.com", "08012345678", "real-hash");

        when(userRepository.findByEmail("real@devtunde.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword123!", "real-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("real@devtunde.com", "WrongPassword123!")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailure("real@devtunde.com");
    }
}
