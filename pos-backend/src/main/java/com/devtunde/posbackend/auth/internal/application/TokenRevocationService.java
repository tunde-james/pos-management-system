package com.devtunde.posbackend.auth.internal.application;

import java.time.Instant;

public interface TokenRevocationService {

    void revoke(String jti, Instant expiresAt);

    boolean isRevoked(String jti);
}
