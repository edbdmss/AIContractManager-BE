ALTER TABLE payment_detection_events ADD COLUMN client_event_id VARCHAR(100) NULL;
CREATE UNIQUE INDEX uk_detection_client_event ON payment_detection_events(user_id, client_event_id);
ALTER TABLE contract_schedules ADD COLUMN managed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_managed_schedules ON contract_schedules(contract_id, managed, status);
