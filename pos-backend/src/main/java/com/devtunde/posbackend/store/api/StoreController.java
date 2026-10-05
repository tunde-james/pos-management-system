package com.devtunde.posbackend.store.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.posbackend.store.api.dto.StoreApiResponse;
import com.devtunde.posbackend.store.api.dto.StoreRequest;
import com.devtunde.posbackend.store.api.dto.StoreUpdateRequest;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreApiResponse> createStore(@Valid @RequestBody StoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storeService.createStore(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StoreApiResponse>> getAllStores() {
        return ResponseEntity.ok(storeService.getAllStores());
    }

    @GetMapping("/my-store")
    public ResponseEntity<StoreApiResponse> getMyStore() {
        return ResponseEntity.ok(storeService.getMyStore());
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreApiResponse> getStoreByPublicId(@PathVariable("publicId") UUID publicId) {
        return ResponseEntity.ok(storeService.getStoreByPublicId(publicId));
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreApiResponse> updateStore(
            @PathVariable("publicId") UUID publicId, @Valid @RequestBody StoreUpdateRequest request) {
        return ResponseEntity.ok(storeService.updateStore(publicId, request));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStore(@PathVariable("publicId") UUID publicId) {
        storeService.deleteStore(publicId);

        return ResponseEntity.noContent().build();
    }
}
