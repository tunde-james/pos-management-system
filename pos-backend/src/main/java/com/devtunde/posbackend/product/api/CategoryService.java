package com.devtunde.posbackend.product.api;

import java.util.List;
import java.util.UUID;

import com.devtunde.posbackend.product.api.dto.CategoryRequest;
import com.devtunde.posbackend.product.api.dto.CategoryResponse;

public interface CategoryService {

    CategoryResponse createCategory(CategoryRequest request);

    List<CategoryResponse> getAllCategories();

    CategoryResponse updateCategory(UUID publicId, CategoryRequest request);

    void deleteCategory(UUID publicId);
}
