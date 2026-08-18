package com.economicbriefing.exchangerate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ExchangeRateServiceTest {

    @Mock ExchangeRateRepository repository;
    @Mock KoreaEximExchangeRateClient client;
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
        when(repository.findTop2ByBaseCurrencyAndQuoteCurrencyOrderByRateDateDesc("USD", "KRW"))
                .thenReturn(List.of(monday, friday));
        when(repository.findByBaseCurrencyAndQuoteCurrencyAndRateDateBetweenOrderByRateDateAsc(
                "USD", "KRW", LocalDate.of(2026, 7, 17), LocalDate.of(2026, 8, 17)))
                .thenReturn(List.of(friday, monday));

        var response = service.getUsdKrw(ExchangeRatePeriod.M1);

        assertEquals(new BigDecimal("1370.00"), response.previousRate());
        assertEquals(new BigDecimal("10.00"), response.changeAmount());
        assertEquals(new BigDecimal("1375.00"), response.averageRate());
        assertEquals("KRW_WEAK", response.trend());
    }

    @Test
    void fallingUsdKrwMeansStrongKrw() {
        var start = rate("2026-08-10", "1400.00");
        var current = rate("2026-08-17", "1380.00");
        when(repository.findTop2ByBaseCurrencyAndQuoteCurrencyOrderByRateDateDesc("USD", "KRW"))
                .thenReturn(List.of(current, start));
        when(repository.findByBaseCurrencyAndQuoteCurrencyAndRateDateBetweenOrderByRateDateAsc(
                "USD", "KRW", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 17)))
                .thenReturn(List.of(start, current));

        assertEquals("KRW_STRONG", service.getUsdKrw(ExchangeRatePeriod.W1).trend());
    }

    @Test
    void savesParsedRateAndSkipsMissingOrDuplicateData() {
        LocalDate date = LocalDate.of(2026, 8, 18);
        when(client.fetchUsdRate(date)).thenReturn(Optional.of(new BigDecimal("1380.20")));
        assertTrue(service.collect(date));

        var saved = ArgumentCaptor.forClass(ExchangeRateEntity.class);
        verify(repository).save(saved.capture());
        assertEquals(new BigDecimal("1380.20"), saved.getValue().getRate());

        LocalDate holiday = date.minusDays(3);
        when(client.fetchUsdRate(holiday)).thenReturn(Optional.empty());
        assertFalse(service.collect(holiday));

        LocalDate duplicate = date.minusDays(1);
        when(repository.existsByRateDateAndBaseCurrencyAndQuoteCurrency(duplicate, "USD", "KRW"))
                .thenReturn(true);
        assertFalse(service.collect(duplicate));
        verify(client, never()).fetchUsdRate(duplicate);
    }

    private static ExchangeRateEntity rate(String date, String value) {
        return new ExchangeRateEntity(LocalDate.parse(date), new BigDecimal(value));
    }
}
