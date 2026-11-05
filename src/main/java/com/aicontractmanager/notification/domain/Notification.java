package com.aicontractmanager.notification.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record Notification(UUID id, UUID contractId, UUID scheduleId, UUID suggestionId,
                           String notificationType, String title, String body,
                           LocalDateTime scheduledAt, LocalDateTime sentAt,
                           LocalDateTime readAt, LocalDateTime createdAt) {
}
