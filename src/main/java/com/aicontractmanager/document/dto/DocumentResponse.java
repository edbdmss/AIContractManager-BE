package com.aicontractmanager.document.dto;

import com.aicontractmanager.document.domain.Document;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID contractId,
        String documentType,
        String originalFilename,
        String contentType,
        long fileSize,
        String checksum,
        boolean primary,
        LocalDateTime uploadedAt
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(document.id(), document.contractId(), document.documentType(),
                document.originalFilename(), document.contentType(), document.fileSize(),
                document.checksum(), document.primary(), document.uploadedAt());
    }
}
