package com.devtunde.posbackend.common.internal;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.posbackend.common.api.EmailService;
import com.devtunde.posbackend.common.api.email.LowStockAlert;

@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private static final String FROM_ADDRESS = "noreply@pos.devtunde.com";

    private final ObjectProvider<JavaMailSender> mailSender;

    public SmtpEmailService(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendLowStockAlert(String recipientEmail, String storeBrand, LowStockAlert alert) {
        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null) {
            log.warn("SMTP not configured — skipping low stock alert to {} for {}", recipientEmail, storeBrand);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(FROM_ADDRESS);
        message.setTo(recipientEmail);
        message.setSubject("Low stock alert — " + storeBrand);
        message.setText(body(alert));

        sender.send(message);
    }

    private String body(LowStockAlert alert) {
        StringBuilder text = new StringBuilder("Low stock report:\n\n");

        if (!alert.out().isEmpty()) {
            text.append("OUT OF STOCK — reorder now:\n");
            alert.out()
                    .forEach(item ->
                            text.append("  - %s (on hand: %d)\n".formatted(item.productName(), item.quantity())));
            text.append("\n");
        }

        if (!alert.low().isEmpty()) {
            text.append("Running low:\n");
            alert.low()
                    .forEach(item -> text.append("  - %s (on hand: %d, threshold: %d)\n"
                            .formatted(item.productName(), item.quantity(), item.lowStockThreshold())));
        }

        return text.toString();
    }
}
