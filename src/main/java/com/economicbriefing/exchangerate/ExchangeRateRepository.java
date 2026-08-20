package com.economicbriefing.exchangerate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRateEntity, Long> {
    Optional<ExchangeRateEntity> findByRateDateAndBaseCurrencyAndQuoteCurrencyAndSource(
            LocalDate date, String base, String quote, String source);

    List<ExchangeRateEntity> findTop2ByBaseCurrencyAndQuoteCurrencyAndSourceOrderByRateDateDesc(
            String base, String quote, String source);

    List<ExchangeRateEntity> findByBaseCurrencyAndQuoteCurrencyAndSourceAndRateDateBetweenOrderByRateDateAsc(
            String base, String quote, String source, LocalDate from, LocalDate to);
}
