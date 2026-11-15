package com.aicontractmanager.notification.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class DeviceTokenRepository {
    private final JdbcTemplate jdbc;

    public DeviceTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(UUID userId, String token, String platform, String deviceName) {
        jdbc.update("""
                INSERT INTO user_device_tokens (id, user_id, device_token, platform, device_name, active, last_used_at)
                VALUES (?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), platform = VALUES(platform),
                    device_name = VALUES(device_name), active = TRUE, last_used_at = CURRENT_TIMESTAMP
                """, UUID.randomUUID().toString(), userId.toString(), token, platform.toUpperCase(), deviceName);
    }

    public int deactivate(UUID userId, String token) {
        return jdbc.update("UPDATE user_device_tokens SET active = FALSE WHERE user_id = ? AND device_token = ?",
                userId.toString(), token);
    }

    public List<String> findActiveTokens(UUID userId) {
        return jdbc.query("SELECT device_token FROM user_device_tokens WHERE user_id = ? AND active = TRUE",
                (rs, row) -> rs.getString(1), userId.toString());
    }

    public void deactivateToken(String token) {
        jdbc.update("UPDATE user_device_tokens SET active = FALSE WHERE device_token = ?", token);
    }
}
