package com.aicontractmanager.contract.controller;

import com.aicontractmanager.contract.domain.Contract;
import com.aicontractmanager.contract.dto.AmountVerificationRequest;
import com.aicontractmanager.contract.dto.ContractRequest;
import com.aicontractmanager.contract.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
public class ContractController {

    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    public List<Contract> list(Principal principal) {
        return service.list(userId(principal));
    }

    @GetMapping("/{id}")
    public Contract get(Principal principal, @PathVariable UUID id) {
        return service.get(userId(principal), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Contract create(Principal principal, @Valid @RequestBody ContractRequest request) {
        return service.create(userId(principal), request);
    }

    @PutMapping("/{id}")
    public Contract update(Principal principal, @PathVariable UUID id, @Valid @RequestBody ContractRequest request) {
        return service.update(userId(principal), id, request);
    }

    @PostMapping("/{id}/verify-amount")
    public Contract verifyAmount(Principal principal, @PathVariable UUID id,
                                 @Valid @RequestBody AmountVerificationRequest request) {
        return service.verifyAmount(userId(principal), id, request.monthlyAmount());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable UUID id) {
        service.delete(userId(principal), id);
    }

    private UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
