package com.devtunde.posbackend.auth.internal.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.auth.api.AuthService;
import com.devtunde.posbackend.auth.api.dto.AuthResponse;
import com.devtunde.posbackend.auth.api.dto.LoginRequest;
import com.devtunde.posbackend.auth.api.dto.RefreshRequest;
import com.devtunde.posbackend.auth.api.dto.SignupRequest;
import com.devtunde.posbackend.auth.api.exception.AccountLockedException;
import com.devtunde.posbackend.auth.api.exception.EmailAlreadyRegisteredException;
import com.devtunde.posbackend.auth.api.exception.InvalidCredentialsException;
import com.devtunde.posbackend.auth.internal.domain.User;
import com.devtunde.posbackend.auth.internal.jwt.JwtService;
import com.devtunde.posbackend.auth.internal.mapper.UserMapper;
import com.devtunde.posbackend.auth.internal.persistence.UserRepository;
import com.devtunde.posbackend.common.api.validation.PhoneNormalizer;
import io.jsonwebtoken.Claims;

@Service
public class AuthServiceImpl implements AuthService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final PhoneNormalizer phoneNormalizer;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final String dummyHash;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserMapper userMapper,
            PhoneNormalizer phoneNormalizer,
            LoginAttemptService loginAttemptService,
            RefreshTokenService refreshTokenService,
            TokenRevocationService tokenRevocationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.phoneNormalizer = phoneNormalizer;
        this.loginAttemptService = loginAttemptService;
        this.refreshTokenService = refreshTokenService;
        this.tokenRevocationService = tokenRevocationService;
        this.dummyHash = passwordEncoder.encode("no-such-account-dummy-password");
    }

    @Override
    @Transactional
    public AuthResponse signup(SignupRequest request) {

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyRegisteredException(request.email());
        }

        User user = User.register(
                request.fullName(),
                request.email(),
                phoneNormalizer.normalize(request.phone()),
                passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        loginAttemptService.clearAttempts(request.email());

        TokenPair pair = refreshTokenService.createSession(user);

        return new AuthResponse(pair.accessToken(), pair.refreshToken(), "Signup successful", userMapper.toView(user));
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {

        if (loginAttemptService.isLocked(request.email())) {
            throw new AccountLockedException();
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);

        String passwordHash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);

        if (user == null || !passwordMatches) {
            loginAttemptService.recordFailure(request.email());
            throw new InvalidCredentialsException();
        }

        loginAttemptService.clearAttempts(request.email());

        user.recordLogin();

        TokenPair pair = refreshTokenService.createSession(user);

        return new AuthResponse(pair.accessToken(), pair.refreshToken(), "Login successful", userMapper.toView(user));
    }

    @Override
    public AuthResponse refresh(RefreshRequest request) {

        TokenPair pair = refreshTokenService.refresh(request.refreshToken());

        return new AuthResponse(
                pair.accessToken(), pair.refreshToken(), "Refresh successful", userMapper.toView(pair.user()));
    }

    @Override
    public void logout(String accessToken, String refreshToken) {

        if (accessToken != null) {
            try {
                Claims claims = jwtService.parse(accessToken);
                tokenRevocationService.revoke(
                        claims.getId(), claims.getExpiration().toInstant());
            } catch (Exception e) {
            }
        }

        refreshTokenService.revokeFamilyByToken(refreshToken);
    }
}
