package com.devtunde.posbackend.store.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class StoreContact {

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false, unique = true)
    private String email;
}
