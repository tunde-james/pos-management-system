package com.devtunde.posbackend.employee.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.devtunde.posbackend.common.api.validation.ValidPhoneNumber;
import com.devtunde.posbackend.employee.api.EmployeeJobTitle;

public record EmployeeHireRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must be at most 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Size(max = 254, message = "Email must be at most 254 characters")
        @Email(
                regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "Please provide a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password,

        @ValidPhoneNumber String phone,

        @NotNull(message = "Job title is required") EmployeeJobTitle jobTitle) {}
