ALTER TABLE notifications
    ADD COLUMN dismissed_at DATETIME NULL;

CREATE INDEX idx_notifications_user_dismissed
    ON notifications(user_id, dismissed_at, scheduled_at);

CREATE TABLE user_notification_preferences (
    user_id CHAR(36) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
