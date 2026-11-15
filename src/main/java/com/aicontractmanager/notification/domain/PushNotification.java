package com.aicontractmanager.notification.domain;

import java.util.UUID;

public record PushNotification(UUID id, UUID userId, String title, String body, int attempts) {
}
