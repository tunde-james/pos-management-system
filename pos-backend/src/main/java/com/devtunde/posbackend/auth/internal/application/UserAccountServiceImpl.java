package com.devtunde.posbackend.auth.internal.application;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.devtunde.posbackend.auth.api.UserAccountService;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.auth.api.exception.UserNotFoundException;
import com.devtunde.posbackend.auth.internal.mapper.UserMapper;
import com.devtunde.posbackend.auth.internal.persistence.UserRepository;

@Service
public class UserAccountServiceImpl implements UserAccountService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserAccountServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public UserViewResponse currentUser() {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmail(email).map(userMapper::toView).orElseThrow(UserNotFoundException::new);
    }

    @Override
    public UserViewResponse findByPublicId(UUID publicId) {

        return userRepository.findByPublicId(publicId).map(userMapper::toView).orElseThrow(UserNotFoundException::new);
    }

    @Override
    public List<UserViewResponse> findAll() {

        return userRepository.findAll().stream().map(userMapper::toView).toList();
    }
}
