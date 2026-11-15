package com.aicontractmanager.notification.domain;

import java.time.OffsetDateTime;

public record NotificationPreference(boolean enabled, OffsetDateTime updatedAt) {
}
