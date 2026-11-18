ALTER TABLE contracts
    ADD COLUMN renewal_cycle_months INT NULL,
    ADD CONSTRAINT chk_contract_renewal_cycle CHECK
        (renewal_cycle_months IS NULL OR renewal_cycle_months BETWEEN 1 AND 60);

-- Existing contracts remain unset until the user selects a renewal cycle.

CREATE INDEX idx_contracts_user_expiry ON contracts(user_id, contract_end_date, deleted_at);
