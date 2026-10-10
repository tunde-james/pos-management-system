package com.devtunde.posbackend.product.internal.domain;


   import java.math.BigDecimal;
   import java.time.LocalDateTime;
   import java.util.UUID;

   import jakarta.persistence.Column;
   import jakarta.persistence.Entity;
   import jakarta.persistence.FetchType;
   import jakarta.persistence.GeneratedValue;
   import jakarta.persistence.GenerationType;
   import jakarta.persistence.Id;
   import jakarta.persistence.JoinColumn;
   import jakarta.persistence.ManyToOne;
   import jakarta.persistence.PrePersist;
   import jakarta.persistence.PreUpdate;
   import jakarta.persistence.Table;

   import
 org.hibernate.annotations.JdbcTypeCode;
   import org.hibernate.type.SqlTypes;

   import lombok.AccessLevel;
   import lombok.EqualsAndHashCode;
   import lombok.Getter;
   import lombok.NoArgsConstructor;
   import lombok.Setter;
   import lombok.ToString;

@Entity 
@Table(name = "store_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString 
public class StoreProduct {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "store_id", nullable = false, length = 36)
    private UUID storeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private  Product product;

    @Setter
    @Column(name="maximum_retail_price",nullable = false, precision = 12, scale = 2)
    private BigDecimal mrp;

    @Setter 
    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Setter
    @Column(name ="discount_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt= now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static StoreProduct create(UUID storeId, Product product, BigDecimal mrp, BigDecimal sellingPrice, BigDecimal discountPercentage) {
        StoreProduct listing = new StoreProduct();
        listing.storeId = storeId;
        listing.product = product;
        listing.mrp = mrp;
        listing.sellingPrice = sellingPrice;
        listing.discountPercentage = discountPercentage == null ? BigDecimal.ZERO : discountPercentage;

        return  listing;
    }

}
