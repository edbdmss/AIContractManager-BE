package com.aicontractmanager.chatbot.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatSession(UUID id, UUID contractId, LocalDateTime createdAt, LocalDateTime closedAt) {
}
