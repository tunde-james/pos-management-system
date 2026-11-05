package com.devtunde.posbackend.order.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.order.internal.domain.OrderSequence;

public interface OrderSequenceRepository extends JpaRepository<OrderSequence, Integer> {

    @Modifying
    @Transactional
    @Query("update OrderSequence s set s.currentValue = s.currentValue + 1 where s.id = 1")
    long increment();
}
