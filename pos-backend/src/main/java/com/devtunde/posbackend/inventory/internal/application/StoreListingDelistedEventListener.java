package com.devtunde.posbackend.inventory.internal.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.devtunde.posbackend.inventory.internal.persistence.InventoryRepository;
import com.devtunde.posbackend.product.api.ProductService;
import com.devtunde.posbackend.product.api.event.StoreListingDelistedEvent;

@Component
public class StoreListingDelistedEventListener {

    private final InventoryRepository inventoryRepository;
    private final ProductService productService;

    public StoreListingDelistedEventListener(InventoryRepository inventoryRepository, ProductService productService) {
        this.inventoryRepository = inventoryRepository;
        this.productService = productService;
    }

    @ApplicationModuleListener
    void onListingDelisted(StoreListingDelistedEvent event) {

        if (productService.isListed(event.storePublicId(), event.productPublicId())) {
            return;
        }

        inventoryRepository
                .findByStoreIdAndProductPublicId(event.storePublicId(), event.productPublicId())
                .ifPresent(inventory -> {
                    inventory.archive();
                    inventoryRepository.save(inventory);
                });
    }
}
