package com.devtunde.posbackend.store.internal.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.devtunde.posbackend.store.api.dto.StoreApiResponse;
import com.devtunde.posbackend.store.api.dto.StoreUpdateRequest;
import com.devtunde.posbackend.store.internal.domain.Store;
import com.devtunde.posbackend.store.internal.domain.StoreContact;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface StoreMapper {

    @Mapping(target = "storeAdminPublicId", source = "storeAdminId")
    StoreApiResponse toResponse(Store store);

    StoreApiResponse.Contact toResponseContact(StoreContact contact);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "storeAdminId", source = "storeAdminPublicId")
    void update(StoreUpdateRequest request, @MappingTarget Store store);

    StoreContact toStoreContact(StoreUpdateRequest.Contact contact);
}
