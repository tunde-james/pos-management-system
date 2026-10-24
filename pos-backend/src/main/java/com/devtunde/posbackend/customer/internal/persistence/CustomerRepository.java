package com.devtunde.posbackend.customer.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.posbackend.customer.internal.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPublicId(UUID publicId);

    Optional<Customer> findByPhone(String phone);

    boolean existsByLoyaltyTag(String loyaltyTag);

    List<Customer> findAllByOrderByCreatedAtDescIdDesc();
}
