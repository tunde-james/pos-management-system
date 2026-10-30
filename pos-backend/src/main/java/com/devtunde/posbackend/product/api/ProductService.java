package com.devtunde.posbackend.product.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.devtunde.posbackend.product.api.dto.ListingPriceUpdateRequest;
import com.devtunde.posbackend.product.api.dto.ProductMasterResponse;
import com.devtunde.posbackend.product.api.dto.ProductMasterUpdateRequest;
import com.devtunde.posbackend.product.api.dto.ProductRequest;
import com.devtunde.posbackend.product.api.dto.ProductResponse;
import com.devtunde.posbackend.product.api.dto.StoreProductSaleView;

public interface ProductService {

    ProductResponse createListing(UUID storeId, ProductRequest request);

    List<ProductResponse> listCatalog(UUID storeId);

    List<ProductResponse> searchCatalog(UUID storeId, String keyword);

    ProductResponse updateListingPrices(UUID storeId, UUID productPublicId, ListingPriceUpdateRequest request);

    void delistProduct(UUID storeId, UUID productPublicId);

    ProductMasterResponse updateMaster(UUID productPublicId, ProductMasterUpdateRequest request);

    void requireListing(UUID storeId, UUID productPublicId);

    boolean isListed(UUID storeId, UUID productPublicId);

    String productNameOf(UUID productPublicId);

    Optional<StoreProductSaleView> findSaleView(UUID storeId, UUID productPublicId);
}
