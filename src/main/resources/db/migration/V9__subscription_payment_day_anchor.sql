ALTER TABLE contracts
    ADD COLUMN payment_day_of_month TINYINT UNSIGNED NULL;

UPDATE contracts
SET payment_day_of_month = DAY(next_payment_date)
WHERE category = 'SUBSCRIPTION'
  AND next_payment_date IS NOT NULL;

ALTER TABLE contracts
    ADD CONSTRAINT chk_contracts_payment_day_of_month
        CHECK (payment_day_of_month IS NULL OR payment_day_of_month BETWEEN 1 AND 31);
