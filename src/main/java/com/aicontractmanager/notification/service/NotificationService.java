package com.aicontractmanager.notification.service;

import com.aicontractmanager.notification.domain.Notification;
import com.aicontractmanager.notification.repository.NotificationRepository;
import com.aicontractmanager.schedule.dto.ContractScheduleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public List<Notification> list(UUID userId, boolean unreadOnly) {
        return repository.findByUser(userId, unreadOnly);
    }

    @Transactional
    public Notification markRead(UUID userId, UUID notificationId) {
        repository.findOwned(notificationId, userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."));
        repository.markRead(notificationId, userId);
        return repository.findOwned(notificationId, userId).orElseThrow();
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId);
    }

    @Transactional
    public void dismiss(UUID userId, UUID notificationId) {
        if (repository.findOwned(notificationId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.");
        }
        repository.dismiss(notificationId, userId);
    }

    @Transactional
    public int dismissAll(UUID userId) {
        return repository.dismissAll(userId);
    }

    public void cancelPendingScheduleReminders(UUID scheduleId) {
        repository.cancelPendingScheduleReminders(scheduleId);
    }

    public void createScheduleReminder(UUID userId, UUID contractId, UUID scheduleId,
                                       ContractScheduleRequest request) {
        LocalDateTime scheduledAt = request.dueDate().minusDays(request.reminderDaysBefore()).atStartOfDay();
        String body = request.actionDescription() == null || request.actionDescription().isBlank()
                ? request.dueDate() + " 예정된 일정을 확인해 주세요."
                : request.actionDescription();
        repository.createScheduleReminder(UUID.randomUUID(), userId, contractId, scheduleId,
                request.actionTitle(), body, scheduledAt);
    }
}
