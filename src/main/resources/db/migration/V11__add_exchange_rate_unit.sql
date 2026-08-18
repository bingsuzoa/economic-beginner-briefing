ALTER TABLE exchange_rates ADD COLUMN unit INTEGER NOT NULL DEFAULT 1;

ALTER TABLE exchange_rates ADD CONSTRAINT ck_exchange_rates_unit_positive CHECK (unit > 0);
