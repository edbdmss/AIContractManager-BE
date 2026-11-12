package com.aicontractmanager.schedule.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ContractSchedule(UUID id, UUID contractId, String scheduleType, LocalDate dueDate,
                               String actionTitle, String actionDescription, int reminderDaysBefore,
                               String status, OffsetDateTime completedAt, OffsetDateTime createdAt) {
}
