package com.aicontractmanager.contract.service;

import com.aicontractmanager.contract.domain.Contract;
import com.aicontractmanager.contract.dto.ContractRequest;
import com.aicontractmanager.contract.repository.ContractRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ContractService {

    private final ContractRepository repository;

    public ContractService(ContractRepository repository) {
        this.repository = repository;
    }

    public List<Contract> list(UUID userId) {
        return repository.findAll(userId);
    }

    public Contract get(UUID userId, UUID id) {
        return repository.findOwned(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "계약을 찾을 수 없습니다."));
    }

    @Transactional
    public Contract create(UUID userId, ContractRequest request) {
        validateDates(request);
        UUID id = UUID.randomUUID();
        repository.create(id, userId, request);
        return get(userId, id);
    }

    @Transactional
    public Contract update(UUID userId, UUID id, ContractRequest request) {
        validateDates(request);
        Contract existing = get(userId, id);
        boolean amountChanged = !equalAmount(existing.monthlyAmount(), request.monthlyAmount());
        repository.update(id, userId, request, amountChanged);
        return get(userId, id);
    }

    @Transactional
    public Contract verifyAmount(UUID userId, UUID id, BigDecimal amount) {
        Contract contract = get(userId, id);
        if (contract.monthlyAmount() == null || contract.monthlyAmount().compareTo(amount) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "등록된 월 금액과 일치하지 않습니다.");
        }
        repository.verifyAmount(id, userId);
        return get(userId, id);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        get(userId, id);
        repository.delete(id, userId);
    }

    private void validateDates(ContractRequest request) {
        if (request.contractStartDate() != null && request.contractEndDate() != null
                && request.contractEndDate().isBefore(request.contractStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "계약 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private boolean equalAmount(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }
}
