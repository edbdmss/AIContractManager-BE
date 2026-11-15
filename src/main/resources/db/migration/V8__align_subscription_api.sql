ALTER TABLE contracts RENAME COLUMN auto_renewal_date TO next_payment_date;
ALTER TABLE payment_detection_events MODIFY COLUMN source_package VARCHAR(255) NULL;
ALTER TABLE contract_suggestions ADD COLUMN next_payment_date DATE NULL;
UPDATE contract_suggestions s
SET next_payment_date = (SELECT DATE_ADD(MAX(e.paid_at), INTERVAL 1 MONTH)
    FROM payment_detection_events e WHERE e.user_id=s.user_id AND e.merchant_name=s.merchant_name AND e.amount=s.estimated_amount);
