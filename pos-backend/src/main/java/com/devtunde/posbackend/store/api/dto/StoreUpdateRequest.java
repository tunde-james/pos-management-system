package com.devtunde.posbackend.store.api.dto;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.devtunde.posbackend.common.api.validation.ValidPhoneNumber;
import com.devtunde.posbackend.store.api.StoreStatus;
import com.devtunde.posbackend.store.api.StoreType;

public record StoreUpdateRequest(
        @Pattern(regexp = "(?U)(?s).*\\S.*", message = "Brand must not be blank")
        @Size(max = 100, message = "Brand must be at most 100 characters")
        String brand,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        StoreType storeType,

        StoreStatus status,

        UUID storeAdminPublicId,

        @Valid Contact contact) {

    public record Contact(
            @NotBlank(message = "Address is required")
            @Size(max = 255, message = "Address must be at most 255 characters")
            String address,

            @NotBlank(message = "Phone is required") @ValidPhoneNumber
            String phone,

            @NotBlank(message = "Contact email is required")
            @Size(max = 254, message = "Contact email must be at most 254 characters")
            @Email(
                    regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                    message = "Please provide a valid contact email address")
            String email) {}
}
