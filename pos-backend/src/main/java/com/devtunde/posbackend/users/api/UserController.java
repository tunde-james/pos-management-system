package com.devtunde.posbackend.users.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.posbackend.auth.api.UserAccountService;
import com.devtunde.posbackend.auth.api.dto.UserViewResponse;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/profile")
    public ResponseEntity<UserViewResponse> getProfile() {
        return ResponseEntity.ok(userAccountService.currentUser());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserViewResponse> getUserById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(userAccountService.findByPublicId(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserViewResponse>> getAllUsers() {
        return ResponseEntity.ok(userAccountService.findAll());
    }
}
