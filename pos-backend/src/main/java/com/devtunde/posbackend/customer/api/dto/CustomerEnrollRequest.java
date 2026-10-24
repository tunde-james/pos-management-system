package com.devtunde.posbackend.customer.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import com.devtunde.posbackend.common.api.validation.ValidPhoneNumber;

public record CustomerEnrollRequest(
        @NotBlank String fullName,
        @NotBlank @ValidPhoneNumber String phone,
        @Email String email) {}
