package com.aicontractmanager.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ContractScheduleRequest(
        @NotBlank String scheduleType,
        @NotNull LocalDate dueDate,
        @NotBlank @Size(max = 200) String actionTitle,
        String actionDescription,
        @PositiveOrZero int reminderDaysBefore
) {
}
