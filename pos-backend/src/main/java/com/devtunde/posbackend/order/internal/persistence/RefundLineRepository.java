package com.devtunde.posbackend.order.internal.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.posbackend.order.internal.domain.RefundLine;

public interface RefundLineRepository extends JpaRepository<RefundLine, Long> {

    List<RefundLine> findByOrderId(Long orderId);

    long countByOrderId(Long orderId);
}
