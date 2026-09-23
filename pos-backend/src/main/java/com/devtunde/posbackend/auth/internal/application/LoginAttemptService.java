package com.devtunde.posbackend.auth.internal.application;

public interface LoginAttemptService {

    boolean isLocked(String email);

    void recordFailure(String email);

    void clearAttempts(String email);

    void purgeStale();
}
