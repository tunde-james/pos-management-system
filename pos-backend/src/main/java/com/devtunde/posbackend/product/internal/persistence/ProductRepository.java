package com.devtunde.posbackend.product.internal.persistence;


   import java.util.Optional;
   import java.util.UUID;

   import
 org.springframework.data.jpa.repository.JpaRepository;

   import
 com.devtunde.posbackend.product.internal.domain.Product;
import java.util.List;


public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByPublicId(UUID publicId);

    Optional<Product> findBySku(String sku);

    boolean existsByCategoryId(Long categoryId);
}
