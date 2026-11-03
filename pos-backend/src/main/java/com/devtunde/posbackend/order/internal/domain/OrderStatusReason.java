package com.devtunde.posbackend.order.internal.domain;

public enum OrderStatusReason {
    TTL_TIMEOUT,
    STOCK_GONE_AT_SETTLEMENT,
    GATEWAY_DECLINED
}
