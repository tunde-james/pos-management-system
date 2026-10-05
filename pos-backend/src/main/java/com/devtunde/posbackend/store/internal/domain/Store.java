package com.devtunde.posbackend.store.internal.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

import com.devtunde.posbackend.store.api.StoreStatus;
import com.devtunde.posbackend.store.api.StoreType;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
    private UUID publicId;

    @Setter
    @Column(nullable = false, unique = true)
    private String brand;

    @Setter
    @Column(length = 500)
    private String description;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoreType storeType;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoreStatus status;

    @Setter
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "store_admin_id", nullable = false, unique = true, length = 36)
    private UUID storeAdminId;

    @Setter
    @Embedded
    private StoreContact contact = new StoreContact();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }

        if (status == null) {
            status = StoreStatus.ACTIVE;
        }

        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static Store create(
            String brand,
            String description,
            StoreType storeType,
            String address,
            String phone,
            String email,
            UUID storeAdminId) {
        Store store = new Store();
        store.brand = brand;
        store.description = description;
        store.storeType = storeType;
        store.storeAdminId = storeAdminId;

        StoreContact contact = new StoreContact();
        contact.setAddress(address);
        contact.setPhone(phone);
        contact.setEmail(email);

        store.contact = contact;

        return store;
    }
}
