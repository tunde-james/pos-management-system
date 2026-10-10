package com.devtunde.posbackend.product.api;

import java.util.List;
import java.util.UUID;

import com.devtunde.posbackend.product.api.dto.ListingPriceUpdateRequest;
import com.devtunde.posbackend.product.api.dto.ProductMasterResponse;
import com.devtunde.posbackend.product.api.dto.ProductMasterUpdateRequest;
import com.devtunde.posbackend.product.api.dto.ProductRequest;
import com.devtunde.posbackend.product.api.dto.ProductResponse;

public interface ProductService {

    ProductResponse createListing(UUID storeId, ProductRequest request);

    List<ProductResponse> listCatalog(UUID storeId);

    List<ProductResponse> searchCatalog(UUID storeId, String keyword);

    ProductResponse updateListingPrices(UUID storeId, UUID productPublicId, ListingPriceUpdateRequest request);

    void delistProduct(UUID storeId, UUID productPublicId);

    ProductMasterResponse updateMaster(UUID productPublicId, ProductMasterUpdateRequest request);
}
