ALTER TABLE contracts
    ADD COLUMN registration_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL' AFTER source_type;

-- Preserve contracts imported from documents and previously incomplete records.
UPDATE contracts
SET registration_mode = 'DOCUMENT'
WHERE category = 'UNCLASSIFIED'
   OR contract_start_date IS NULL
   OR next_payment_date IS NULL;

ALTER TABLE contracts
    ADD CONSTRAINT chk_contract_registration_mode
        CHECK (registration_mode IN ('MANUAL', 'DOCUMENT')),
    ADD CONSTRAINT chk_manual_contract_required_dates
        CHECK (
            registration_mode = 'DOCUMENT'
            OR status = 'CANCELLED'
            OR (
                next_payment_date IS NOT NULL
                AND (category = 'SUBSCRIPTION' OR contract_start_date IS NOT NULL)
            )
        );
