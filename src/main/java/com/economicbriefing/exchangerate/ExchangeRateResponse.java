package com.economicbriefing.exchangerate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExchangeRateResponse(
        String currency,
        String currencyName,
        String pair,
        int unit,
        String unitLabel,
        String flag,
        LocalDate latestDailyRateDate,
        BigDecimal latestDailyRate,
        BigDecimal previousDailyRate,
        BigDecimal dailyChangeAmount,
        BigDecimal dailyChangePercent,
        String period,
        BigDecimal periodStartRate,
        BigDecimal periodChangePercent,
        BigDecimal averageRate,
        BigDecimal differenceFromAverage,
        BigDecimal differenceFromAveragePercent,
        String krwTrend,
        String foreignCurrencyTrend,
        List<HistoryPoint> history
) {
    public record HistoryPoint(LocalDate date, BigDecimal rate) {}
}
