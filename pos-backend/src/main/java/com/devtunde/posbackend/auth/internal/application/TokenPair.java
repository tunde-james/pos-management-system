package com.devtunde.posbackend.auth.internal.application;

import com.devtunde.posbackend.auth.internal.domain.User;

public record TokenPair(String accessToken, String refreshToken, User user) {}
