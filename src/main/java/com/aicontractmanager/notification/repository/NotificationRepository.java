package com.aicontractmanager.notification.repository;

import com.aicontractmanager.notification.domain.Notification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.aicontractmanager.notification.domain.PushNotification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<Notification> mapper = this::map;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void createScheduleReminder(UUID id, UUID userId, UUID contractId, UUID scheduleId,
                                       String title, String body, LocalDateTime scheduledAt) {
        jdbc.update("""
                INSERT INTO notifications
                    (id, user_id, contract_id, schedule_id, notification_type, title, body, scheduled_at)
                VALUES (?, ?, ?, ?, 'SCHEDULE_REMINDER', ?, ?, ?)
                """, id.toString(), userId.toString(), contractId.toString(), scheduleId.toString(),
                title, body, scheduledAt);
    }

    public List<Notification> findByUser(UUID userId, boolean unreadOnly) {
        String sql = """
                SELECT id, contract_id, schedule_id, suggestion_id, notification_type, title, body,
                       scheduled_at, sent_at, read_at, created_at
                FROM notifications WHERE user_id = ? AND scheduled_at <= CURRENT_TIMESTAMP
                  AND dispatch_status <> 'CANCELLED'
                """ + (unreadOnly ? " AND read_at IS NULL" : "") + " ORDER BY created_at DESC";
        return jdbc.query(sql, mapper, userId.toString());
    }

    public Optional<Notification> findOwned(UUID notificationId, UUID userId) {
        return jdbc.query("""
                SELECT id, contract_id, schedule_id, suggestion_id, notification_type, title, body,
                       scheduled_at, sent_at, read_at, created_at
                FROM notifications WHERE id = ? AND user_id = ?
                """, mapper, notificationId.toString(), userId.toString()).stream().findFirst();
    }

    public int markRead(UUID notificationId, UUID userId) {
        return jdbc.update("""
                UPDATE notifications SET read_at = CURRENT_TIMESTAMP
                WHERE id = ? AND user_id = ? AND read_at IS NULL
                  AND scheduled_at <= CURRENT_TIMESTAMP AND dispatch_status <> 'CANCELLED'
                """, notificationId.toString(), userId.toString());
    }

    public int markAllRead(UUID userId) {
        return jdbc.update("""
                UPDATE notifications SET read_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND read_at IS NULL AND scheduled_at <= CURRENT_TIMESTAMP
                  AND dispatch_status <> 'CANCELLED'
                """,
                userId.toString());
    }

    public void cancelPendingScheduleReminders(UUID scheduleId) {
        jdbc.update("""
                UPDATE notifications SET dispatch_status = 'CANCELLED', dispatch_claimed_at = NULL
                WHERE schedule_id = ? AND dispatch_status = 'PENDING'
                """, scheduleId.toString());
    }

    @Transactional
    public List<PushNotification> claimDue(int limit) {
        jdbc.update("""
                UPDATE notifications
                SET dispatch_status = 'PENDING', dispatch_claimed_at = NULL
                WHERE dispatch_status = 'SENDING' AND dispatch_claimed_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE)
                """);
        List<PushNotification> due = jdbc.query("""
                SELECT id, user_id, title, body, dispatch_attempts
                FROM notifications
                WHERE dispatch_status = 'PENDING'
                  AND scheduled_at <= CURRENT_TIMESTAMP
                  AND (dispatch_next_attempt_at IS NULL OR dispatch_next_attempt_at <= CURRENT_TIMESTAMP)
                  AND dispatch_attempts < 5
                ORDER BY scheduled_at
                LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new PushNotification(UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")), rs.getString("title"), rs.getString("body"),
                rs.getInt("dispatch_attempts")), limit);
        for (PushNotification notification : due) {
            jdbc.update("""
                    UPDATE notifications SET dispatch_status = 'SENDING', dispatch_claimed_at = CURRENT_TIMESTAMP,
                        dispatch_attempts = dispatch_attempts + 1
                    WHERE id = ? AND dispatch_status = 'PENDING'
                    """, notification.id().toString());
        }
        return due;
    }

    public void markPushSent(UUID id) {
        jdbc.update("""
                UPDATE notifications SET dispatch_status = 'SENT', sent_at = CURRENT_TIMESTAMP,
                    dispatch_claimed_at = NULL, dispatch_error = NULL
                WHERE id = ? AND dispatch_status = 'SENDING'
                """, id.toString());
    }

    public void markPushFailed(UUID id, String error, int attempt) {
        int retryMinutes = Math.min(60, 1 << Math.min(attempt, 6));
        jdbc.update("""
                UPDATE notifications
                SET dispatch_status = ?, dispatch_claimed_at = NULL, dispatch_error = ?,
                    dispatch_next_attempt_at = ?
                WHERE id = ? AND dispatch_status = 'SENDING'
                """, attempt >= 5 ? "FAILED" : "PENDING", truncate(error, 500),
                attempt >= 5 ? null : LocalDateTime.now().plusMinutes(retryMinutes), id.toString());
    }

    public void releaseWithoutDevices(UUID id) {
        jdbc.update("""
                UPDATE notifications SET dispatch_status = 'PENDING', dispatch_claimed_at = NULL,
                    dispatch_attempts = GREATEST(dispatch_attempts - 1, 0)
                WHERE id = ? AND dispatch_status = 'SENDING'
                """, id.toString());
    }

    private String truncate(String value, int max) {
        if (value == null) return "발송 실패";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private Notification map(ResultSet rs, int rowNum) throws SQLException {
        return new Notification(UUID.fromString(rs.getString("id")), uuid(rs, "contract_id"),
                uuid(rs, "schedule_id"), uuid(rs, "suggestion_id"), rs.getString("notification_type"),
                rs.getString("title"), rs.getString("body"), rs.getObject("scheduled_at", LocalDateTime.class),
                rs.getObject("sent_at", LocalDateTime.class), rs.getObject("read_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class));
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }
}
