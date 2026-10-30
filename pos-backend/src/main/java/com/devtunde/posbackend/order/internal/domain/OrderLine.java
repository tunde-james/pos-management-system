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
@Table(name = "order_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "product_public_id", nullable = false, length = 36)
    private UUID productPublicId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "unit_price_kobo", nullable = false)
    private long unitPriceKobo;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total_kobo", nullable = false)
    private long lineTotalKobo;

    @Column(nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

    public static OrderLine of(
            Long orderId, UUID productPublicId, String productName, long unitPriceKobo, int quantity) {

        OrderLine line = new OrderLine();
        line.orderId = orderId;
        line.productPublicId = productPublicId;
        line.productName = productName;
        line.unitPriceKobo = unitPriceKobo;
        line.quantity = quantity;
        line.lineTotalKobo = unitPriceKobo * quantity;

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
