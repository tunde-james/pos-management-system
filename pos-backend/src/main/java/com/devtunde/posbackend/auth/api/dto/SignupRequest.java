package com.devtunde.posbackend.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.devtunde.posbackend.auth.api.validation.ValidPhoneNumber;

public record SignupRequest(
        @NotBlank(message = "Full name is required") String fullName,

        @NotBlank(message = "Email is required")
        @Email(
                regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "Please provide a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password,

        @ValidPhoneNumber String phone) {}
