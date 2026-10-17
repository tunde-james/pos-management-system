package com.devtunde.posbackend.common.api;

import com.devtunde.posbackend.common.api.email.LowStockAlert;

public interface EmailService {

    void sendLowStockAlert(String recipientEmail, String storeBrand, LowStockAlert alert);
}
