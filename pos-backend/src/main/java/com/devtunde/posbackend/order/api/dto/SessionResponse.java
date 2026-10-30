package com.devtunde.posbackend.order.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponse(
        UUID publicId,
        String status,
        UUID operatorPublicId,
        UUID storePublicId,
        LocalDateTime openedAt,
        LocalDateTime closedAt) {}
