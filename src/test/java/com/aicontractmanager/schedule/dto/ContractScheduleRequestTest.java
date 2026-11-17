package com.aicontractmanager.schedule.dto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class ContractScheduleRequestTest {
 @Test void defaultThreeDaysPreservesExplicitZero() throws Exception {
  var mapper = new ObjectMapper().findAndRegisterModules();
  assertEquals(3, mapper.readValue("{}", ContractScheduleRequest.class).reminderDaysBefore());
  assertEquals(3, mapper.readValue("{\"reminderDaysBefore\":null}", ContractScheduleRequest.class).reminderDaysBefore());
  assertEquals(0, mapper.readValue("{\"reminderDaysBefore\":0}", ContractScheduleRequest.class).reminderDaysBefore());
  assertEquals(7, mapper.readValue("{\"reminderDaysBefore\":7}", ContractScheduleRequest.class).reminderDaysBefore());
 }
}
