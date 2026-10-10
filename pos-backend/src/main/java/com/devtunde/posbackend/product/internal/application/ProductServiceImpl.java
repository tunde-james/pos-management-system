package com.devtunde.posbackend.product.internal.application;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

 import
 com.devtunde.posbackend.store.api.StoreStatus;
   import
 com.devtunde.posbackend.store.api.exception.StoreNotFoundException;

import com.devtunde.posbackend.auth.api.UserAccountService;
import com.devtunde.posbackend.auth.api.UserRole;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.product.api.ProductService;
import com.devtunde.posbackend.product.api.dto.ListingPriceUpdateRequest;
import com.devtunde.posbackend.product.api.dto.ProductMasterResponse;
import com.devtunde.posbackend.product.api.dto.ProductMasterUpdateRequest;
import com.devtunde.posbackend.product.api.dto.ProductRequest;
import com.devtunde.posbackend.product.api.dto.ProductResponse;
import com.devtunde.posbackend.product.api.exception.EmptyPatchException;
import com.devtunde.posbackend.product.api.exception.ProductAlreadyListedException;
import com.devtunde.posbackend.product.api.exception.ProductNotFoundException;
import com.devtunde.posbackend.product.api.exception.UnknownCategoryException;
import com.devtunde.posbackend.product.internal.domain.Category;
import com.devtunde.posbackend.product.internal.domain.Product;
import com.devtunde.posbackend.product.internal.domain.StoreProduct;
import com.devtunde.posbackend.product.internal.mapper.ProductMapper;
import com.devtunde.posbackend.product.internal.persistence.CategoryRepository;
import com.devtunde.posbackend.product.internal.persistence.ProductRepository;
import com.devtunde.posbackend.product.internal.persistence.StoreProductRepository;
import com.devtunde.posbackend.store.api.StoreService;
import com.devtunde.posbackend.store.api.dto.StoreApiResponse;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StoreProductRepository storeProductRepository;
    private final StoreService storeService;
    private final UserAccountService userAccountService;
    private final ProductMapper productMapper;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            StoreProductRepository storeProductRepository,
            StoreService storeService,
            UserAccountService userAccountService,
            ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.storeProductRepository = storeProductRepository;
        this.storeService = storeService;
        this.userAccountService = userAccountService;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional
    public ProductResponse createListing(UUID storeId, ProductRequest request) {

        requireStore(storeId);

        Product master = resolveMaster(request);

        if (storeProductRepository
                .findByStoreIdAndProductPublicId(storeId, master.getPublicId())
                .isPresent()) {
            throw new ProductAlreadyListedException();
        }

        StoreProduct listing = StoreProduct.create(
                storeId, master, request.mrp(), request.sellingPrice(), request.discountPercentage());

        return productMapper.toResponse(storeProductRepository.save(listing));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> listCatalog(UUID storeId) {

        requireStore(storeId);

        return productMapper.toResponses(storeProductRepository.findCatalogByStoreId(storeId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> searchCatalog(UUID storeId, String keyword) {

        requireStore(storeId);

        return productMapper.toResponses(storeProductRepository.searchCatalogByStoreId(storeId, keyword));
    }

    @Override
    @Transactional
    public ProductResponse updateListingPrices(UUID storeId, UUID productPublicId, ListingPriceUpdateRequest request) {

        requireStore(storeId);

        StoreProduct listing = storeProductRepository
                .findByStoreIdAndProductPublicId(storeId, productPublicId)
                .orElseThrow(ProductNotFoundException::new);

        if (request.isEmpty()) {
            throw new EmptyPatchException();
        }

        productMapper.updateListing(request, listing);

        return productMapper.toResponse(storeProductRepository.save(listing));
    }

    @Override
    @Transactional
    public void delistProduct(UUID storeId, UUID productPublicId) {

        requireStore(storeId);

        StoreProduct listing = storeProductRepository
                .findByStoreIdAndProductPublicId(storeId, productPublicId)
                .orElseThrow(ProductNotFoundException::new);

        storeProductRepository.delete(listing);
    }

    @Override
    @Transactional
    public ProductMasterResponse updateMaster(UUID productPublicId, ProductMasterUpdateRequest request) {

        Product master = productRepository.findByPublicId(productPublicId).orElseThrow(ProductNotFoundException::new);

        if (request.isEmpty()) {
            throw new EmptyPatchException();
        }

        productMapper.updateMaster(request, master);

        return productMapper.toMasterResponse(productRepository.save(master));
    }

    private StoreApiResponse requireStore(UUID storeId) {

        StoreApiResponse store = storeService.getStoreByPublicId(storeId);

        if (store.status() != StoreStatus.ACTIVE) {
            throw new StoreNotFoundException();
        }

        UserViewResponse currentUser = userAccountService.currentUser();

        if (currentUser.role() != UserRole.ROLE_ADMIN
                && !store.storeAdminPublicId().equals(currentUser.publicId())) {
            throw new AccessDeniedException("Only the store admin or the chain admin may manage this catalog");
        }

        return store;
    }

    private Product resolveMaster(ProductRequest request) {
        return productRepository.findBySku(request.sku()).orElseGet(() -> {
            Category category = categoryRepository
                    .findByPublicId(request.categoryPublicId())
                    .orElseThrow(UnknownCategoryException::new);

            return productRepository.save(Product.create(
                    request.name(), request.sku(), request.description(), request.brand(), request.image(), category));
        });
    }
}
