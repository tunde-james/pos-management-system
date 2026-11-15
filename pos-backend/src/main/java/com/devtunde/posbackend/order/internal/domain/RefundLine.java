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

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "refund_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class RefundLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "refund_id", nullable = false)
    private Long refundId;

    /** Denormalized on purpose: the double-refund
     * UNIQUE lives on (order, product). */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "product_public_id", nullable = false, length = 36)
    private UUID productPublicId;

    @Column(nullable = false)
    private int quantity;

    /** The FROZEN line total from the order snapshot
     * — never re-priced. */
    @Column(name = "line_total_kobo", nullable = false)
    private long lineTotalKobo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RefundDisposition disposition;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static RefundLine of(
            Long refundId,
            Long orderId,
            UUID productPublicId,
            int quantity,
            long lineTotalKobo,
            RefundDisposition disposition) {

        RefundLine line = new RefundLine();
        line.refundId = refundId;
        line.orderId = orderId;
        line.productPublicId = productPublicId;
        line.quantity = quantity;
        line.lineTotalKobo = lineTotalKobo;
        line.disposition = disposition;

        return line;
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
