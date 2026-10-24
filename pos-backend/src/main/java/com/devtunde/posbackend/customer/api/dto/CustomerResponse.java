package com.devtunde.posbackend.customer.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID publicId, String loyaltyTag, String fullName, String phone, String email, LocalDateTime createdAt) {}
