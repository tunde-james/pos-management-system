package com.devtunde.posbackend.employee.internal.domain;

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

import com.devtunde.posbackend.employee.api.EmployeeJobTitle;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "store_employees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class StoreEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "public_id", nullable = false, length = 36)
    private UUID publicId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "store_id", nullable = false, length = 36)
    private UUID storeId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_public_id", nullable = false, length = 36)
    private UUID userPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_title", nullable = false, length = 20)
    private EmployeeJobTitle jobTitle;

    @Column(name = "hired_at", nullable = false, updatable = false)
    private LocalDateTime hiredAt;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static StoreEmployee create(UUID storeId, UUID userPublicId, EmployeeJobTitle jobTitle) {
        StoreEmployee employment = new StoreEmployee();
        employment.publicId = UUID.randomUUID();
        employment.storeId = storeId;
        employment.userPublicId = userPublicId;
        employment.jobTitle = jobTitle;
        employment.hiredAt = LocalDateTime.now();
        return employment;
    }

    public void terminate() {
        this.terminatedAt = LocalDateTime.now();
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
