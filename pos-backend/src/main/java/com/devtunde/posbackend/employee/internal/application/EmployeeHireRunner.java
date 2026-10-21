package com.devtunde.posbackend.employee.internal.application;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.auth.api.UserProvisioningService;
import com.devtunde.posbackend.auth.api.dto.UserProvisioningRequest;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.employee.api.dto.EmployeeHireRequest;
import com.devtunde.posbackend.employee.api.dto.EmployeeResponse;
import com.devtunde.posbackend.employee.api.exception.EmployeeAlreadyEmployedException;
import com.devtunde.posbackend.employee.api.exception.InvalidWorkEmailException;
import com.devtunde.posbackend.employee.internal.config.EmployeeProperties;
import com.devtunde.posbackend.employee.internal.domain.StoreEmployee;
import com.devtunde.posbackend.employee.internal.persistence.StoreEmployeeRepository;

@Service
public class EmployeeHireRunner {

    private final StoreEmployeeRepository storeEmployeeRepository;
    private final UserProvisioningService userProvisioningService;
    private final EmployeeProperties properties;

    public EmployeeHireRunner(
            StoreEmployeeRepository storeEmployeeRepository,
            UserProvisioningService userProvisioningService,
            EmployeeProperties properties) {

        this.storeEmployeeRepository = storeEmployeeRepository;
        this.userProvisioningService = userProvisioningService;
        this.properties = properties;
    }

    @Transactional
    public EmployeeResponse hire(UUID storeId, EmployeeHireRequest request) {

        String email = request.email();
        String domain = email.substring(email.lastIndexOf('@') + 1);

        if (!domain.equalsIgnoreCase(properties.emailDomain())) {
            throw new InvalidWorkEmailException();
        }

        UserViewResponse user = userProvisioningService.provision(
                new UserProvisioningRequest(request.fullName(), request.email(), request.password(), request.phone()));

        if (storeEmployeeRepository.existsByUserPublicIdAndTerminatedAtIsNull(user.publicId())) {
            throw new EmployeeAlreadyEmployedException();
        }

        StoreEmployee employment = StoreEmployee.create(storeId, user.publicId(), request.jobTitle());

        try {
            storeEmployeeRepository.saveAndFlush(employment);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("uk_store_employees_active_user")) {
                throw new EmployeeAlreadyEmployedException();
            }

            throw ex;
        }

        return toResponse(employment, user);
    }

    private EmployeeResponse toResponse(StoreEmployee employment, UserViewResponse user) {
        return new EmployeeResponse(
                employment.getPublicId(),
                employment.getStoreId(),
                employment.getJobTitle(),
                employment.getHiredAt(),
                user);
    }
}
