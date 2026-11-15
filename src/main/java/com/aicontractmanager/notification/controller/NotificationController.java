package com.aicontractmanager.notification.controller;

import com.aicontractmanager.notification.domain.Notification;
import com.aicontractmanager.notification.dto.DeviceTokenRequest;
import com.aicontractmanager.notification.service.DeviceTokenService;
import com.aicontractmanager.notification.service.NotificationService;
import com.aicontractmanager.notification.repository.NotificationPreferenceRepository;
import com.aicontractmanager.notification.domain.NotificationPreference;
import com.aicontractmanager.notification.dto.NotificationPreferenceRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/aicontract/notifications")
public class NotificationController {
    private final NotificationService service;
    private final DeviceTokenService deviceTokenService;
    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationController(NotificationService service, DeviceTokenService deviceTokenService,
                                   NotificationPreferenceRepository preferenceRepository) {
        this.service = service;
        this.deviceTokenService = deviceTokenService;
        this.preferenceRepository = preferenceRepository;
    }

    @GetMapping
    public List<Notification> list(Principal principal,
                                   @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return service.list(userId(principal), unreadOnly);
    }

    @PatchMapping("/{id}/read")
    public Notification markRead(Principal principal, @PathVariable UUID id) {
        return service.markRead(userId(principal), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Principal principal) {
        service.markAllRead(userId(principal));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(Principal principal, @PathVariable UUID id) {
        service.dismiss(userId(principal), id);
    }

    @PostMapping("/dismiss-all")
    public java.util.Map<String, Integer> dismissAll(Principal principal) {
        return java.util.Map.of("dismissedCount", service.dismissAll(userId(principal)));
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerDevice(Principal principal, @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.register(userId(principal), request);
    }

    @DeleteMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregisterDevice(Principal principal, @RequestParam String token) {
        deviceTokenService.unregister(userId(principal), token);
    }

    @GetMapping("/preferences")
    public NotificationPreference getPreferences(Principal principal) {
        return preferenceRepository.get(userId(principal));
    }

    @org.springframework.web.bind.annotation.PutMapping("/preferences")
    public NotificationPreference updatePreferences(Principal principal,
            @Valid @RequestBody NotificationPreferenceRequest request) {
        return preferenceRepository.update(userId(principal), request.enabled());
    }

    private UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
