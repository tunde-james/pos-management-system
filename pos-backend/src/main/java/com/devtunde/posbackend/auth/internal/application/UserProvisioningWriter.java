package com.devtunde.posbackend.auth.internal.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.auth.internal.domain.User;
import com.devtunde.posbackend.auth.internal.persistence.UserRepository;

@Service
public class UserProvisioningWriter {

    private final UserRepository userRepository;

    public UserProvisioningWriter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User insert(User user) {

        return userRepository.saveAndFlush(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<User> findRacedByEmail(String email) {

        return userRepository.findByEmail(email);
    }
}
