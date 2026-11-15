-- Payment notification parsers provide the calendar date only; time-of-day is not collected.
ALTER TABLE payment_detection_events
    MODIFY COLUMN paid_at DATE NOT NULL;

ALTER TABLE payment_transactions
    MODIFY COLUMN paid_at DATE NOT NULL;
