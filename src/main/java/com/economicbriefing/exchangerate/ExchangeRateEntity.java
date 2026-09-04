package com.economicbriefing.exchangerate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "exchange_rates", uniqueConstraints = @UniqueConstraint(
        name = "uq_exchange_rates_pair_date_source",
        columnNames = {"rate_date", "base_currency", "quote_currency", "source"}))
public class ExchangeRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    @Column(nullable = false)
    private int unit;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ExchangeRateEntity() {}

    public ExchangeRateEntity(LocalDate rateDate, SupportedCurrency currency, BigDecimal rate, String source) {
        this.rateDate = rateDate;
        this.rate = rate;
        this.baseCurrency = currency.name();
        this.quoteCurrency = "KRW";
        this.unit = currency.unit();
        this.source = source;
    }

    public void updateRate(BigDecimal rate) { this.rate = rate; }

    @PrePersist
    void prePersist() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public LocalDate getRateDate() { return rateDate; }
    public String getBaseCurrency() { return baseCurrency; }
    public String getQuoteCurrency() { return quoteCurrency; }
    public BigDecimal getRate() { return rate; }
    public int getUnit() { return unit; }
    public String getSource() { return source; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
