package com.devtunde.posbackend.employee.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.employee.api.EmployeeJobTitle;

public record EmployeeResponse(
        UUID publicId, UUID storeId, EmployeeJobTitle jobTitle, LocalDateTime hiredAt, UserViewResponse user) {}
