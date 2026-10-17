package com.devtunde.posbackend.product.api.event;

import java.util.UUID;

public record StoreListingDelistedEvent(UUID storePublicId, UUID productPublicId) {}
