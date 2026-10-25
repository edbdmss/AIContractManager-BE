package com.aicontractmanager.contract.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record Contract(
        UUID id,
        UUID userId,
        String title,
        String category,
        String providerName,
        String status,
        String sourceType,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        LocalDate autoRenewalDate,
        LocalDate cancellationDeadline,
        BigDecimal monthlyAmount,
        String currency,
        BigDecimal penaltyAmount,
        String cancellationTerms,
        String plainSummary,
        boolean amountVerified
) {
}
