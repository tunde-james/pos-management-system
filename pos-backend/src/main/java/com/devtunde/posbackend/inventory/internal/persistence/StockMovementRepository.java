package com.devtunde.posbackend.inventory.internal.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.posbackend.inventory.internal.domain.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByStoreIdAndProductPublicIdOrderByCreatedAtAscIdAsc(UUID storeId, UUID productPublicId);
}
