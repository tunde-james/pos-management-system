package com.devtunde.posbackend.auth.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.auth.api.exception.InvalidCredentialsException;
import com.devtunde.posbackend.auth.internal.config.AuthProperties;
import com.devtunde.posbackend.auth.internal.domain.RefreshToken;
import com.devtunde.posbackend.auth.internal.domain.User;
import com.devtunde.posbackend.auth.internal.jwt.JwtService;
import com.devtunde.posbackend.auth.internal.persistence.RefreshTokenRepository;
import com.devtunde.posbackend.auth.internal.persistence.UserRepository;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final Duration refreshTtl;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            JwtService jwtService,
            AuthProperties properties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.refreshTtl = properties.refresh().ttl();
    }

    @Transactional
    public TokenPair createSession(User user) {

        String refreshToken = UUID.randomUUID().toString();

        refreshTokenRepository.save(
                RefreshToken.issue(UUID.randomUUID(), user.getId(), hash(refreshToken), refreshTtl));

        return new TokenPair(accessTokenFor(user), refreshToken, user);
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {

        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(hash(refreshToken))
                .orElseThrow(InvalidCredentialsException::new);

        if (stored.isRevoked()) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), LocalDateTime.now());

            throw new InvalidCredentialsException();
        }

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException();
        }

        stored.revoke();
        refreshTokenRepository.save(stored);

        String newRefreshToken = UUID.randomUUID().toString();
        refreshTokenRepository.save(
                RefreshToken.issue(stored.getFamilyId(), stored.getUserId(), hash(newRefreshToken), refreshTtl));

        User user = userRepository.findById(stored.getUserId()).orElseThrow(InvalidCredentialsException::new);

        return new TokenPair(accessTokenFor(user), newRefreshToken, user);
    }

    @Transactional
    public void revokeFamilyByToken(String refreshToken) {

        Optional<RefreshToken> stored = refreshTokenRepository.findByTokenHash(hash(refreshToken));

        if (stored.isPresent()) {
            refreshTokenRepository.revokeFamily(stored.get().getFamilyId(), LocalDateTime.now());
        }
    }

    private String accessTokenFor(User user) {

        return jwtService.generateToken(
                user.getEmail(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
