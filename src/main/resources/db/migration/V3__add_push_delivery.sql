ALTER TABLE notifications
    ADD COLUMN dispatch_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN dispatch_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN dispatch_claimed_at DATETIME NULL,
    ADD COLUMN dispatch_next_attempt_at DATETIME NULL,
    ADD COLUMN dispatch_error VARCHAR(500) NULL;

UPDATE notifications
SET dispatch_status = 'SENT'
WHERE sent_at IS NOT NULL;

CREATE INDEX idx_notifications_dispatch
    ON notifications(dispatch_status, scheduled_at, dispatch_next_attempt_at, dispatch_claimed_at);

CREATE TABLE user_device_tokens (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    device_token VARCHAR(500) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    device_name VARCHAR(100) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_token (device_token),
    INDEX idx_device_tokens_user_active (user_id, active),
    CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
