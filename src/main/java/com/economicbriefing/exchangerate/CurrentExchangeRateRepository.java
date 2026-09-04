package com.economicbriefing.exchangerate;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrentExchangeRateRepository extends JpaRepository<CurrentExchangeRateEntity, Long> {
    Optional<CurrentExchangeRateEntity> findByBaseCurrencyAndQuoteCurrency(String base, String quote);
}
