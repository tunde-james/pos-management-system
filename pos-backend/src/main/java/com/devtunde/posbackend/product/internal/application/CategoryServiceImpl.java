package com.devtunde.posbackend.product.internal.application;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.auth.api.UserAccountService;
import com.devtunde.posbackend.auth.api.UserRole;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.product.api.CategoryService;
import com.devtunde.posbackend.product.api.dto.CategoryRequest;
import com.devtunde.posbackend.product.api.dto.CategoryResponse;
import com.devtunde.posbackend.product.api.exception.CategoryAlreadyExistsException;
import com.devtunde.posbackend.product.api.exception.CategoryInUseException;
import com.devtunde.posbackend.product.api.exception.CategoryNotFoundException;
import com.devtunde.posbackend.product.internal.domain.Category;
import com.devtunde.posbackend.product.internal.mapper.CategoryMapper;
import com.devtunde.posbackend.product.internal.persistence.CategoryRepository;
import com.devtunde.posbackend.product.internal.persistence.ProductRepository;
import com.devtunde.posbackend.store.api.StoreService;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final UserAccountService userAccountService;
    private final StoreService storeService;

    public CategoryServiceImpl(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            CategoryMapper categoryMapper,
            UserAccountService userAccountService,
            StoreService storeService) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.categoryMapper = categoryMapper;
        this.userAccountService = userAccountService;
        this.storeService = storeService;
    }

    @Override
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new CategoryAlreadyExistsException();
        }

        Category category = Category.create(request.name());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        UserViewResponse currentUser = userAccountService.currentUser();

        boolean chainAdmin = currentUser.role() == UserRole.ROLE_ADMIN;
        boolean anyStoreAdmin = storeService.isStoreAdmin(currentUser.publicId());

        if (!chainAdmin && !anyStoreAdmin) {
            throw new AccessDeniedException("Only chain admins and store admins may list categories");
        }

        return categoryRepository.findAll().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID publicId, CategoryRequest request) {
        Category category = categoryRepository.findByPublicId(publicId).orElseThrow(CategoryNotFoundException::new);

        if (categoryRepository.existsByNameAndIdNot(request.name(), category.getId())) {
            throw new CategoryAlreadyExistsException();
        }

        category.setName(request.name());

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    public void deleteCategory(UUID publicId) {
        Category category = categoryRepository.findByPublicId(publicId).orElseThrow(CategoryNotFoundException::new);

        if (productRepository.existsByCategoryId(category.getId())) {
            throw new CategoryInUseException();
        }

        categoryRepository.delete(category);
    }
}
