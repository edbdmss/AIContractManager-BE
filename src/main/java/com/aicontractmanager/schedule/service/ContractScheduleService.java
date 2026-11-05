package com.aicontractmanager.schedule.service;

import com.aicontractmanager.contract.service.ContractService;
import com.aicontractmanager.notification.service.NotificationService;
import com.aicontractmanager.schedule.domain.ContractSchedule;
import com.aicontractmanager.schedule.dto.ContractScheduleRequest;
import com.aicontractmanager.schedule.repository.ContractScheduleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ContractScheduleService {
    private static final Set<String> TYPES = Set.of("RENEWAL", "CANCELLATION", "PAYMENT", "EXPIRY", "CUSTOM");
    private final ContractService contracts;
    private final ContractScheduleRepository repository;
    private final NotificationService notifications;

    public ContractScheduleService(ContractService contracts, ContractScheduleRepository repository,
                                   NotificationService notifications) {
        this.contracts = contracts;
        this.repository = repository;
        this.notifications = notifications;
    }

    public List<ContractSchedule> list(UUID userId, UUID contractId) {
        contracts.get(userId, contractId);
        return repository.findByContract(contractId, userId);
    }

    public List<ContractSchedule> listUpcoming(UUID userId) {
        return repository.findUpcoming(userId);
    }

    @Transactional
    public ContractSchedule create(UUID userId, UUID contractId, ContractScheduleRequest request) {
        contracts.get(userId, contractId);
        validate(request);
        UUID id = UUID.randomUUID();
        repository.create(id, contractId, new ContractScheduleRepository.ContractScheduleRequestData(
                request.scheduleType(), request.dueDate(), request.actionTitle().trim(),
                request.actionDescription(), request.reminderDaysBefore()));
        notifications.createScheduleReminder(userId, contractId, id, request);
        return repository.findOwned(id, userId).orElseThrow();
    }

    @Transactional
    public ContractSchedule complete(UUID userId, UUID scheduleId) {
        return transition(userId, scheduleId, "COMPLETED");
    }

    @Transactional
    public ContractSchedule dismiss(UUID userId, UUID scheduleId) {
        return transition(userId, scheduleId, "DISMISSED");
    }

    private ContractSchedule transition(UUID userId, UUID scheduleId, String status) {
        repository.findOwned(scheduleId, userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));
        if (repository.markStatus(scheduleId, userId, status) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 일정입니다.");
        }
        notifications.cancelPendingScheduleReminders(scheduleId);
        return repository.findOwned(scheduleId, userId).orElseThrow();
    }

    private void validate(ContractScheduleRequest request) {
        if (!TYPES.contains(request.scheduleType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 일정 유형입니다.");
        }
        if (request.reminderDaysBefore() > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알림 시점은 일정 1년 전 이내로 설정해 주세요.");
        }
    }
}
