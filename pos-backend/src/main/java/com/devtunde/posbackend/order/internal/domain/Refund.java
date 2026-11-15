package com.devtunde.posbackend.order.internal.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "public_id", nullable = false, length = 36)
    private UUID publicId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "total_kobo", nullable = false)
    private long totalKobo;

    @Column(name = "return_method", length = 13)
    private String returnMethod;

    /** The provider's refund reference; null for
     * counter-settled orders. */
    @Column(name = "provider_refund_reference", length = 255)
    private String providerRefundReference;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "actor_public_id", nullable = false, length = 36)
    private UUID actorPublicId;

    @Column(name = "refunded_at", nullable = false)
    private LocalDateTime refundedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Refund of(
            Long orderId, long totalKobo, String returnMethod, String providerRefundReference, UUID actorPublicId) {

        Refund refund = new Refund();
        refund.publicId = UUID.randomUUID();
        refund.orderId = orderId;
        refund.totalKobo = totalKobo;
        refund.returnMethod = returnMethod;
        refund.providerRefundReference = providerRefundReference;
        refund.actorPublicId = actorPublicId;
        refund.refundedAt = LocalDateTime.now();

        return refund;
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
