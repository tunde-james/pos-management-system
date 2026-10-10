package com.devtunde.posbackend.store.api;

import java.util.List;
import java.util.UUID;

import com.devtunde.posbackend.store.api.dto.StoreApiResponse;
import com.devtunde.posbackend.store.api.dto.StoreRequest;
import com.devtunde.posbackend.store.api.dto.StoreUpdateRequest;

public interface StoreService {

    StoreApiResponse createStore(StoreRequest request);

    StoreApiResponse getStoreByPublicId(UUID publicId);

    List<StoreApiResponse> getAllStores();

    StoreApiResponse getMyStore();

    StoreApiResponse updateStore(UUID publicId, StoreUpdateRequest request);

    boolean isStoreAdmin(UUID userPublicId);

    void deleteStore(UUID publicId);
}
