package com.devtunde.posbackend.inventory.internal.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.auth.api.UserAccountService;
import com.devtunde.posbackend.auth.api.UserRole;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.inventory.api.InventoryService;
import com.devtunde.posbackend.inventory.api.MovementType;
import com.devtunde.posbackend.inventory.api.dto.DamageRequest;
import com.devtunde.posbackend.inventory.api.dto.InventoryCreateRequest;
import com.devtunde.posbackend.inventory.api.dto.InventoryResponse;
import com.devtunde.posbackend.inventory.api.dto.LowStockItemResponse;
import com.devtunde.posbackend.inventory.api.dto.LowStockResponse;
import com.devtunde.posbackend.inventory.api.dto.MovementResponse;
import com.devtunde.posbackend.inventory.api.dto.ReceivedRequest;
import com.devtunde.posbackend.inventory.api.dto.SaleLine;
import com.devtunde.posbackend.inventory.api.dto.SaleOutcome;
import com.devtunde.posbackend.inventory.api.dto.StockShortfall;
import com.devtunde.posbackend.inventory.api.dto.StocktakeRequest;
import com.devtunde.posbackend.inventory.api.dto.ThresholdUpdateRequest;
import com.devtunde.posbackend.inventory.api.exception.InvalidStockAdjustmentException;
import com.devtunde.posbackend.inventory.api.exception.InventoryAlreadyExistsException;
import com.devtunde.posbackend.inventory.api.exception.InventoryNotFoundException;
import com.devtunde.posbackend.inventory.internal.domain.Inventory;
import com.devtunde.posbackend.inventory.internal.domain.StockMovement;
import com.devtunde.posbackend.inventory.internal.mapper.InventoryMapper;
import com.devtunde.posbackend.inventory.internal.persistence.InventoryRepository;
import com.devtunde.posbackend.inventory.internal.persistence.StockMovementRepository;
import com.devtunde.posbackend.product.api.ProductService;
import com.devtunde.posbackend.store.api.StoreService;
import com.devtunde.posbackend.store.api.StoreStatus;
import com.devtunde.posbackend.store.api.dto.StoreApiResponse;
import com.devtunde.posbackend.store.api.exception.StoreNotFoundException;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StoreService storeService;
    private final ProductService productService;
    private final UserAccountService userAccountService;
    private final InventoryMapper inventoryMapper;

    public InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            StockMovementRepository stockMovementRepository,
            StoreService storeService,
            ProductService productService,
            UserAccountService userAccountService,
            InventoryMapper inventoryMapper) {

        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.storeService = storeService;
        this.productService = productService;
        this.userAccountService = userAccountService;
        this.inventoryMapper = inventoryMapper;
    }

    @Override
    @Transactional
    public InventoryResponse createInventory(UUID storeId, InventoryCreateRequest request) {

        requireStore(storeId);
        productService.requireListing(storeId, request.productPublicId());

        Optional<Inventory> existing =
                inventoryRepository.findByStoreIdAndProductPublicId(storeId, request.productPublicId());

        if (existing.isPresent()) {
            existing = inventoryRepository.lockPair(storeId, request.productPublicId());
        }

        if (existing.isPresent()) {
            Inventory inventory = existing.get();

            if (inventory.getArchivedAt() == null) {
                throw new InventoryAlreadyExistsException();
            }

            int before = inventory.getQuantity();
            inventory.unarchive();

            inventory.setQuantity(request.quantity());

            inventory.setLowStockThreshold(request.lowStockThreshold());
            inventoryRepository.save(inventory);
            recordMovement(inventory, MovementType.ADJUSTMENT, request.quantity() - before, before, null);

            return inventoryMapper.toResponse(inventory);
        }

        Inventory inventory =
                Inventory.create(storeId, request.productPublicId(), request.quantity(), request.lowStockThreshold());
        try {
            inventoryRepository.saveAndFlush(inventory);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("uk_inventories_store_product")) {
                throw new InventoryAlreadyExistsException();
            }
            throw ex;
        }
        recordMovement(inventory, MovementType.OPENING, request.quantity(), 0, null);

        return inventoryMapper.toResponse(inventory);
    }

    @Override
    @Transactional
    public InventoryResponse updateQuantity(UUID storeId, UUID productPublicId, StocktakeRequest request) {

        requireStore(storeId);
        productService.requireListing(storeId, productPublicId);

        Inventory inventory = requireInventoryForUpdate(storeId, productPublicId);

        int before = inventory.getQuantity();
        inventory.setQuantity(request.quantity());
        inventoryRepository.save(inventory);
        recordMovement(inventory, MovementType.ADJUSTMENT, request.quantity() - before, before, null);

        return inventoryMapper.toResponse(inventory);
    }

    @Override
    @Transactional
    public InventoryResponse updateThreshold(UUID storeId, UUID productPublicId, ThresholdUpdateRequest request) {

        requireStore(storeId);
        productService.requireListing(storeId, productPublicId);

        Inventory inventory = requireInventoryForUpdate(storeId, productPublicId);

        inventory.setLowStockThreshold(request.lowStockThreshold());
        inventoryRepository.save(inventory);

        return inventoryMapper.toResponse(inventory);
    }

    @Override
    @Transactional
    public InventoryResponse receiveStock(UUID storeId, UUID productPublicId, ReceivedRequest request) {

        requireStore(storeId);
        productService.requireListing(storeId, productPublicId);

        Inventory inventory = requireInventoryForUpdate(storeId, productPublicId);

        int before = inventory.getQuantity();
        inventory.setQuantity(before + request.quantity());
        inventoryRepository.save(inventory);
        recordMovement(inventory, MovementType.RECEIVED, request.quantity(), before, null);

        return inventoryMapper.toResponse(inventory);
    }

    @Override
    @Transactional
    public InventoryResponse reportDamage(UUID storeId, UUID productPublicId, DamageRequest request) {

        requireStore(storeId);
        productService.requireListing(storeId, productPublicId);

        Inventory inventory = requireInventoryForUpdate(storeId, productPublicId);

        if (request.quantity() > inventory.getQuantity()) {
            throw new InvalidStockAdjustmentException();
        }

        int before = inventory.getQuantity();
        inventory.setQuantity(before - request.quantity());
        inventoryRepository.save(inventory);
        recordMovement(inventory, MovementType.DAMAGE, -request.quantity(), before, request.reason());

        return inventoryMapper.toResponse(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getInventory(UUID storeId, UUID productPublicId) {

        requireStore(storeId);
        productService.requireListing(storeId, productPublicId);

        return inventoryMapper.toResponse(requireInventory(storeId, productPublicId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryResponse> listInventory(UUID storeId) {

        requireStore(storeId);

        return inventoryRepository.findByStoreIdAndArchivedAtIsNullOrderByCreatedAtDesc(storeId).stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LowStockResponse getLowStock(UUID storeId) {

        requireStore(storeId);

        List<LowStockItemResponse> low = inventoryRepository.findLowStockByStoreId(storeId).stream()
                .map(this::toLowStockItem)
                .toList();

        List<LowStockItemResponse> out = inventoryRepository.findOutOfStockByStoreId(storeId).stream()
                .map(this::toLowStockItem)
                .toList();

        return new LowStockResponse(low, out);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovementResponse> getMovements(UUID storeId, UUID productPublicId) {

        requireStore(storeId);

        inventoryRepository
                .findByStoreIdAndProductPublicId(storeId, productPublicId)
                .orElseThrow(InventoryNotFoundException::new);

        return inventoryMapper.toMovementResponses(
                stockMovementRepository.findByStoreIdAndProductPublicIdOrderByCreatedAtAscIdAsc(
                        storeId, productPublicId));
    }

    @Override
    public SaleOutcome sellStock(UUID storeId, List<SaleLine> lines) {

        Map<UUID, Integer> wantedByProduct = new TreeMap<>();
        for (SaleLine line : lines) {

            wantedByProduct.merge(line.productPublicId(), line.quantity(), Integer::sum);
        }

        Map<UUID, Inventory> lockedByProduct = new LinkedHashMap<>();
        List<StockShortfall> shortfalls = new ArrayList<>();

        for (Map.Entry<UUID, Integer> wanted : wantedByProduct.entrySet()) {
            Inventory inventory =
                    inventoryRepository.lockActive(storeId, wanted.getKey()).orElse(null);

            int available = inventory == null ? 0 : inventory.getQuantity();

            if (wanted.getValue() > available) {
                shortfalls.add(new StockShortfall(wanted.getKey(), wanted.getValue(), available));
            }

            if (inventory != null) {

                lockedByProduct.put(wanted.getKey(), inventory);
            }
        }

        if (!shortfalls.isEmpty()) {
            return new SaleOutcome(shortfalls);
        }

        for (Map.Entry<UUID, Integer> wanted : wantedByProduct.entrySet()) {
            Inventory inventory = lockedByProduct.get(wanted.getKey());
            int before = inventory.getQuantity();

            inventory.setQuantity(before - wanted.getValue());

            inventoryRepository.save(inventory);

            recordMovement(inventory, MovementType.SALE, -wanted.getValue(), before, "Till sale");
        }

        return new SaleOutcome(shortfalls);
    }

    private Inventory requireInventory(UUID storeId, UUID productPublicId) {

        return inventoryRepository
                .findByStoreIdAndProductPublicIdAndArchivedAtIsNull(storeId, productPublicId)
                .orElseThrow(InventoryNotFoundException::new);
    }

    private Inventory requireInventoryForUpdate(UUID storeId, UUID productPublicId) {

        return inventoryRepository.lockActive(storeId, productPublicId).orElseThrow(InventoryNotFoundException::new);
    }

    private StoreApiResponse requireStore(UUID storeId) {

        StoreApiResponse store = storeService.getStoreByPublicId(storeId);

        if (store.status() != StoreStatus.ACTIVE) {
            throw new StoreNotFoundException(); // unknown and CLOSED alike: never reveal dead stores
        }

        UserViewResponse currentUser = userAccountService.currentUser();

        if (currentUser.role() != UserRole.ROLE_ADMIN
                && !store.storeAdminPublicId().equals(currentUser.publicId())) {
            throw new AccessDeniedException("Only the store admin or the chain admin may manage this stock");
        }

        return store;
    }

    private void recordMovement(Inventory inventory, MovementType type, int delta, int before, String reason) {

        UUID actor = userAccountService.currentUser().publicId();

        stockMovementRepository.save(StockMovement.of(
                inventory.getStoreId(),
                inventory.getProductPublicId(),
                type,
                delta,
                before,
                inventory.getQuantity(),
                reason,
                actor));
    }

    private LowStockItemResponse toLowStockItem(Inventory inventory) {

        return new LowStockItemResponse(
                inventory.getProductPublicId(),
                productService.productNameOf(inventory.getProductPublicId()),
                inventory.getQuantity(),
                inventory.getLowStockThreshold());
    }
}
