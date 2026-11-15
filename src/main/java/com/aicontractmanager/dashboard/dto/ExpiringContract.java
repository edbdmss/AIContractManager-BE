package com.aicontractmanager.dashboard.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ExpiringContract(UUID contractId, String title, String providerName,
                               LocalDate contractEndDate, long daysRemaining) {
}
