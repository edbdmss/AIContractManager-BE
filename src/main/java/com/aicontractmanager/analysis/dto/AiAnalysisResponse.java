package com.aicontractmanager.analysis.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiAnalysisResponse(
        UUID analysisId,
        UUID documentId,
        String status,
        JsonNode result,
        OffsetDateTime completedAt
) {
}
