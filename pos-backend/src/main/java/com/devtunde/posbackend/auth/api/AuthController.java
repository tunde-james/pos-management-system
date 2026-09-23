package com.devtunde.posbackend.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.posbackend.auth.api.dto.AuthResponse;
import com.devtunde.posbackend.auth.api.dto.LoginRequest;
import com.devtunde.posbackend.auth.api.dto.LogoutRequest;
import com.devtunde.posbackend.auth.api.dto.RefreshRequest;
import com.devtunde.posbackend.auth.api.dto.SignupRequest;
import com.devtunde.posbackend.auth.api.exception.TooManyRequestsException;
import com.devtunde.posbackend.auth.internal.application.RateLimiter;
import com.devtunde.posbackend.auth.internal.config.AuthProperties;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final AuthProperties properties;

    public AuthController(AuthService authService, RateLimiter rateLimiter, AuthProperties properties) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {

        if (!rateLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
            throw new TooManyRequestsException(properties.rateLimit().window().toSeconds());
        }

        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {

        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request, HttpServletRequest httpRequest) {

        String header = httpRequest.getHeader("Authorization");
        String accessToken = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;

        authService.logout(accessToken, request.refreshToken());

        return ResponseEntity.noContent().build();
    }
}
