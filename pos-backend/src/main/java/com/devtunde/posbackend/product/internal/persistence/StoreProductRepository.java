package com.devtunde.posbackend.product.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devtunde.posbackend.product.internal.domain.StoreProduct;

public interface StoreProductRepository extends JpaRepository<StoreProduct, Long> {

    @Query("""
            select sp from StoreProduct sp
            join fetch sp.product p
            join fetch p.category
            where sp.storeId = :storeId
        """)
    List<StoreProduct> findCatalogByStoreId(@Param("storeId") UUID storeId);

    @Query("""
            select sp from StoreProduct sp
            join fetch sp.product p
            join fetch p.category
            where sp.storeId = :storeId
            and (lower(p.name) like lower(concat('%', :keyword, '%'))
            or lower(p.sku) like lower(concat('%', :keyword, '%')))
        """)
    List<StoreProduct> searchCatalogByStoreId(@Param("storeId") UUID storeId, @Param("keyword") String keyword);

    @Query("""
            select sp from StoreProduct sp
            join fetch sp.product p
            join fetch p.category
            where sp.storeId = :storeId
            and p.publicId = :productPublicId
        """)
    Optional<StoreProduct> findByStoreIdAndProductPublicId(
            @Param("storeId") UUID storeId, @Param("productPublicId") UUID productPublicId);
}
