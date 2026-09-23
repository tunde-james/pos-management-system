package com.devtunde.posbackend.auth.internal.config;

import java.time.Instant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.devtunde.posbackend.auth.internal.application.InMemoryLoginAttemptService;
import com.devtunde.posbackend.auth.internal.application.InMemoryRateLimiter;
import com.devtunde.posbackend.auth.internal.application.InMemoryTokenRevocationService;
import com.devtunde.posbackend.auth.internal.application.LoginAttemptService;
import com.devtunde.posbackend.auth.internal.application.RateLimiter;
import com.devtunde.posbackend.auth.internal.application.TokenRevocationService;

@Configuration
public class AuthBeansConfig {

    @Bean
    LoginAttemptService loginAttemptService(AuthProperties properties) {

        return new InMemoryLoginAttemptService(
                properties.lockout().maxAttempts(), properties.lockout().lockDuration(), Instant::now);
    }

    @Bean
    RateLimiter rateLimiter(AuthProperties properties) {

        return new InMemoryRateLimiter(
                properties.rateLimit().limit(), properties.rateLimit().window(), Instant::now);
    }

    @Bean
    TokenRevocationService tokenRevocationService() {
        return new InMemoryTokenRevocationService();
    }
}
