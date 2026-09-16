package com.devtunde.posbackend.auth.api;

import com.devtunde.posbackend.auth.api.dto.AuthResponse;
import com.devtunde.posbackend.auth.api.dto.LoginRequest;
import com.devtunde.posbackend.auth.api.dto.SignupRequest;

public interface AuthService {

    AuthResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);
}
