package com.aicontractmanager.suggestion.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SuggestionCompletionRequest(@NotNull UUID contractId) {
}
