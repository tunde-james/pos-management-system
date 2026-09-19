package com.devtunde.posbackend.auth.internal.application;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.posbackend.auth.api.AuthService;
import com.devtunde.posbackend.auth.api.dto.AuthResponse;
import com.devtunde.posbackend.auth.api.dto.LoginRequest;
import com.devtunde.posbackend.auth.api.dto.SignupRequest;
import com.devtunde.posbackend.auth.api.exception.AccountLockedException;
import com.devtunde.posbackend.auth.api.exception.EmailAlreadyRegisteredException;
import com.devtunde.posbackend.auth.api.exception.InvalidCredentialsException;
import com.devtunde.posbackend.auth.internal.domain.User;
import com.devtunde.posbackend.auth.internal.jwt.JwtService;
import com.devtunde.posbackend.auth.internal.mapper.UserMapper;
import com.devtunde.posbackend.auth.internal.persistence.UserRepository;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final PhoneNormalizer phoneNormalizer;
    private final LoginAttemptService loginAttemptService;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserMapper userMapper,
            PhoneNormalizer phoneNormalizer,
            LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.phoneNormalizer = phoneNormalizer;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    @Transactional
    public AuthResponse signup(SignupRequest request) {

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyRegisteredException(request.email());
        }

        User user = User.register(
                request.fullName(),
                request.email(),
                phoneNormalizer.normalize(request.phone()),
                passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        loginAttemptService.clearAttempts(request.email());

        String accessToken = issueAccessToken(user);

        return new AuthResponse(accessToken, "Signup successful", userMapper.toView(user));
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {

        if (loginAttemptService.isLocked(request.email())) {
            throw new AccountLockedException();
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {

            loginAttemptService.recordFailure(request.email());

            throw new InvalidCredentialsException();
        }

        loginAttemptService.clearAttempts(request.email());

        user.recordLogin();

        String accessToken = issueAccessToken(user);

        return new AuthResponse(accessToken, "Login successful", userMapper.toView(user));
    }

    private String issueAccessToken(User user) {
        return jwtService.generateToken(
                user.getEmail(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
    }
}
