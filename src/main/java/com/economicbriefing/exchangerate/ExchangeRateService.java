package com.economicbriefing.exchangerate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateRepository repository;
    private final KoreaEximExchangeRateClient client;
    private final AtomicBoolean backfillRunning = new AtomicBoolean();

    public ExchangeRateService(ExchangeRateRepository repository, KoreaEximExchangeRateClient client) {
        this.repository = repository;
        this.client = client;
    }

    public int collect(LocalDate date) {
        List<SupportedCurrency> missing = Arrays.stream(SupportedCurrency.values())
                .filter(currency -> !repository.existsByRateDateAndBaseCurrencyAndQuoteCurrency(
                        date, currency.name(), QUOTE))
                .toList();
        if (missing.isEmpty()) {
            log.info("[ExchangeRate] DB SKIP date={} reason=all-currencies-exist", date);
            return 0;
        }

        Map<SupportedCurrency, BigDecimal> rates = client.fetchRates(date);
        if (rates.isEmpty()) {
            log.info("[ExchangeRate] Non-business-day SKIP date={}", date);
            return 0;
        }

        int inserted = 0;
        for (SupportedCurrency currency : missing) {
            BigDecimal rate = rates.get(currency);
            if (rate == null) {
                log.info("[ExchangeRate] Currency SKIP date={} currency={} reason=no-data", date, currency);
                continue;
            }
            try {
                repository.save(new ExchangeRateEntity(date, currency, rate));
                inserted++;
                log.info("[ExchangeRate] DB INSERT date={} currency={} unit={} rate={}",
                        date, currency, currency.unit(), rate);
            } catch (DataIntegrityViolationException e) {
                log.info("[ExchangeRate] DB SKIP date={} currency={} reason=concurrent-duplicate",
                        date, currency);
            }
        }
        return inserted;
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
        int inserted = 0;
        int skipped = 0;
        log.info("[ExchangeRate] Initial backfill started from={} to={}", end.minusYears(1), end);
        for (LocalDate date = end.minusYears(1); !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                skipped++;
                continue;
            }
            int saved = collect(date);
            inserted += saved;
            if (saved == 0) skipped++;
        }
        log.info("[ExchangeRate] Initial backfill finished inserted={} skipped={}", inserted, skipped);
    }

    public ExchangeRateResponse getRate(SupportedCurrency currency, ExchangeRatePeriod period) {
        String base = currency.name();
        List<ExchangeRateEntity> latest = repository
                .findTop2ByBaseCurrencyAndQuoteCurrencyOrderByRateDateDesc(base, QUOTE);
        if (latest.isEmpty()) throw new ExchangeRateNotReadyException();

        ExchangeRateEntity current = latest.get(0);
        BigDecimal previous = latest.size() > 1 ? latest.get(1).getRate() : current.getRate();
        List<ExchangeRateEntity> entities = repository
                .findByBaseCurrencyAndQuoteCurrencyAndRateDateBetweenOrderByRateDateAsc(
                        base, QUOTE, period.startFrom(current.getRateDate()), current.getRateDate());
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
