package com.economicbriefing.exchangerate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "current_exchange_rates", uniqueConstraints = @UniqueConstraint(
        name = "uq_current_exchange_rates_pair", columnNames = {"base_currency", "quote_currency"}))
public class CurrentExchangeRateEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;
    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;
    @Column(nullable = false)
    private int unit;
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;
    @Column(nullable = false, length = 32)
    private String source;
    @Column(name = "source_timestamp", nullable = false)
    private OffsetDateTime sourceTimestamp;
    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;

    protected CurrentExchangeRateEntity() {}

    public CurrentExchangeRateEntity(SupportedCurrency currency, BigDecimal rate,
            OffsetDateTime sourceTimestamp, OffsetDateTime fetchedAt) {
        this.baseCurrency = currency.name();
        this.quoteCurrency = "KRW";
        this.unit = currency.unit();
        update(rate, sourceTimestamp, fetchedAt);
    }

    public void update(BigDecimal rate, OffsetDateTime sourceTimestamp, OffsetDateTime fetchedAt) {
        this.rate = rate;
        this.source = "FIXER";
        this.sourceTimestamp = sourceTimestamp;
        this.fetchedAt = fetchedAt;
    }

    public String getBaseCurrency() { return baseCurrency; }
    public int getUnit() { return unit; }
    public BigDecimal getRate() { return rate; }
    public String getSource() { return source; }
    public OffsetDateTime getSourceTimestamp() { return sourceTimestamp; }
}
