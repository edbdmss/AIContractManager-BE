package com.aicontractmanager.chatbot.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatSession(UUID id, UUID contractId, OffsetDateTime createdAt, OffsetDateTime closedAt) {
}
