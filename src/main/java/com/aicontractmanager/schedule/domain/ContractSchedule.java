package com.aicontractmanager.schedule.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContractSchedule(UUID id, UUID contractId, String scheduleType, LocalDate dueDate,
                               String actionTitle, String actionDescription, int reminderDaysBefore,
                               String status, LocalDateTime completedAt, LocalDateTime createdAt) {
}
