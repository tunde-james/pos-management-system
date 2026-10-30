package com.devtunde.posbackend.employee.internal.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.auth.api.UserAccountService;
import com.devtunde.posbackend.auth.api.UserRole;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.auth.api.exception.UserProvisioningRaceException;
import com.devtunde.posbackend.employee.api.EmployeeJobTitle;
import com.devtunde.posbackend.employee.api.EmployeeService;
import com.devtunde.posbackend.employee.api.dto.ActiveEmploymentView;
import com.devtunde.posbackend.employee.api.dto.EmployeeHireRequest;
import com.devtunde.posbackend.employee.api.dto.EmployeeResponse;
import com.devtunde.posbackend.employee.api.exception.EmployeeNotFoundException;
import com.devtunde.posbackend.employee.internal.domain.StoreEmployee;
import com.devtunde.posbackend.employee.internal.persistence.StoreEmployeeRepository;
import com.devtunde.posbackend.store.api.StoreService;
import com.devtunde.posbackend.store.api.StoreStatus;
import com.devtunde.posbackend.store.api.dto.StoreApiResponse;
import com.devtunde.posbackend.store.api.exception.StoreNotFoundException;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final StoreEmployeeRepository storeEmployeeRepository;
    private final StoreService storeService;
    private final UserAccountService userAccountService;
    private final EmployeeHireRunner hireRunner;

    public EmployeeServiceImpl(
            StoreEmployeeRepository storeEmployeeRepository,
            StoreService storeService,
            UserAccountService userAccountService,
            EmployeeHireRunner hireRunner) {

        this.storeEmployeeRepository = storeEmployeeRepository;
        this.storeService = storeService;
        this.userAccountService = userAccountService;
        this.hireRunner = hireRunner;
    }

    @Override
    @Transactional
    public EmployeeResponse hireEmployee(UUID storeId, EmployeeHireRequest request) {

        requireStore(storeId);

        try {
            return hireRunner.hire(storeId, request);
        } catch (UserProvisioningRaceException ex) {
            return hireRunner.hire(storeId, request);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> listEmployees(UUID storeId, EmployeeJobTitle jobTitle) {

        requireStore(storeId);

        List<StoreEmployee> roster = jobTitle == null
                ? storeEmployeeRepository.findByStoreIdAndTerminatedAtIsNullOrderByCreatedAtDesc(storeId)
                : storeEmployeeRepository.findByStoreIdAndTerminatedAtIsNullAndJobTitleOrderByCreatedAtDesc(
                        storeId, jobTitle);

        return roster.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(UUID storeId, UUID employeePublicId) {

        requireStore(storeId);

        StoreEmployee employment = storeEmployeeRepository
                .findByStoreIdAndPublicIdAndTerminatedAtIsNull(storeId, employeePublicId)
                .orElseThrow(EmployeeNotFoundException::new);

        return toResponse(employment);
    }

    @Override
    public Optional<ActiveEmploymentView> activeEraOfUser(UUID userPublicId) {

        return storeEmployeeRepository
                .findByUserPublicIdAndTerminatedAtIsNull(userPublicId)
                .map(era -> new ActiveEmploymentView(era.getStoreId(), era.getJobTitle()));
    }

    @Override
    @Transactional
    public void terminateEmployee(UUID storeId, UUID employeePublicId) {

        requireStore(storeId);

        StoreEmployee employment = storeEmployeeRepository
                .findByStoreIdAndPublicIdAndTerminatedAtIsNull(storeId, employeePublicId)
                .orElseThrow(EmployeeNotFoundException::new);

        employment.terminate();

        storeEmployeeRepository.save(employment);
    }

    private StoreApiResponse requireStore(UUID storeId) {

        StoreApiResponse store = storeService.getStoreByPublicId(storeId);

        if (store.status() != StoreStatus.ACTIVE) {
            throw new StoreNotFoundException();
        }

        UserViewResponse currentUser = userAccountService.currentUser();

        if (currentUser.role() != UserRole.ROLE_ADMIN
                && !store.storeAdminPublicId().equals(currentUser.publicId())) {
            throw new AccessDeniedException("Only the store admin or the chain admin may manage this staff");
        }

        return store;
    }

    private EmployeeResponse toResponse(StoreEmployee employment) {
        return toResponse(employment, userAccountService.findByPublicId(employment.getUserPublicId()));
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
