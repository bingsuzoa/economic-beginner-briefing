ALTER TABLE exchange_rates DROP CONSTRAINT uq_exchange_rates_pair_date;
ALTER TABLE exchange_rates ADD CONSTRAINT uq_exchange_rates_pair_date_source
    UNIQUE (rate_date, base_currency, quote_currency, source);

CREATE TABLE current_exchange_rates (
    id               BIGSERIAL PRIMARY KEY,
    base_currency    VARCHAR(3) NOT NULL,
    quote_currency   VARCHAR(3) NOT NULL,
    unit             INTEGER NOT NULL,
    rate             NUMERIC(18, 6) NOT NULL,
    source           VARCHAR(32) NOT NULL,
    source_timestamp TIMESTAMPTZ NOT NULL,
    fetched_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_current_exchange_rates_pair UNIQUE (base_currency, quote_currency)
);
