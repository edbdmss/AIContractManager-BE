-- Keep DECIMAL precision so fractional legacy values are rejected, not rounded
-- away by conversion to an integer/scale-zero column. Existing invalid rows
-- must be corrected explicitly before this migration can succeed.
ALTER TABLE contracts
    ADD CONSTRAINT chk_contracts_monthly_whole_won
        CHECK (monthly_amount IS NULL OR monthly_amount = TRUNCATE(monthly_amount, 0)),
    ADD CONSTRAINT chk_contracts_penalty_whole_won
        CHECK (penalty_amount IS NULL OR
               (penalty_amount >= 0 AND penalty_amount = TRUNCATE(penalty_amount, 0)));

ALTER TABLE payment_detection_events
    ADD CONSTRAINT chk_detection_whole_won CHECK (amount = TRUNCATE(amount, 0));

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_transactions_whole_won CHECK (amount = TRUNCATE(amount, 0));

ALTER TABLE contract_suggestions
    ADD CONSTRAINT chk_suggestions_whole_won
        CHECK (estimated_amount IS NULL OR estimated_amount = TRUNCATE(estimated_amount, 0));

ALTER TABLE payment_detection_events RENAME COLUMN paid_at TO paid_date;
ALTER TABLE payment_transactions RENAME COLUMN paid_at TO paid_date;

ALTER TABLE contract_schedules ALTER COLUMN reminder_days_before SET DEFAULT 3;
