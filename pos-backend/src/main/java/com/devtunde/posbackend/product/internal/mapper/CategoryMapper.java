package com.devtunde.posbackend.product.internal.mapper;

import org.mapstruct.Mapper;

import com.devtunde.posbackend.product.api.dto.CategoryResponse;
import com.devtunde.posbackend.product.internal.domain.Category;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);
}
