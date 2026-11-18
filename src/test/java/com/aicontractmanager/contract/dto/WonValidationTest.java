package com.aicontractmanager.contract.dto;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WonValidationTest {
 @Test void rejectsFractionalPenaltyAndVerification() {
  try (var factory = Validation.buildDefaultValidatorFactory()) {
   var validator = factory.getValidator();
   assertTrue(validator.validate(new AmountVerificationRequest(new BigDecimal("1000"))).isEmpty());
   assertFalse(validator.validate(new AmountVerificationRequest(new BigDecimal("1000.001"))).isEmpty());
   var request = new ContractRequest("계약", "OTHER", null, null, null, null, null,
       BigDecimal.ZERO, new BigDecimal("1.01"), null, null, null, null);
   assertTrue(validator.validate(request).stream().anyMatch(v -> v.getPropertyPath().toString().equals("penaltyAmount")));
  }
 }
}
