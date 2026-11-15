package com.aicontractmanager.payment;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class PaymentRequestValidationTest {
 @Test void enforcesUuidAndWholeWonWithoutRequiringSourceMetadata() {
  try(var factory=Validation.buildDefaultValidatorFactory()) {
   var validator=factory.getValidator();
   var valid=new PaymentRequest("550e8400-e29b-41d4-a716-446655440000","넷플릭스",new BigDecimal("17000"),LocalDate.now(),null,null);
   assertTrue(validator.validate(valid).isEmpty());
   assertFalse(validator.validate(new PaymentRequest("bad-id","넷플릭스",new BigDecimal("17000"),LocalDate.now(),null,null)).isEmpty());
   assertFalse(validator.validate(new PaymentRequest(valid.eventId(),"넷플릭스",new BigDecimal("1.5"),LocalDate.now(),null,null)).isEmpty());
  }
 }
}
