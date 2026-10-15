package com.devtunde.posbackend.inventory.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devtunde.posbackend.inventory.internal.domain.Inventory;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByStoreIdAndProductPublicIdAndArchivedAtIsNull(UUID storeId, UUID productPublicId);

    Optional<Inventory> findByStoreIdAndProductPublicId(UUID storeId, UUID productPublicId);

    List<Inventory> findByStoreIdAndArchivedAtIsNull(UUID storeId);

    @Query("""
              select i from Inventory i
              where i.storeId = :storeId
                and i.archivedAt is null
                and i.quantity > 0
                and i.quantity <= i.lowStockThreshold
              order by i.quantity asc
              """)
    List<Inventory> findLowStockByStoreId(@Param("storeId") UUID storeId);

    @Query("""
              select i from Inventory i
              where i.storeId = :storeId
                and i.archivedAt is null
                and i.quantity = 0
              order by i.productPublicId asc
              """)
    List<Inventory> findOutOfStockByStoreId(@Param("storeId") UUID storeId);
}
