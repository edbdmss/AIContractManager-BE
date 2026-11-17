package com.aicontractmanager.contract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AmountVerificationRequest(@NotNull @DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer=13, fraction=0) BigDecimal monthlyAmount) {
}
