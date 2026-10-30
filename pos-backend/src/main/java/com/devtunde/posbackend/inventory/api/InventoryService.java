package com.devtunde.posbackend.inventory.api;

import java.util.List;
import java.util.UUID;

import com.devtunde.posbackend.inventory.api.dto.DamageRequest;
import com.devtunde.posbackend.inventory.api.dto.InventoryCreateRequest;
import com.devtunde.posbackend.inventory.api.dto.InventoryResponse;
import com.devtunde.posbackend.inventory.api.dto.LowStockResponse;
import com.devtunde.posbackend.inventory.api.dto.MovementResponse;
import com.devtunde.posbackend.inventory.api.dto.ReceivedRequest;
import com.devtunde.posbackend.inventory.api.dto.SaleLine;
import com.devtunde.posbackend.inventory.api.dto.SaleOutcome;
import com.devtunde.posbackend.inventory.api.dto.StocktakeRequest;
import com.devtunde.posbackend.inventory.api.dto.ThresholdUpdateRequest;

public interface InventoryService {

    InventoryResponse createInventory(UUID storeId, InventoryCreateRequest request);

    InventoryResponse getInventory(UUID storeId, UUID productPublicId);

    InventoryResponse updateQuantity(UUID storeId, UUID productPublicId, StocktakeRequest request);

    InventoryResponse updateThreshold(UUID storeId, UUID productPublicId, ThresholdUpdateRequest request);

    InventoryResponse receiveStock(UUID storeId, UUID productPublicId, ReceivedRequest request);

    InventoryResponse reportDamage(UUID storeId, UUID productPublicId, DamageRequest request);

    List<InventoryResponse> listInventory(UUID storeId);

    LowStockResponse getLowStock(UUID storeId);

    List<MovementResponse> getMovements(UUID storeId, UUID productPublicId);

    SaleOutcome sellStock(UUID storeId, List<SaleLine> lines);
}
