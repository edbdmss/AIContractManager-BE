package com.aicontractmanager.document.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Document(
        UUID id,
        UUID contractId,
        String documentType,
        String originalFilename,
        String storageKey,
        String contentType,
        long fileSize,
        String checksum,
        boolean primary,
        OffsetDateTime uploadedAt
) {
}
