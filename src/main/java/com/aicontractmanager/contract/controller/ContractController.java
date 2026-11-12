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
@RequestMapping("/api/aicontract/contracts")
public class ContractController {

    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    public List<Contract> list(Principal principal,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="created") String sort) {
        List<Contract> result = service.list(userId(principal));
        return switch (sort) {
            case "created" -> result;
            case "amount" -> result.stream().sorted(java.util.Comparator.comparing(Contract::monthlyAmount,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))).toList();
            case "deadline" -> result.stream().sorted(java.util.Comparator.comparing(
                    c -> java.util.stream.Stream.of(c.cancellationDeadline(), c.autoRenewalDate(), c.contractEndDate())
                            .filter(java.util.Objects::nonNull).min(java.time.LocalDate::compareTo).orElse(null),
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))).toList();
            default -> throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"지원하지 않는 정렬입니다.");
        };
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

    @PostMapping("/{id}/cancel")
    public Contract cancel(Principal principal, @PathVariable UUID id) {
        return service.cancel(userId(principal), id);
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
