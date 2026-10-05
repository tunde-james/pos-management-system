package com.devtunde.posbackend.store.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.posbackend.store.internal.domain.Store;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByPublicId(UUID publicId);

    Optional<Store> findByStoreAdminId(UUID storeAdminId);

    Optional<Store> findByBrand(String brand);

    Optional<Store> findByContact_Email(String email);
}
