package com.devtunde.posbackend.auth.internal.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import com.devtunde.posbackend.auth.api.UserProvisioningService;
import com.devtunde.posbackend.auth.api.dto.UserProvisioningRequest;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;
import com.devtunde.posbackend.auth.internal.domain.User;
import com.devtunde.posbackend.auth.internal.mapper.UserMapper;
import com.devtunde.posbackend.auth.internal.persistence.UserRepository;
import com.devtunde.posbackend.common.api.validation.PhoneNormalizer;

@Service
@Validated
public class UserProvisioningServiceImpl implements UserProvisioningService {

    private final UserRepository userRepository;
    private final UserProvisioningWriter
 userProvisioningWriter;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNormalizer phoneNormalizer;
    private final UserMapper userMapper;

    public UserProvisioningServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserProvisioningWriter
 userProvisioningWriter,
            PhoneNormalizer phoneNormalizer,
            UserMapper userMapper) {

        this.userRepository = userRepository;
        this.userProvisioningWriter = userProvisioningWriter;
        this.passwordEncoder = passwordEncoder;
        this.phoneNormalizer = phoneNormalizer;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public UserViewResponse provision(UserProvisioningRequest request) {

        User existing = userRepository.findByEmail(request.email()).orElse(null);

        if (existing != null) {
            return userMapper.toView(existing);
        }

        User user = User.register(
                request.fullName(),
                request.email(),
                phoneNormalizer.normalize(request.phone()),
                passwordEncoder.encode(request.password()));

        try {
            user = userProvisioningWriter.insert(user);
        } catch (DataIntegrityViolationException ex) {
            User raced = userProvisioningWriter.findRacedByEmail(request.email()).orElse(null);

            if (raced == null) {
                throw ex;
            }

            return userMapper.toView(raced);
        }

        return userMapper.toView(user);
    }
}
