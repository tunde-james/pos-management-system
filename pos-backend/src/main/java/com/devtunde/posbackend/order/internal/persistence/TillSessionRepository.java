package com.devtunde.posbackend.order.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtunde.posbackend.order.internal.domain.SessionStatus;
import com.devtunde.posbackend.order.internal.domain.TillSession;

public interface TillSessionRepository extends JpaRepository<TillSession, Long> {

    Optional<TillSession> findByPublicId(UUID publicId);

    Optional<TillSession> findByOperatorPublicIdAndStatusNot(UUID operatorPublicId, SessionStatus status);
}
