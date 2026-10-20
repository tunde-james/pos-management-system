package com.devtunde.posbackend.employee.internal.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.employee")
public record EmployeeProperties(@NotBlank String emailDomain) {}
