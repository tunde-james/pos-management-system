package com.devtunde.posbackend.auth.internal.application;

public interface RateLimiter {

    boolean tryAcquire(String key);

    void purgeStaleWindows();
}
