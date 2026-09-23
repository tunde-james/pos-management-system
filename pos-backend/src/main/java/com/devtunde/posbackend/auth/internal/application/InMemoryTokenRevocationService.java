package com.devtunde.posbackend.auth.internal.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class InMemoryTokenRevocationService implements TokenRevocationService {

    private final Map<String, Instant> revoked = new HashMap<>();

    @Override
    public void revoke(String jti, Instant expiresAt) {
        revoked.put(jti, expiresAt);
    }

    @Override
    public synchronized boolean isRevoked(String jti) {
        Instant expiresAt = revoked.get(jti);

        if (expiresAt == null) {
            return false;
        }

        if (Instant.now().isAfter(expiresAt)) {
            revoked.remove(jti);

            return false;
        }

        return true;
    }
}
