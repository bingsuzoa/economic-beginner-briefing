package com.economicbriefing.exchangerate;

import java.time.LocalDate;

public enum ExchangeRatePeriod {
    W1("1W") { public LocalDate startFrom(LocalDate date) { return date.minusWeeks(1); } },
    M1("1M") { public LocalDate startFrom(LocalDate date) { return date.minusMonths(1); } },
    M3("3M") { public LocalDate startFrom(LocalDate date) { return date.minusMonths(3); } },
    Y1("1Y") { public LocalDate startFrom(LocalDate date) { return date.minusYears(1); } };

    private final String apiValue;

    ExchangeRatePeriod(String apiValue) { this.apiValue = apiValue; }
    public String apiValue() { return apiValue; }
    public abstract LocalDate startFrom(LocalDate date);

    public static ExchangeRatePeriod from(String value) {
        for (var period : values()) {
            if (period.apiValue.equalsIgnoreCase(value)) return period;
        }
        throw new IllegalArgumentException("Unsupported period: " + value);
    }
}
