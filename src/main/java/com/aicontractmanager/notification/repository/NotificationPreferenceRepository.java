package com.aicontractmanager.notification.repository;

import com.aicontractmanager.notification.domain.NotificationPreference;
import com.aicontractmanager.common.ApiTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public class NotificationPreferenceRepository {
    private final JdbcTemplate jdbc;

    public NotificationPreferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public NotificationPreference get(UUID userId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(p.enabled, TRUE) AS enabled, p.updated_at
                FROM users u LEFT JOIN user_notification_preferences p ON p.user_id = u.id
                WHERE u.id = ? AND u.deleted_at IS NULL
                """, (rs, row) -> new NotificationPreference(
                rs.getBoolean("enabled"), rs.getTimestamp("updated_at") == null
                ? null : ApiTime.fromDatabase(rs.getObject("updated_at", LocalDateTime.class))), userId.toString());
    }

    public NotificationPreference update(UUID userId, boolean enabled) {
        jdbc.update("""
                INSERT INTO user_notification_preferences(user_id, enabled)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP
                """, userId.toString(), enabled);
        return get(userId);
    }
}
