package com.devtunde.posbackend.inventory.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.posbackend.inventory.api.dto.DamageRequest;
import com.devtunde.posbackend.inventory.api.dto.InventoryCreateRequest;
import com.devtunde.posbackend.inventory.api.dto.InventoryResponse;
import com.devtunde.posbackend.inventory.api.dto.LowStockResponse;
import com.devtunde.posbackend.inventory.api.dto.MovementResponse;
import com.devtunde.posbackend.inventory.api.dto.ReceivedRequest;
import com.devtunde.posbackend.inventory.api.dto.StocktakeRequest;
import com.devtunde.posbackend.inventory.api.dto.ThresholdUpdateRequest;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    public ResponseEntity<InventoryResponse> createInventory(
            @PathVariable("storeId") UUID storeId, @Valid @RequestBody InventoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createInventory(storeId, request));
    }

    @GetMapping
    public List<InventoryResponse> listInventory(@PathVariable("storeId") UUID storeId) {
        return inventoryService.listInventory(storeId);
    }

    @GetMapping("/low-stock")
    public LowStockResponse getLowStock(@PathVariable("storeId") UUID storeId) {
        return inventoryService.getLowStock(storeId);
    }

    @GetMapping("/products/{productPublicId}")
    public InventoryResponse getInventory(
            @PathVariable("storeId") UUID storeId, @PathVariable("productPublicId") UUID productPublicId) {
        return inventoryService.getInventory(storeId, productPublicId);
    }

    @PutMapping("/products/{productPublicId}")
    public InventoryResponse updateQuantity(
            @PathVariable("storeId") UUID storeId,
            @PathVariable("productPublicId") UUID productPublicId,
            @Valid @RequestBody StocktakeRequest request) {
        return inventoryService.updateQuantity(storeId, productPublicId, request);
    }

    @PutMapping("/products/{productPublicId}/threshold")
    public InventoryResponse updateThreshold(
            @PathVariable("storeId") UUID storeId,
            @PathVariable("productPublicId") UUID productPublicId,
            @Valid @RequestBody ThresholdUpdateRequest request) {
        return inventoryService.updateThreshold(storeId, productPublicId, request);
    }

    @PostMapping("/products/{productPublicId}/received")
    public InventoryResponse receiveStock(
            @PathVariable("storeId") UUID storeId,
            @PathVariable("productPublicId") UUID productPublicId,
            @Valid @RequestBody ReceivedRequest request) {
        return inventoryService.receiveStock(storeId, productPublicId, request);
    }

    @PostMapping("/products/{productPublicId}/damage")
    public InventoryResponse reportDamage(
            @PathVariable("storeId") UUID storeId,
            @PathVariable("productPublicId") UUID productPublicId,
            @Valid @RequestBody DamageRequest request) {
        return inventoryService.reportDamage(storeId, productPublicId, request);
    }

    @GetMapping("/products/{productPublicId}/movements")
    public List<MovementResponse> getMovements(
            @PathVariable("storeId") UUID storeId, @PathVariable("productPublicId") UUID productPublicId) {
        return inventoryService.getMovements(storeId, productPublicId);
    }
}
