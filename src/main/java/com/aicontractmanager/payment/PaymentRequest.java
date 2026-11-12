package com.aicontractmanager.payment;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record PaymentRequest(
 @NotBlank @Size(max=100) String eventId,
 @NotBlank @Size(max=200) String merchantName,
 @NotNull @DecimalMin("0.01") @Digits(integer=13, fraction=2) BigDecimal amount,
 @NotNull @PastOrPresent LocalDate paidDate,
 @NotBlank @Size(max=255) String sourcePackage,
 @Size(max=50) String parserVersion) {}
