package com.devtunde.posbackend.order.api;

import java.util.UUID;

import com.devtunde.posbackend.order.api.dto.SessionResponse;

public interface SessionService {

    SessionResponse openSession();

    SessionResponse getSession(UUID publicId);

    SessionResponse closeSession(UUID publicId);

    SessionResponse lockSession(UUID publicId);

    SessionResponse resumeSession(UUID publicId);
}
