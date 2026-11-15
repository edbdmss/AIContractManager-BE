package com.aicontractmanager.suggestion.service;

import com.aicontractmanager.suggestion.domain.ContractSuggestion;
import com.aicontractmanager.suggestion.dto.SuggestionAcceptanceResponse;
import com.aicontractmanager.suggestion.repository.ContractSuggestionRepository;
import com.aicontractmanager.contract.service.ContractService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ContractSuggestionService {
    private final ContractSuggestionRepository repository;
    private final ContractService contracts;

    public ContractSuggestionService(ContractSuggestionRepository repository, ContractService contracts) {
        this.repository = repository;
        this.contracts = contracts;
    }

    public List<ContractSuggestion> listPending(UUID userId) {
        return repository.findPending(userId);
    }

    @Transactional
    public SuggestionAcceptanceResponse accept(UUID userId, UUID suggestionId) {
        ContractSuggestion suggestion = getOwned(userId, suggestionId);
        if (!"PENDING".equals(suggestion.status()) || repository.respond(suggestionId, userId, "ACCEPTED") != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 구독 제안입니다.");
        }
        return new SuggestionAcceptanceResponse(suggestion.id(), suggestion.merchantName(),
                suggestion.estimatedAmount(), suggestion.estimatedCycle(), repository.nextPaymentDate(suggestionId, userId));
    }

    @Transactional
    public void reject(UUID userId, UUID suggestionId) {
        getOwned(userId, suggestionId);
        if (repository.respond(suggestionId, userId, "REJECTED") != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 구독 제안입니다.");
        }
    }

    @Transactional
    public void linkContract(UUID userId, UUID suggestionId, UUID contractId) {
        contracts.get(userId, contractId);
        if (repository.linkContract(suggestionId, userId, contractId) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "제안에 연결할 수 없는 구독입니다.");
        }
    }

    private ContractSuggestion getOwned(UUID userId, UUID suggestionId) {
        return repository.findOwned(suggestionId, userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "구독 제안을 찾을 수 없습니다."));
    }
}
