package com.devtunde.posbackend.auth.api.dto;

public record AuthResponse(String accessToken, String message, UserViewResponse user) {}
