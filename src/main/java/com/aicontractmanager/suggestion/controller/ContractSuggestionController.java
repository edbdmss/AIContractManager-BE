package com.aicontractmanager.suggestion.controller;

import com.aicontractmanager.suggestion.domain.ContractSuggestion;
import com.aicontractmanager.suggestion.dto.SuggestionAcceptanceResponse;
import com.aicontractmanager.suggestion.dto.SuggestionCompletionRequest;
import com.aicontractmanager.suggestion.service.ContractSuggestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/aicontract/subscription-suggestions")
public class ContractSuggestionController {
    private final ContractSuggestionService service;

    public ContractSuggestionController(ContractSuggestionService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContractSuggestion> list(Principal principal) {
        return service.listPending(userId(principal));
    }

    @PostMapping("/{id}/accept")
    public SuggestionAcceptanceResponse accept(Principal principal, @PathVariable UUID id) {
        return service.accept(userId(principal), id);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(Principal principal, @PathVariable UUID id) {
        service.reject(userId(principal), id);
    }

    @PostMapping("/{id}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(Principal principal, @PathVariable UUID id,
                         @Valid @RequestBody SuggestionCompletionRequest request) {
        service.linkContract(userId(principal), id, request.contractId());
    }

    private UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
