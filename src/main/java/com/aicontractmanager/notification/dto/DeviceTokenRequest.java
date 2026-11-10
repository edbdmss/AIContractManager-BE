package com.aicontractmanager.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceTokenRequest(
        @NotBlank @Size(max = 500) String token,
        @NotBlank @Size(max = 20) String platform,
        @Size(max = 100) String deviceName) {
}
