package com.aicontractmanager.notification.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Notification(UUID id, UUID contractId, UUID scheduleId, UUID suggestionId,
                           String notificationType, String title, String body,
                           OffsetDateTime scheduledAt, OffsetDateTime sentAt,
                           OffsetDateTime readAt, OffsetDateTime createdAt) {
}
