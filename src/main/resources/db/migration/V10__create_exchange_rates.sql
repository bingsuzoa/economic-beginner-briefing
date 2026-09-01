CREATE TABLE exchange_rates (
    id              BIGSERIAL PRIMARY KEY,
    rate_date       DATE NOT NULL,
    base_currency   VARCHAR(3) NOT NULL,
    quote_currency  VARCHAR(3) NOT NULL,
    rate            NUMERIC(18, 6) NOT NULL,
    source          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exchange_rates_pair_date UNIQUE (rate_date, base_currency, quote_currency)
);

CREATE INDEX idx_exchange_rates_pair_date
    ON exchange_rates (base_currency, quote_currency, rate_date DESC);
