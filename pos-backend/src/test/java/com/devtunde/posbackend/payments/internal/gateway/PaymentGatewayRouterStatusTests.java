package com.devtunde.posbackend.payments.internal.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devtunde.posbackend.payments.api.GatewayVerifyStatus;

class PaymentGatewayRouterStatusTests {

    @Test
    @DisplayName("Monnify reports a completed transfer as PAID — that must settle")
    void monnifyPaidMapsToSuccess() {

        assertThat(PaymentGatewayRouter.monnifyStatusOf("PAID")).isEqualTo(GatewayVerifyStatus.SUCCESS);

        assertThat(PaymentGatewayRouter.monnifyStatusOf("paid")).isEqualTo(GatewayVerifyStatus.SUCCESS);

        assertThat(PaymentGatewayRouter.monnifyStatusOf("success")).isEqualTo(GatewayVerifyStatus.SUCCESS);
    }

    @Test
    @DisplayName("Failures map to FAILED; anything else stays PENDING")
    void monnifyFailuresAndUnknownsStaySafe() {

        assertThat(PaymentGatewayRouter.monnifyStatusOf("FAILED")).isEqualTo(GatewayVerifyStatus.FAILED);

        assertThat(PaymentGatewayRouter.monnifyStatusOf("pending")).isEqualTo(GatewayVerifyStatus.PENDING);

        assertThat(PaymentGatewayRouter.monnifyStatusOf(null)).isEqualTo(GatewayVerifyStatus.PENDING);
    }
}
