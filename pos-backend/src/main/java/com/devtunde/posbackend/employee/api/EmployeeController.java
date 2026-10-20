package com.devtunde.posbackend.employee.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.posbackend.employee.api.dto.EmployeeHireRequest;
import com.devtunde.posbackend.employee.api.dto.EmployeeResponse;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> hireEmployee(
            @PathVariable("storeId") UUID storeId, @Valid @RequestBody EmployeeHireRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.hireEmployee(storeId, request));
    }

    @GetMapping
    public List<EmployeeResponse> listEmployees(
            @PathVariable("storeId") UUID storeId,
            @RequestParam(name = "jobTitle", required = false) EmployeeJobTitle jobTitle) {
        return employeeService.listEmployees(storeId, jobTitle);
    }

    @GetMapping("/{employeePublicId}")
    public EmployeeResponse getEmployee(
            @PathVariable("storeId") UUID storeId, @PathVariable("employeePublicId") UUID employeePublicId) {
        return employeeService.getEmployee(storeId, employeePublicId);
    }

    @DeleteMapping("/{employeePublicId}")
    public ResponseEntity<Void> terminateEmployee(
            @PathVariable("storeId") UUID storeId, @PathVariable("employeePublicId") UUID employeePublicId) {

        employeeService.terminateEmployee(storeId, employeePublicId);
        return ResponseEntity.noContent().build();
    }
}
