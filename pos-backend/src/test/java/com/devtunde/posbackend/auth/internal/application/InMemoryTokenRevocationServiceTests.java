package com.devtunde.posbackend.auth.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryTokenRevocationServiceTests {

    private final InMemoryTokenRevocationService service = new InMemoryTokenRevocationService();

    @Test
    @DisplayName("a revoked jti is revoked")
    void revokedJtiIsRevoked() {
        service.revoke("jti-1", Instant.now().plusSeconds(600));

        assertThat(service.isRevoked("jti-1")).isTrue();
    }

    @Test
    @DisplayName("an unknown jti is not revoked")
    void unknownJtiIsNotRevoked() {

        assertThat(service.isRevoked("never-seen")).isFalse();
    }

    @Test
    @DisplayName("an entry for an already-expired token is ignored and discarded")
    void expiredEntryIsIgnored() {
        service.revoke("jti-2", Instant.now().minusSeconds(1));

        assertThat(service.isRevoked("jti-2")).isFalse();
    }
}
