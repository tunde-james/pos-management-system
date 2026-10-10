package com.devtunde.posbackend.product.internal.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.devtunde.posbackend.product.api.dto.CategoryRef;
import com.devtunde.posbackend.product.api.dto.ListingPriceUpdateRequest;
import com.devtunde.posbackend.product.api.dto.ProductMasterResponse;
import com.devtunde.posbackend.product.api.dto.ProductMasterUpdateRequest;
import com.devtunde.posbackend.product.api.dto.ProductResponse;
import com.devtunde.posbackend.product.internal.domain.Category;
import com.devtunde.posbackend.product.internal.domain.Product;
import com.devtunde.posbackend.product.internal.domain.StoreProduct;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProductMapper {

    CategoryRef toCategoryRef(Category category);

    ProductMasterResponse toMasterResponse(Product product);

    @Mapping(target = "publicId", source = "product.publicId")
    @Mapping(target = "name", source = "product.name")
    @Mapping(target = "sku", source = "product.sku")
    @Mapping(target = "description", source = "product.description")
    @Mapping(target = "brand", source = "product.brand")
    @Mapping(target = "image", source = "product.image")
    @Mapping(target = "category", source = "product.category")
    ProductResponse toResponse(StoreProduct listing);

    List<ProductResponse> toResponses(List<StoreProduct> listings);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "storeId", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateListing(ListingPriceUpdateRequest request, @MappingTarget StoreProduct listing);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "sku", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateMaster(ProductMasterUpdateRequest request, @MappingTarget Product product);
}
