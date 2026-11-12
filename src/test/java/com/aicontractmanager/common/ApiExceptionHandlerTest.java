package com.aicontractmanager.common;
import com.aicontractmanager.payment.PaymentRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ApiExceptionHandlerTest {
 @RestController static class Endpoint {
  @PostMapping("/test-payment") void submit(@Valid @RequestBody PaymentRequest body) {}
 }
 @Test void exposesFieldErrorsAndHandlesMalformedDates() throws Exception {
  var mvc=MockMvcBuilders.standaloneSetup(new Endpoint()).setControllerAdvice(new ApiExceptionHandler()).build();
  mvc.perform(post("/test-payment").contentType("application/json").content("{\"eventId\":\"invalid\",\"merchantName\":\"넷플릭스\",\"amount\":1.5,\"paidDate\":\"2020-01-01\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
   .andExpect(jsonPath("$.message").exists()).andExpect(jsonPath("$.fieldErrors.length()").value(2));
  mvc.perform(post("/test-payment").contentType("application/json").content("{\"paidDate\":\"2020-01-01T10:00:00\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists())
   .andExpect(jsonPath("$.message").exists()).andExpect(jsonPath("$.fieldErrors").isArray());
 }
}
