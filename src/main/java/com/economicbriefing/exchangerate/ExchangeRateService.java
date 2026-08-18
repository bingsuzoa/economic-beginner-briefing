package com.economicbriefing.exchangerate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
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
    private static final String BASE = "USD";
    private static final String QUOTE = "KRW";
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateRepository repository;
    private final KoreaEximExchangeRateClient client;
    private final AtomicBoolean backfillRunning = new AtomicBoolean();

    public ExchangeRateService(ExchangeRateRepository repository, KoreaEximExchangeRateClient client) {
        this.repository = repository;
        this.client = client;
    }

    public boolean collect(LocalDate date) {
        if (repository.existsByRateDateAndBaseCurrencyAndQuoteCurrency(date, BASE, QUOTE)) {
            log.info("[ExchangeRate] DB SKIP date={} reason=already-exists", date);
            return false;
        }

        return client.fetchUsdRate(date).map(rate -> {
            try {
                repository.save(new ExchangeRateEntity(date, rate));
                log.info("[ExchangeRate] DB INSERT date={} rate={}", date, rate);
                return true;
            } catch (DataIntegrityViolationException e) {
                log.info("[ExchangeRate] DB SKIP date={} reason=concurrent-duplicate", date);
                return false;
            }
        }).orElseGet(() -> {
            log.info("[ExchangeRate] Non-business-day SKIP date={}", date);
            return false;
        });
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
            if (collect(date)) inserted++; else skipped++;
        }
        log.info("[ExchangeRate] Initial backfill finished inserted={} skipped={}", inserted, skipped);
    }

    public ExchangeRateResponse getUsdKrw(ExchangeRatePeriod period) {
        List<ExchangeRateEntity> latest = repository
                .findTop2ByBaseCurrencyAndQuoteCurrencyOrderByRateDateDesc(BASE, QUOTE);
        if (latest.isEmpty()) throw new ExchangeRateNotReadyException();

        ExchangeRateEntity current = latest.get(0);
        BigDecimal previous = latest.size() > 1 ? latest.get(1).getRate() : current.getRate();
        List<ExchangeRateEntity> entities = repository
                .findByBaseCurrencyAndQuoteCurrencyAndRateDateBetweenOrderByRateDateAsc(
                        BASE, QUOTE, period.startFrom(current.getRateDate()), current.getRateDate());
        if (entities.isEmpty()) entities = List.of(current);

        BigDecimal rate = current.getRate();
        BigDecimal start = entities.get(0).getRate();
        BigDecimal average = entities.stream().map(ExchangeRateEntity::getRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(entities.size()), 2, RoundingMode.HALF_UP);
        BigDecimal periodChange = percent(rate.subtract(start), start);
        BigDecimal averageDifference = rate.subtract(average).setScale(2, RoundingMode.HALF_UP);
        String trend = periodChange.abs().compareTo(NEUTRAL_PERCENT) < 0
                ? "KRW_NEUTRAL" : periodChange.signum() > 0 ? "KRW_WEAK" : "KRW_STRONG";

        return new ExchangeRateResponse(
                "USD/KRW", current.getRateDate(), rate, previous,
                rate.subtract(previous).setScale(2, RoundingMode.HALF_UP), percent(rate.subtract(previous), previous),
                period.apiValue(), start, periodChange, average, averageDifference,
                percent(averageDifference, average), trend,
                entities.stream().map(e -> new ExchangeRateResponse.HistoryPoint(e.getRateDate(), e.getRate())).toList());
    }

    private static BigDecimal percent(BigDecimal difference, BigDecimal base) {
        if (base.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return difference.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    public static class ExchangeRateNotReadyException extends RuntimeException {}
}
