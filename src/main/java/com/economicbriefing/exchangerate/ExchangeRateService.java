package com.economicbriefing.exchangerate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ExchangeRateService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final BigDecimal NEUTRAL_PERCENT = new BigDecimal("0.1");
    private static final String QUOTE = "KRW";
    private static final String SOURCE = "BOK_ECOS";
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateRepository repository;
    private final EcosExchangeRateClient client;
    private final AtomicBoolean backfillRunning = new AtomicBoolean();

    public ExchangeRateService(ExchangeRateRepository repository, EcosExchangeRateClient client) {
        this.repository = repository;
        this.client = client;
    }

    public int collect(LocalDate from, LocalDate to) {
        int saved = 0;
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            for (var daily : client.fetch(currency, from, to)) {
                var existing = repository.findByRateDateAndBaseCurrencyAndQuoteCurrencyAndSource(
                        daily.date(), currency.name(), QUOTE, SOURCE);
                if (existing.isPresent()) {
                    existing.get().updateRate(daily.rate());
                    repository.save(existing.get());
                } else {
                    try {
                        repository.save(new ExchangeRateEntity(daily.date(), currency, daily.rate(), SOURCE));
                        saved++;
                    } catch (DataIntegrityViolationException ignored) {
                        log.info("[ExchangeRate] ECOS duplicate date={} currency={}", daily.date(), currency);
                    }
                }
            }
        }
        return saved;
    }

    public boolean startOneYearBackfill() {
        if (!backfillRunning.compareAndSet(false, true)) return false;
        Thread.startVirtualThread(() -> {
            try {
                backfillOneYear(LocalDate.now(KST));
            } catch (Exception e) {
                log.error("[ExchangeRate] Initial backfill failed", e);
            } finally {
                backfillRunning.set(false);
            }
        });
        return true;
    }

    void backfillOneYear(LocalDate end) {
        log.info("[ExchangeRate] Initial backfill started from={} to={}", end.minusYears(1), end);
        int inserted = collect(end.minusYears(1), end);
        log.info("[ExchangeRate] Initial backfill finished inserted={}", inserted);
    }

    public ExchangeRateResponse getHistory(SupportedCurrency currency, ExchangeRatePeriod period) {
        String base = currency.name();
        List<ExchangeRateEntity> latest = repository
                .findTop2ByBaseCurrencyAndQuoteCurrencyAndSourceOrderByRateDateDesc(base, QUOTE, SOURCE);
        if (latest.isEmpty()) throw new ExchangeRateNotReadyException();

        ExchangeRateEntity current = latest.get(0);
        BigDecimal previous = latest.size() > 1 ? latest.get(1).getRate() : current.getRate();
        List<ExchangeRateEntity> entities = repository
                .findByBaseCurrencyAndQuoteCurrencyAndSourceAndRateDateBetweenOrderByRateDateAsc(
                        base, QUOTE, SOURCE, period.startFrom(current.getRateDate()), current.getRateDate());
        if (entities.isEmpty()) entities = List.of(current);

        BigDecimal rate = current.getRate();
        BigDecimal start = entities.get(0).getRate();
        BigDecimal average = entities.stream().map(ExchangeRateEntity::getRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(entities.size()), 2, RoundingMode.HALF_UP);
        BigDecimal periodChange = percent(rate.subtract(start), start);
        BigDecimal averageDifference = rate.subtract(average).setScale(2, RoundingMode.HALF_UP);
        String krwTrend = periodChange.abs().compareTo(NEUTRAL_PERCENT) < 0
                ? "NEUTRAL" : periodChange.signum() > 0 ? "WEAK" : "STRONG";
        String foreignTrend = "NEUTRAL".equals(krwTrend)
                ? "NEUTRAL" : "WEAK".equals(krwTrend) ? "STRONG" : "WEAK";

        return new ExchangeRateResponse(
                currency.name(), currency.displayName(), currency.name() + "/KRW",
                currency.unit(), currency.unitLabel(), currency.flag(), current.getRateDate(), rate, previous,
                rate.subtract(previous).setScale(2, RoundingMode.HALF_UP), percent(rate.subtract(previous), previous),
                period.apiValue(), start, periodChange, average, averageDifference,
                percent(averageDifference, average), krwTrend, foreignTrend,
                entities.stream().map(e -> new ExchangeRateResponse.HistoryPoint(e.getRateDate(), e.getRate())).toList());
    }

    private static BigDecimal percent(BigDecimal difference, BigDecimal base) {
        if (base.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return difference.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    public static class ExchangeRateNotReadyException extends RuntimeException {}
}
