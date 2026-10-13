package com.devtunde.posbackend.store.api.dto;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.devtunde.posbackend.store.api.StoreStatus;
import com.devtunde.posbackend.store.api.StoreType;
import com.fasterxml.jackson.annotation.JsonFormat;

public record StoreApiResponse(
        UUID publicId,
        String brand,
        String description,
        StoreType storeType,
        StoreStatus status,
        Contact contact,
        UUID storeAdminPublicId,
        @JsonFormat(pattern = "HH:mm[:ss]") LocalTime openTime,
        @JsonFormat(pattern = "HH:mm[:ss]") LocalTime closeTime,
        List<DayOfWeek> workingDays,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public record Contact(String address, String phone, String email) {}
}
