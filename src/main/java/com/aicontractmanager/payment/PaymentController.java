package com.aicontractmanager.payment;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;
@RestController
@RequestMapping("/api/aicontract/payment-detections")
public class PaymentController {
 private final PaymentService service;
 public PaymentController(PaymentService service) {this.service=service;}
 @PostMapping
 public PaymentService.Result receive(Principal principal, @Valid @RequestBody PaymentRequest request) {
  return service.receive(UUID.fromString(principal.getName()),request);
 }
}
