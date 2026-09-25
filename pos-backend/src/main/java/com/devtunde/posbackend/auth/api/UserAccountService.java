package com.devtunde.posbackend.auth.api;

import java.util.List;
import java.util.UUID;

import com.devtunde.posbackend.auth.api.dto.UserViewResponse;

public interface UserAccountService {

    UserViewResponse currentUser();

    UserViewResponse findByPublicId(UUID publicId);

    List<UserViewResponse> findAll();
}
