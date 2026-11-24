package com.aicontractmanager.chatbot.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.aicontractmanager.chatbot.dto.ChatMetadata;

public record ChatMessage(UUID id, String role, String content, OffsetDateTime createdAt,
                          @JsonUnwrapped ChatMetadata metadata) {
}
