package com.aicontractmanager.auth.service;

import com.aicontractmanager.document.service.LocalDocumentStorage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AccountDeletionService {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public AccountDeletionService(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional
    public void delete(UUID userId) {
        String id = userId.toString();
        List<String> storageKeys = jdbc.queryForList("""
                SELECT d.storage_key FROM documents d
                JOIN contracts c ON c.id = d.contract_id
                WHERE c.user_id = ?
                """, String.class, id);

        int updated = jdbc.update("""
                UPDATE users SET email = CONCAT('deleted+', id, '@invalid.local'),
                    name = '탈퇴한 사용자', provider = 'DELETED', provider_user_id = NULL,
                    deleted_at = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted_at IS NULL
                """, id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "활성 사용자 계정을 찾을 수 없습니다.");
        }

        jdbc.update("DELETE FROM user_refresh_tokens WHERE user_id = ?", id);
        jdbc.update("DELETE FROM notifications WHERE user_id = ?", id);
        jdbc.update("DELETE FROM contract_suggestions WHERE user_id = ?", id);
        jdbc.update("DELETE FROM payment_transactions WHERE user_id = ?", id);
        jdbc.update("DELETE FROM payment_detection_events WHERE user_id = ?", id);
        jdbc.update("DELETE FROM user_device_tokens WHERE user_id = ?", id);
        jdbc.update("DELETE FROM chat_messages WHERE session_id IN (SELECT id FROM chat_sessions WHERE user_id = ?)", id);
        jdbc.update("DELETE FROM chat_sessions WHERE user_id = ?", id);
        jdbc.update("DELETE FROM contract_schedules WHERE contract_id IN (SELECT id FROM contracts WHERE user_id = ?)", id);
        jdbc.update("DELETE FROM ai_analyses WHERE document_id IN (SELECT d.id FROM documents d JOIN contracts c ON c.id = d.contract_id WHERE c.user_id = ?)", id);
        jdbc.update("""
                UPDATE documents d JOIN contracts c ON c.id = d.contract_id
                SET d.original_filename = 'deleted', d.content_type = NULL, d.file_size = NULL,
                    d.checksum = NULL, d.deleted_at = CURRENT_TIMESTAMP
                WHERE c.user_id = ?
                """, id);
        jdbc.update("""
                UPDATE contracts SET title = '삭제된 계약', category = 'DELETED', provider_name = NULL,
                    status = 'CANCELLED', source_type = 'MANUAL', contract_start_date = NULL,
                    contract_end_date = NULL, auto_renewal_date = NULL, cancellation_deadline = NULL,
                    monthly_amount = NULL, penalty_amount = NULL, cancellation_terms = NULL,
                    plain_summary = NULL, deleted_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND deleted_at IS NULL
                """, id);

        if (!storageKeys.isEmpty()) {
            events.publishEvent(new AccountDocumentsCleanupRequested(storageKeys));
        }
    }

    public record AccountDocumentsCleanupRequested(List<String> storageKeys) {
        public AccountDocumentsCleanupRequested {
            storageKeys = List.copyOf(storageKeys);
        }
    }
}
