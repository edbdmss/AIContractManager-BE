package com.aicontractmanager.suggestion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContractSuggestion(UUID id, String merchantName, String category, BigDecimal estimatedAmount,
                                 String estimatedCycle, int detectionCount, String status, LocalDateTime createdAt) {
}
