package com.devtunde.posbackend.order.internal.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.order.internal.domain.OrderSequence;
import com.devtunde.posbackend.order.internal.persistence.OrderSequenceRepository;

@Component
public class OrderNumberAllocator {

    private final OrderSequenceRepository orderSequenceRepository;

    public OrderNumberAllocator(OrderSequenceRepository orderSequenceRepository) {
        this.orderSequenceRepository = orderSequenceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String mint() {

        orderSequenceRepository.increment();
        OrderSequence sequence = orderSequenceRepository
                .findById(1)
                .orElseThrow(() -> new IllegalStateException("Order sequence row is missing."));
        return "O-" + String.format("%06d", sequence.getCurrentValue());
    }
}
