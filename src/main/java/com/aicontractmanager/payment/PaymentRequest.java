package com.aicontractmanager.payment;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record PaymentRequest(
 @NotBlank @Pattern(regexp="^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", message="eventId는 UUID 형식이어야 합니다.") String eventId,
 @NotBlank @Size(max=200) String merchantName,
 @NotNull @DecimalMin("0.01") @Digits(integer=13, fraction=0) BigDecimal amount,
 @NotNull @PastOrPresent @com.fasterxml.jackson.annotation.JsonFormat(pattern="uuuu-MM-dd", lenient=com.fasterxml.jackson.annotation.OptBoolean.FALSE) LocalDate paidDate,
 @Size(max=255) String sourcePackage,
 @Size(max=50) String parserVersion) {}
