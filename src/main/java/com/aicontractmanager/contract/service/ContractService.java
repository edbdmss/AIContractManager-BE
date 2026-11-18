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
import java.time.YearMonth;

@Service
public class ContractService {

    private final ContractRepository repository;

    public ContractService(ContractRepository repository) {
        this.repository = repository;
    }

    public List<Contract> list(UUID userId) {
        return repository.findAll(userId);
    }

    @Transactional
    public int rollForwardDueSubscriptionPayments(java.time.LocalDate today) {
        int updated = 0;
        for (ContractRepository.DueSubscriptionPayment due : repository.findDueSubscriptionPayments(today)) {
            int billingDay = due.paymentDayOfMonth() == null
                    ? due.nextPaymentDate().getDayOfMonth()
                    : due.paymentDayOfMonth();
            java.time.LocalDate nextPaymentDate = due.nextPaymentDate();
            do {
                YearMonth nextMonth = YearMonth.from(nextPaymentDate).plusMonths(due.renewalCycleMonths());
                nextPaymentDate = nextMonth.atDay(Math.min(billingDay, nextMonth.lengthOfMonth()));
            } while (nextPaymentDate.isBefore(today));

            if (due.contractEndDate() != null && nextPaymentDate.isAfter(due.contractEndDate())) continue;

            if (repository.advanceSubscriptionPayment(due, nextPaymentDate) == 1) {
                repository.refreshSchedulesAfterPaymentRollover(due.id(), due.userId());
                updated++;
            }
        }
        return updated;
    }

    public Contract get(UUID userId, UUID id) {
        return repository.findOwned(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "계약을 찾을 수 없습니다."));
    }

    @Transactional
    public Contract create(UUID userId, ContractRequest request) {
        validateDates(request, false);
        UUID id = UUID.randomUUID();
        repository.create(id, userId, normalized(request, null));
        return get(userId, id);
    }

    @Transactional
    public Contract createDraft(UUID userId, ContractRequest request) {
        Contract contract = create(userId, request);
        repository.markDraft(contract.id(), userId);
        return get(userId, contract.id());
    }

    @Transactional
    public Contract save(UUID userId, UUID id, ContractRequest request) {
        get(userId, id);
        repository.completeRegistration(id, userId);
        return update(userId, id, request);
    }

    @Transactional
    public Contract update(UUID userId, UUID id, ContractRequest request) {
        Contract existing = get(userId, id);
        validateDates(request, repository.isDocumentRegistration(id, userId));
        boolean amountChanged = !equalAmount(existing.monthlyAmount(), request.monthlyAmount());
        repository.update(id, userId, normalized(request, existing), amountChanged);
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

    @Transactional
    public Contract cancel(UUID userId, UUID id) {
        get(userId, id);
        repository.cancel(id, userId);
        return get(userId, id);
    }

    public List<Contract> attention(UUID userId) {
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        return repository.findAttention(userId, today, today.plusDays(7));
    }

    private ContractRequest normalized(ContractRequest request, Contract existing) {
        Integer cycle = request.renewalCycleMonths();
        if (cycle == null && existing != null) cycle = existing.renewalCycleMonths();
        boolean verified = request.amountVerified() != null ? request.amountVerified()
                : existing != null && existing.amountVerified()
                  && equalAmount(existing.monthlyAmount(), request.monthlyAmount());
        if (verified && request.monthlyAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "확인할 금액이 필요합니다.");
        }
        return new ContractRequest(request.title(), request.category(), request.providerName(),
                request.contractStartDate(), request.contractEndDate(), request.nextPaymentDate(),
                request.cancellationDeadline(), request.monthlyAmount(), request.penaltyAmount(),
                request.cancellationTerms(), request.plainSummary(), cycle, verified);
    }

    private void validateDates(ContractRequest request, boolean documentRegistration) {
        if (request.contractStartDate() != null && request.contractEndDate() != null
                && request.contractEndDate().isBefore(request.contractStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "계약 종료일은 시작일보다 빠를 수 없습니다.");
        }
        boolean incompleteDocument = "UNCLASSIFIED".equalsIgnoreCase(request.category());
        if (!documentRegistration && !incompleteDocument) {
            if (request.nextPaymentDate() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "다음 결제일을 입력해 주세요.");
            }
            if (!"SUBSCRIPTION".equalsIgnoreCase(request.category())
                    && request.contractStartDate() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "계약 시작일을 입력해 주세요.");
            }
        }
        if (!documentRegistration && !incompleteDocument
                && "SUBSCRIPTION".equalsIgnoreCase(request.category())
                && (request.monthlyAmount() == null || request.nextPaymentDate() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "구독은 월 금액과 다음 결제일을 입력해야 합니다.");
        }
    }

    private boolean equalAmount(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }
}
