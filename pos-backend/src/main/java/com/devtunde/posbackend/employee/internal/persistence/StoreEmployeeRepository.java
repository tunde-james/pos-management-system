package com.devtunde.posbackend.employee.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.posbackend.employee.api.EmployeeJobTitle;
import com.devtunde.posbackend.employee.internal.domain.StoreEmployee;

public interface StoreEmployeeRepository extends JpaRepository<StoreEmployee, Long> {

    List<StoreEmployee> findByStoreIdAndTerminatedAtIsNullOrderByCreatedAtDesc(UUID storeId);

    List<StoreEmployee> findByStoreIdAndTerminatedAtIsNullAndJobTitleOrderByCreatedAtDesc(
            UUID storeId, EmployeeJobTitle jobTitle);

    Optional<StoreEmployee> findByStoreIdAndPublicIdAndTerminatedAtIsNull(UUID storeId, UUID publicId);

    boolean existsByUserPublicIdAndTerminatedAtIsNull(UUID userPublicId);
}
