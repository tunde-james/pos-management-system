package com.devtunde.posbackend.order.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.posbackend.order.api.dto.SessionResponse;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse open() {

        return sessionService.openSession();
    }

    @GetMapping("/{publicId}")
    public SessionResponse read(@PathVariable UUID publicId) {

        return sessionService.getSession(publicId);
    }

    @PostMapping("/{publicId}/close")
    public SessionResponse close(@PathVariable UUID publicId) {

        return sessionService.closeSession(publicId);
    }

    @PostMapping("/{publicId}/lock")
    public SessionResponse lock(@PathVariable UUID publicId) {

        return sessionService.lockSession(publicId);
    }

    @PostMapping("/{publicId}/resume")
    public SessionResponse resume(@PathVariable UUID publicId) {

        return sessionService.resumeSession(publicId);
    }
}
