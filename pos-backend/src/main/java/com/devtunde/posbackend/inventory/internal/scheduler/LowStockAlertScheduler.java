package com.devtunde.posbackend.inventory.internal.scheduler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.devtunde.posbackend.auth.api.UserAccountService;
import com.devtunde.posbackend.common.api.EmailService;
import com.devtunde.posbackend.common.api.email.LowStockAlert;
import com.devtunde.posbackend.inventory.internal.domain.Inventory;
import com.devtunde.posbackend.inventory.internal.persistence.InventoryRepository;
import com.devtunde.posbackend.product.api.ProductService;
import com.devtunde.posbackend.store.api.StoreService;
import com.devtunde.posbackend.store.api.StoreStatus;
import com.devtunde.posbackend.store.api.dto.StoreApiResponse;

@Component
public class LowStockAlertScheduler {

    private final StoreService storeService;
    private final UserAccountService userAccountService;
    private final ProductService productService;
    private final InventoryRepository inventoryRepository;
    private final EmailService emailService;

    public LowStockAlertScheduler(
            StoreService storeService,
            UserAccountService userAccountService,
            ProductService productService,
            InventoryRepository inventoryRepository,
            EmailService emailService) {
        this.storeService = storeService;
        this.userAccountService = userAccountService;
        this.productService = productService;
        this.inventoryRepository = inventoryRepository;
        this.emailService = emailService;
    }

    public void scanAndAlert() {
        List<StoreApiResponse> activeStores = storeService.getAllStores().stream()
                .filter(store -> store.status() == StoreStatus.ACTIVE)
                .toList();

        for (StoreApiResponse store : activeStores) {
            alertStore(store);
        }
    }

    private void alertStore(StoreApiResponse store) {
        List<Inventory> low = inventoryRepository.findLowStockByStoreId(store.publicId());
        List<Inventory> out = inventoryRepository.findOutOfStockByStoreId(store.publicId());

        if (low.isEmpty() && out.isEmpty()) {
            return;
        }

        String recipient =
                userAccountService.findByPublicId(store.storeAdminPublicId()).email();
        emailService.sendLowStockAlert(recipient, store.brand(), new LowStockAlert(toItems(low), toItems(out)));
    }

    private List<LowStockAlert.Item> toItems(List<Inventory> rows) {
        return rows.stream()
                .map(row -> new LowStockAlert.Item(
                        productService.productNameOf(row.getProductPublicId()),
                        row.getQuantity(),
                        row.getLowStockThreshold()))
                .toList();
    }
}
