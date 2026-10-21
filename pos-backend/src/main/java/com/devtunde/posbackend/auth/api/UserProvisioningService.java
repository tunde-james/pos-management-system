package com.devtunde.posbackend.auth.api;

import jakarta.validation.Valid;

import com.devtunde.posbackend.auth.api.dto.UserProvisioningRequest;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;

public interface UserProvisioningService {

    UserViewResponse provision(@Valid UserProvisioningRequest request);
}
