package com.aicontractmanager.payment;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class PaymentServiceTest {
 @Test void retryDoesNotInsertPaymentOrSuggestion() {
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
  var user=java.util.UUID.randomUUID();
  var detection=java.util.UUID.randomUUID();
  org.mockito.Mockito.when(jdbc.queryForList("SELECT id FROM users WHERE id=? AND deleted_at IS NULL FOR UPDATE",String.class,user.toString()))
    .thenReturn(java.util.List.of(user.toString()));
  org.mockito.Mockito.when(jdbc.queryForList("SELECT id FROM payment_detection_events WHERE user_id=? AND client_event_id=?",String.class,user.toString(),"event-1"))
    .thenReturn(java.util.List.of(detection.toString()));
  var result=new PaymentService(jdbc).receive(user,new PaymentRequest("event-1","헬스장",new java.math.BigDecimal("30000"),LocalDate.now().minusDays(1),"test.bank",null));
  assertTrue(result.duplicate());
  assertEquals(detection,result.detectionId());
  org.mockito.Mockito.verify(jdbc).queryForList("SELECT id FROM users WHERE id=? AND deleted_at IS NULL FOR UPDATE",String.class,user.toString());
  org.mockito.Mockito.verify(jdbc).queryForList("SELECT id FROM payment_detection_events WHERE user_id=? AND client_event_id=?",String.class,user.toString(),"event-1");
  org.mockito.Mockito.verifyNoMoreInteractions(jdbc);
 }
 @Test void monthlyIntervalIncludesFebruaryAndExcludesDuplicateDay() {
  var start=LocalDate.of(2026,2,1);
  assertTrue(PaymentService.monthly(start,start.plusDays(28)));
  assertTrue(PaymentService.monthly(start,start.plusDays(31)));
  assertFalse(PaymentService.monthly(start,start));
  assertFalse(PaymentService.monthly(start,start.plusDays(7)));
  assertFalse(PaymentService.monthly(start,start.plusDays(36)));
 }
}
