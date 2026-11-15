package com.devtunde.posbackend.order.api;

import java.util.UUID;

import com.devtunde.posbackend.order.api.dto.RefundRequest;
import com.devtunde.posbackend.order.api.dto.RefundResponse;

public interface RefundService {

    RefundResponse refund(UUID orderPublicId, RefundRequest request);
}
