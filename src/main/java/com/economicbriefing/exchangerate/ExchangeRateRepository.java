package com.economicbriefing.exchangerate;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRateEntity, Long> {
    boolean existsByRateDateAndBaseCurrencyAndQuoteCurrency(LocalDate date, String base, String quote);

    List<ExchangeRateEntity> findTop2ByBaseCurrencyAndQuoteCurrencyOrderByRateDateDesc(
            String base, String quote);

    List<ExchangeRateEntity> findByBaseCurrencyAndQuoteCurrencyAndRateDateBetweenOrderByRateDateAsc(
            String base, String quote, LocalDate from, LocalDate to);
}
