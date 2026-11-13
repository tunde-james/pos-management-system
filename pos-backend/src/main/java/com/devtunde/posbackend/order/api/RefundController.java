package com.devtunde.posbackend.order.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.posbackend.order.api.dto.RefundRequest;
import com.devtunde.posbackend.order.api.dto.RefundResponse;

@RestController
@RequestMapping("/api/v1/orders")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/{publicId}/refunds")
    public ResponseEntity<RefundResponse> refund(
            @PathVariable UUID publicId, @Valid @RequestBody RefundRequest request) {

        RefundResponse response = refundService.refund(publicId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
