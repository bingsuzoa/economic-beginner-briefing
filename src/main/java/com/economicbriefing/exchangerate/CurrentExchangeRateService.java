package com.economicbriefing.exchangerate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentExchangeRateService {
    private static final String QUOTE = "KRW";
    private final CurrentExchangeRateRepository repository;
    private final FixerExchangeRateClient client;

    public CurrentExchangeRateService(CurrentExchangeRateRepository repository, FixerExchangeRateClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Transactional
    public int refresh() {
        var snapshot = client.fetchLatest();
        var sourceTime = snapshot.sourceTimestamp().atOffset(ZoneOffset.UTC);
        var fetchedAt = OffsetDateTime.now(ZoneOffset.UTC);
        snapshot.rates().forEach((currency, rate) -> {
            var entity = repository.findByBaseCurrencyAndQuoteCurrency(currency.name(), QUOTE)
                    .orElseGet(() -> new CurrentExchangeRateEntity(currency, rate, sourceTime, fetchedAt));
            entity.update(rate, sourceTime, fetchedAt);
            repository.save(entity);
        });
        return snapshot.rates().size();
    }

    public CurrentExchangeRateResponse get(SupportedCurrency currency) {
        var entity = repository.findByBaseCurrencyAndQuoteCurrency(currency.name(), QUOTE)
                .orElseThrow(ExchangeRateService.ExchangeRateNotReadyException::new);
        return new CurrentExchangeRateResponse(currency.name(), entity.getUnit(), currency.unitLabel(),
                entity.getRate(), entity.getSource(), entity.getSourceTimestamp());
    }
}
