package com.devtunde.posbackend.order.internal.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.posbackend.order.internal.domain.Refund;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrderIdOrderByRefundedAtAsc(Long orderId);
}
