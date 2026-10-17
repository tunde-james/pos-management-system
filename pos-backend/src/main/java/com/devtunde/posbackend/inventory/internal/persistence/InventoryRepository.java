package com.devtunde.posbackend.inventory.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

 import jakarta.persistence.LockModeType;

   import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devtunde.posbackend.inventory.internal.domain.Inventory;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByStoreIdAndProductPublicIdAndArchivedAtIsNull(UUID storeId, UUID productPublicId);

    Optional<Inventory> findByStoreIdAndProductPublicId(UUID storeId, UUID productPublicId);

    List<Inventory> findByStoreIdAndArchivedAtIsNullOrderByCreatedAtDesc(UUID storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
      select i from Inventory i
      where i.storeId = :storeId
      and i.productPublicId = :productPublicId
      and i.archivedAt is null
      """)
    Optional<Inventory> lockActive(@Param("storeId") UUID storeId, @Param("productPublicId") UUID productPublicId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
      select i from Inventory i
      where i.storeId = :storeId
      and i.productPublicId = :productPublicId
    """)
    Optional<Inventory> lockPair(@Param("storeId") UUID storeId, @Param("productPublicId") UUID productPublicId);
  


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
