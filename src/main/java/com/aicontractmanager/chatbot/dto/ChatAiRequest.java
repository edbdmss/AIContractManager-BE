package com.aicontractmanager.chatbot.dto;

import java.util.List;

public record ChatAiRequest(String question, List<ChatTurn> history, List<ContractContext> contractContext) {
    public record ChatTurn(String role, String content) { }
    public record ContractContext(String contractId, String title, String category, String providerName,
                                  String status, String contractEndDate, String nextPaymentDate,
                                  String cancellationDeadline, String monthlyAmount,
                                  String cancellationTerms, String plainSummary) { }
}
