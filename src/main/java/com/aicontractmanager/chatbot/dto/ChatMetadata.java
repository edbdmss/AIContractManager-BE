package com.aicontractmanager.chatbot.dto;

import java.util.List;
import java.util.UUID;

public record ChatMetadata(AnswerType answerType, List<UUID> referencedContractIds,
                           List<LegalSource> legalSources, Boolean needsMoreInfo) {
    public enum AnswerType { CONTRACT, LEGAL, HYBRID }
    public record LegalSource(String ruleId, String lawName, String article, String title, String sourceUrl) { }

    public ChatMetadata {
        referencedContractIds = referencedContractIds == null ? List.of() : List.copyOf(referencedContractIds);
        legalSources = legalSources == null ? List.of() : List.copyOf(legalSources);
    }

    public static ChatMetadata empty() {
        return new ChatMetadata(null, List.of(), List.of(), null);
    }
}
