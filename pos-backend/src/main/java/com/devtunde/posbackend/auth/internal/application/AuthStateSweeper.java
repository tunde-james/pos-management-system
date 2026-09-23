package com.devtunde.posbackend.auth.internal.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthStateSweeper {

    private final LoginAttemptService loginAttemptService;
    private RateLimiter rateLimiter;
    private final TokenRevocationService tokenRevocationService;

    public AuthStateSweeper(
            LoginAttemptService loginAttemptService,
            RateLimiter rateLimiter,
            TokenRevocationService tokenRevocationService) {
        this.loginAttemptService = loginAttemptService;
        this.rateLimiter = rateLimiter;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void sweep() {
        loginAttemptService.purgeStale();
        rateLimiter.purgeStaleWindows();
        tokenRevocationService.purgeExpired();
    }
}
