package com.devtunde.posbackend.employee.api;

import java.util.List;
import java.util.UUID;

import com.devtunde.posbackend.employee.api.dto.EmployeeHireRequest;
import com.devtunde.posbackend.employee.api.dto.EmployeeResponse;

public interface EmployeeService {

    EmployeeResponse hireEmployee(UUID storeId, EmployeeHireRequest request);

    List<EmployeeResponse> listEmployees(UUID storeId, EmployeeJobTitle jobTitle);

    EmployeeResponse getEmployee(UUID storeId, UUID employeePublicId);

    void terminateEmployee(UUID storeId, UUID employeePublicId);
}
