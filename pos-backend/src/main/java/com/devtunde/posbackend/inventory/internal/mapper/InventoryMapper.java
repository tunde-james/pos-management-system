package com.devtunde.posbackend.inventory.internal.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.devtunde.posbackend.inventory.api.dto.InventoryResponse;
import com.devtunde.posbackend.inventory.api.dto.MovementResponse;
import com.devtunde.posbackend.inventory.internal.domain.Inventory;
import com.devtunde.posbackend.inventory.internal.domain.StockMovement;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "storePublicId", source = "storeId")
    InventoryResponse toResponse(Inventory inventory);

    MovementResponse toMovementResponse(StockMovement movement);

    List<MovementResponse> toMovementResponses(List<StockMovement> movements);
}
