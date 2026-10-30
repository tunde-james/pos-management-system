package com.devtunde.posbackend.product.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StoreProductSaleView(UUID productPublicId, String name, BigDecimal sellingPrice) {}
