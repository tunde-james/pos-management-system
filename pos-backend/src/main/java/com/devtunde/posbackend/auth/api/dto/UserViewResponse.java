package com.devtunde.posbackend.auth.api.dto;

import java.util.UUID;

import com.devtunde.posbackend.auth.api.UserRole;

public record UserViewResponse(UUID publicId, String fullName, String email, String phone, UserRole role) {}
