package com.aicontractmanager.chatbot.dto;

import java.util.List;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.JsonNode;

public record ChatAiRequest(String question, List<ChatTurn> history, List<ContractContext> contractContext) {
    public record ChatTurn(String role, String content) { }
    public record ContractContext(String contractId, String title, String category, String providerName,
                                  String status, String contractEndDate, String nextPaymentDate,
                                  String cancellationDeadline, BigDecimal monthlyAmount,
                                  String cancellationTerms, String plainSummary,
                                  String contractStartDate, BigDecimal penaltyAmount,
                                  boolean amountVerified, String currency, JsonNode riskReports) { }
}
