package com.devtunde.posbackend.order.internal.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.devtunde.posbackend.payments.api.PaymentProvider;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "public_id", nullable = false, length = 36)
    private UUID publicId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "customer_public_id", length = 36)
    private UUID customerPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_reason", length = 40)
    private OrderStatusReason statusReason;

    @Column(name = "order_number", nullable = false, length = 12)
    private String orderNumber;

    @Column(name = "total_kobo", nullable = false)
    private long totalKobo;

    @Column(name = "paid_at", updatable = false)
    private LocalDateTime paidAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 10)
    private PaymentProvider provider;

    @Column(name = "provider_url", length = 500)
    private String providerUrl;

    @Column(name = "provider_account_number", length = 30)
    private String providerAccountNumber;

    @Column(name = "provider_bank_name", length = 100)
    private String providerBankName;

    @Column(name = "provider_bank_code", length = 10)
    private String providerBankCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Order paid(
            Long sessionId,
            UUID customerPublicId,
            PaymentMethod paymentMethod,
            String paymentReference,
            long totalKobo) {

        Order order = new Order();
        order.publicId = UUID.randomUUID();
        order.sessionId = sessionId;
        order.customerPublicId = customerPublicId;
        order.paymentMethod = paymentMethod;
        order.paymentReference = paymentReference;
        order.status = OrderStatus.PAID;
        order.totalKobo = totalKobo;
        order.paidAt = LocalDateTime.now();

        return order;
    }

    public static Order pendingPayment(
            Long sessionId,
            UUID customerPublicId,
            PaymentMethod paymentMethod,
            long totalKobo,
            LocalDateTime expiresAt) {

        Order order = new Order();
        order.publicId = UUID.randomUUID();
        order.sessionId = sessionId;
        order.customerPublicId = customerPublicId;
        order.paymentMethod = paymentMethod;
        order.status = OrderStatus.PENDING_PAYMENT;
        order.totalKobo = totalKobo;
        order.expiresAt = expiresAt;

        return order;
    }

    public void attachGateway(
            PaymentProvider provider,
            String providerReference,
            String providerUrl,
            String accountNumber,
            String bankName,
            String bankCode) {

        this.provider = provider;
        this.paymentReference = providerReference;
        this.providerUrl = providerUrl;
        this.providerAccountNumber = accountNumber;
        this.providerBankName = bankName;
        this.providerBankCode = bankCode;
    }

    public void assignOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public boolean isAlive() {
        return status == OrderStatus.PENDING_PAYMENT
                && (expiresAt == null || !LocalDateTime.now().isAfter(expiresAt));
    }

    public void markPaid() {
        this.status = OrderStatus.PAID;
        this.paidAt = LocalDateTime.now();
        this.statusReason = null;
    }

    public void markFailed(OrderStatusReason reason) {
        this.status = OrderStatus.FAILED;
        this.statusReason = reason;
    }

    public void markExpired(OrderStatusReason reason) {
        this.status = OrderStatus.EXPIRED;
        this.statusReason = reason;
    }
    
    public void reviveUntil(LocalDateTime newExpiry) {
        this.status = OrderStatus.PENDING_PAYMENT;
        this.statusReason = null;
        this.expiresAt = newExpiry;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
