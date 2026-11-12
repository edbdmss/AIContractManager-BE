package com.aicontractmanager.contract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 50) String category,
        @Size(max = 200) String providerName,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        LocalDate nextPaymentDate,
        LocalDate cancellationDeadline,
        @jakarta.validation.constraints.NotNull(message="금액을 입력해 주세요.") @DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer=13, fraction=0) BigDecimal monthlyAmount,
        @DecimalMin("0.00") BigDecimal penaltyAmount,
        String cancellationTerms,
        String plainSummary
) {
}
