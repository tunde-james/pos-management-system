package com.devtunde.posbackend.employee.api.dto;

import java.util.UUID;

import com.devtunde.posbackend.employee.api.EmployeeJobTitle;

public record ActiveEmploymentView(UUID storePublicId, EmployeeJobTitle jobTitle) {}
