package com.devtunde.posbackend.store.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.devtunde.posbackend.store.api.StoreStatus;
import com.devtunde.posbackend.store.api.StoreType;

public record StoreApiResponse(
        UUID publicId,
        String brand,
        String description,
        StoreType storeType,
        StoreStatus status,
        Contact contact,
        UUID storeAdminPublicId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public record Contact(String address, String phone, String email) {}
}
