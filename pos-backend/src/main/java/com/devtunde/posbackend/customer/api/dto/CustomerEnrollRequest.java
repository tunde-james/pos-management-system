package com.devtunde.posbackend.customer.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.devtunde.posbackend.common.api.validation.ValidPhoneNumber;

public record CustomerEnrollRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @ValidPhoneNumber String phone,

        @Email(
                regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "Please provide a valid email address")
        @Size(max = 255)
        String email) {}
