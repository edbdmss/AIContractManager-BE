package com.aicontractmanager.notification.service;

import com.aicontractmanager.notification.dto.DeviceTokenRequest;
import com.aicontractmanager.notification.repository.DeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class DeviceTokenService {
    private final DeviceTokenRepository repository;

    public DeviceTokenService(DeviceTokenRepository repository) {
        this.repository = repository;
    }

    public void register(UUID userId, DeviceTokenRequest request) {
        String platform = request.platform().toUpperCase(Locale.ROOT);
        if (!platform.equals("ANDROID") && !platform.equals("IOS")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platform은 ANDROID 또는 IOS여야 합니다.");
        }
        repository.upsert(userId, request.token(), platform, request.deviceName());
    }

    public void unregister(UUID userId, String token) {
        repository.deactivate(userId, token);
    }
}
