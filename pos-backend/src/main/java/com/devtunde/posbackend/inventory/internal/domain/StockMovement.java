package com.devtunde.posbackend.inventory.internal.domain;

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
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.devtunde.posbackend.inventory.api.MovementType;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "store_id", nullable = false, length = 36)
    private UUID storeId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "product_public_id", nullable = false, length = 36)
    private UUID productPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType type;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "before_quantity", nullable = false)
    private int beforeQuantity;

    @Column(name = "after_quantity", nullable = false)
    private int afterQuantity;

    @Column(length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "performed_by", nullable = false, length = 36)
    private UUID performedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static StockMovement of(
            UUID storeId,
            UUID productPublicId,
            MovementType type,
            int quantityDelta,
            int beforeQuantity,
            int afterQuantity,
            String reason,
            UUID performedBy) {
        StockMovement movement = new StockMovement();
        movement.storeId = storeId;
        movement.productPublicId = productPublicId;
        movement.type = type;
        movement.quantityDelta = quantityDelta;
        movement.beforeQuantity = beforeQuantity;
        movement.afterQuantity = afterQuantity;
        movement.reason = reason;
        movement.performedBy = performedBy;
        return movement;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
