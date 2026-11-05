package com.aicontractmanager.suggestion.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SuggestionAcceptanceResponse(UUID suggestionId, String subscriptionName,
                                          BigDecimal estimatedAmount, String estimatedCycle) {
}
