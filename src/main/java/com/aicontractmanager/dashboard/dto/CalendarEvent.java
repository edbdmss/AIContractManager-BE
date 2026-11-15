package com.aicontractmanager.dashboard.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CalendarEvent(LocalDate date, String eventType, UUID contractId,
                            String contractTitle, String title, String description) {
}
