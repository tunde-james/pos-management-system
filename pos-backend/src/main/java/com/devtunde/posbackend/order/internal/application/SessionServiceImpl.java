package com.devtunde.posbackend.order.internal.application;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.auth.api.UserAccountService;
import com.devtunde.posbackend.auth.api.UserRole;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.employee.api.EmployeeJobTitle;
import com.devtunde.posbackend.employee.api.EmployeeService;
import com.devtunde.posbackend.employee.api.dto.ActiveEmploymentView;
import com.devtunde.posbackend.order.api.SessionService;
import com.devtunde.posbackend.order.api.dto.SessionResponse;
import com.devtunde.posbackend.order.api.exception.OrderAccessDeniedException;
import com.devtunde.posbackend.order.api.exception.SessionConflictException;
import com.devtunde.posbackend.order.api.exception.SessionNotFoundException;
import com.devtunde.posbackend.order.internal.domain.SessionStatus;
import com.devtunde.posbackend.order.internal.domain.TillSession;
import com.devtunde.posbackend.order.internal.persistence.TillSessionRepository;

@Service
public class SessionServiceImpl implements SessionService {

    private final TillSessionRepository tillSessionRepository;
    private final EmployeeService employeeService;
    private final UserAccountService userAccountService;

    public SessionServiceImpl(
            TillSessionRepository tillSessionRepository,
            EmployeeService employeeService,
            UserAccountService userAccountService) {

        this.tillSessionRepository = tillSessionRepository;
        this.employeeService = employeeService;
        this.userAccountService = userAccountService;
    }

    @Override
    @Transactional
    public SessionResponse openSession() {

        UserViewResponse caller = userAccountService.currentUser();

        ActiveEmploymentView era = employeeService
                .activeEraOfUser(caller.publicId())
                .orElseThrow(
                        () -> new OrderAccessDeniedException("No active employment — you cannot operate the till."));

        if (era.jobTitle() != EmployeeJobTitle.CASHIER && era.jobTitle() != EmployeeJobTitle.MANAGER) {
            throw new OrderAccessDeniedException("Only cashiers and managers may operate the till.");
        }

        if (tillSessionRepository
                .findByOperatorPublicIdAndStatusNot(caller.publicId(), SessionStatus.CLOSED)
                .isPresent()) {
            throw new SessionConflictException("You already have an open session. Close it first.");
        }

        TillSession session = TillSession.open(era.storePublicId(), caller.publicId());

        try {

            tillSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("uk_till_sessions_open_slot")) {
                throw new SessionConflictException("You already have an open session. Close it first.");
            }
            throw ex;
        }

        return toResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public SessionResponse getSession(UUID publicId) {

        TillSession session = findSession(publicId);
        UserViewResponse caller = userAccountService.currentUser();

        boolean operator = session.getOperatorPublicId().equals(caller.publicId());
        boolean chainAdmin = caller.role() == UserRole.ROLE_ADMIN;

        if (!operator && !chainAdmin) {
            throw new OrderAccessDeniedException("Only the session operator or the chain admin may read this session.");
        }

        return toResponse(session);
    }

    @Override
    @Transactional
    public SessionResponse closeSession(UUID publicId) {

        TillSession session = findSession(publicId);
        requireOperator(session);

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new SessionConflictException("Only an ACTIVE session can be closed. Resume it first.");
        }

        session.close();
        tillSessionRepository.save(session);

        return toResponse(session);
    }

    @Override
    @Transactional
    public SessionResponse lockSession(UUID publicId) {

        TillSession session = findSession(publicId);
        requireOperator(session);

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new SessionConflictException("Only an ACTIVE session can be locked.");
        }

        session.lock();
        tillSessionRepository.save(session);

        return toResponse(session);
    }

    @Override
    @Transactional
    public SessionResponse resumeSession(UUID publicId) {

        TillSession session = findSession(publicId);
        requireOperator(session);

        if (session.getStatus() != SessionStatus.LOCKED) {
            throw new SessionConflictException("Only a LOCKED session can be resumed.");
        }

        session.resume();
        tillSessionRepository.save(session);

        return toResponse(session);
    }

    private TillSession findSession(UUID publicId) {

        return tillSessionRepository.findByPublicId(publicId).orElseThrow(SessionNotFoundException::new);
    }

    private void requireOperator(TillSession session) {

        UserViewResponse caller = userAccountService.currentUser();

        if (!session.getOperatorPublicId().equals(caller.publicId())) {
            throw new OrderAccessDeniedException("Only the session operator may act on this session.");
        }
    }

    private SessionResponse toResponse(TillSession session) {

        return new SessionResponse(
                session.getPublicId(),
                session.getStatus().name(),
                session.getOperatorPublicId(),
                session.getStoreId(),
                session.getOpenedAt(),
                session.getClosedAt());
    }
}
