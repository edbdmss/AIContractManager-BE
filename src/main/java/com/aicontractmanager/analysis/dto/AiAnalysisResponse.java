package com.aicontractmanager.analysis.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record AiAnalysisResponse(
        UUID analysisId,
        UUID documentId,
        String status,
        JsonNode result,
        LocalDateTime completedAt
) {
}
