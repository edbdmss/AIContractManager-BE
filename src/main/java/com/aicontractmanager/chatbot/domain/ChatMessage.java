package com.aicontractmanager.chatbot.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessage(UUID id, String role, String content, LocalDateTime createdAt) {
}
