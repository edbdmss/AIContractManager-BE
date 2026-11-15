package com.aicontractmanager.chatbot.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMessage(UUID id, String role, String content, OffsetDateTime createdAt) {
}
