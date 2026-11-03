package com.devtunde.posbackend.payments.api;

import com.devtunde.posbackend.payments.api.dto.GatewayInitiateRequest;
import com.devtunde.posbackend.payments.api.dto.GatewayInitiateResult;
import com.devtunde.posbackend.payments.api.dto.GatewayVerifyRequest;
import com.devtunde.posbackend.payments.api.dto.GatewayVerifyResult;

public interface PaymentGateway {

    GatewayInitiateResult initiate(GatewayInitiateRequest request);

    GatewayVerifyResult verify(GatewayVerifyRequest request);
}
