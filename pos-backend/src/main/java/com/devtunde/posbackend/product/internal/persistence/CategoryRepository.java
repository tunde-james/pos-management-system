package com.devtunde.posbackend.product.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.posbackend.product.internal.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByPublicId(UUID publicId);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
