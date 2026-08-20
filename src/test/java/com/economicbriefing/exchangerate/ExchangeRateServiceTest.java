package com.economicbriefing.exchangerate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ExchangeRateServiceTest {

    @Mock ExchangeRateRepository repository;
    @Mock EcosExchangeRateClient client;
    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ExchangeRateService(repository, client);
    }

    @Test
    void usesPreviousBusinessRateAndFirstAvailableRateAfterPeriodBoundary() {
        var friday = rate("2026-08-14", "1370.00");
        var monday = rate("2026-08-17", "1380.00");
        when(repository.findTop2ByBaseCurrencyAndQuoteCurrencyAndSourceOrderByRateDateDesc("USD", "KRW", "BOK_ECOS"))
                .thenReturn(List.of(monday, friday));
        when(repository.findByBaseCurrencyAndQuoteCurrencyAndSourceAndRateDateBetweenOrderByRateDateAsc(
                "USD", "KRW", "BOK_ECOS", LocalDate.of(2026, 7, 17), LocalDate.of(2026, 8, 17)))
                .thenReturn(List.of(friday, monday));

        var response = service.getHistory(SupportedCurrency.USD, ExchangeRatePeriod.M1);

        assertEquals(new BigDecimal("1380.00"), response.latestDailyRate());
        assertEquals(new BigDecimal("1370.00"), response.previousDailyRate());
        assertEquals(new BigDecimal("10.00"), response.dailyChangeAmount());
        assertEquals(new BigDecimal("1370.00"), response.periodStartRate());
        assertEquals(new BigDecimal("1375.00"), response.averageRate());
        assertEquals("WEAK", response.krwTrend());
        assertEquals("STRONG", response.foreignCurrencyTrend());
    }

    @Test
    void fallingUsdKrwMeansStrongKrw() {
        var start = rate("2026-08-10", "1400.00");
        var current = rate("2026-08-17", "1380.00");
        when(repository.findTop2ByBaseCurrencyAndQuoteCurrencyAndSourceOrderByRateDateDesc("USD", "KRW", "BOK_ECOS"))
                .thenReturn(List.of(current, start));
        when(repository.findByBaseCurrencyAndQuoteCurrencyAndSourceAndRateDateBetweenOrderByRateDateAsc(
                "USD", "KRW", "BOK_ECOS", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 17)))
                .thenReturn(List.of(start, current));

        assertEquals("STRONG", service.getHistory(SupportedCurrency.USD, ExchangeRatePeriod.W1).krwTrend());
    }

    @Test
    void savesBulkEcosRates() {
        LocalDate date = LocalDate.of(2026, 8, 18);
        when(client.fetch(SupportedCurrency.USD, date, date)).thenReturn(List.of(
                new EcosExchangeRateClient.DailyRate(date, new BigDecimal("1380.20"))));
        when(client.fetch(SupportedCurrency.JPY, date, date)).thenReturn(List.of(
                new EcosExchangeRateClient.DailyRate(date, new BigDecimal("920.50"))));
        assertEquals(2, service.collect(date, date));

        var saved = ArgumentCaptor.forClass(ExchangeRateEntity.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(List.of("USD", "JPY"), saved.getAllValues().stream()
                .map(ExchangeRateEntity::getBaseCurrency).toList());

        assertEquals("BOK_ECOS", saved.getAllValues().get(0).getSource());
    }

    private static ExchangeRateEntity rate(String date, String value) {
        return new ExchangeRateEntity(LocalDate.parse(date), SupportedCurrency.USD, new BigDecimal(value), "BOK_ECOS");
    }
}
