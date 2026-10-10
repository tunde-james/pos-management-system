package com.devtunde.posbackend.product.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.posbackend.product.api.dto.ListingPriceUpdateRequest;
import com.devtunde.posbackend.product.api.dto.ProductMasterResponse;
import com.devtunde.posbackend.product.api.dto.ProductMasterUpdateRequest;
import com.devtunde.posbackend.product.api.dto.ProductRequest;
import com.devtunde.posbackend.product.api.dto.ProductResponse;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/store/{storeId}")
    public ResponseEntity<ProductResponse> createListing(
            @PathVariable("storeId") UUID storeId, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createListing(storeId, request));
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<ProductResponse>> listCatalog(@PathVariable("storeId") UUID storeId) {
        return ResponseEntity.ok(productService.listCatalog(storeId));
    }

    @GetMapping("/store/{storeId}/search")
    public ResponseEntity<List<ProductResponse>> searchCatalog(
            @PathVariable("storeId") UUID storeId, @RequestParam("keyword") String keyword) {
        return ResponseEntity.ok(productService.searchCatalog(storeId, keyword));
    }

    @PatchMapping("/store/{storeId}/{productPublicId}")
    public ResponseEntity<ProductResponse> updateListingPrices(
            @PathVariable("storeId") UUID storeId,
            @PathVariable("productPublicId") UUID productPublicId,
            @Valid @RequestBody ListingPriceUpdateRequest request) {
        return ResponseEntity.ok(productService.updateListingPrices(storeId, productPublicId, request));
    }

    @DeleteMapping("/store/{storeId}/{productPublicId}")
    public ResponseEntity<Void> delistProduct(
            @PathVariable("storeId") UUID storeId, @PathVariable("productPublicId") UUID productPublicId) {
        productService.delistProduct(storeId, productPublicId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{productPublicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductMasterResponse> updateMaster(
            @PathVariable("productPublicId") UUID productPublicId,
            @Valid @RequestBody ProductMasterUpdateRequest request) {
        return ResponseEntity.ok(productService.updateMaster(productPublicId, request));
    }
}
