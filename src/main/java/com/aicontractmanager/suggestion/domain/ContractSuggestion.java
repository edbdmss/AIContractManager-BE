package com.aicontractmanager.suggestion.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ContractSuggestion(UUID id, String merchantName, String category, BigDecimal estimatedAmount,
                                 String estimatedCycle, int detectionCount, String status, OffsetDateTime createdAt) {
}
