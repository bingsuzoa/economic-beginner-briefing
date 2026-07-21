package com.economicbriefing.domain.article;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NewsCategory {
    INTEREST_RATE,
    DEPOSIT_SAVING,
    LOAN,
    HOUSING,
    JEONSE_MONTHLY_RENT,
    SUBSCRIPTION,
    TAX,
    PENSION,
    INSURANCE,
    COST_OF_LIVING,
    EXCHANGE_RATE,
    INVESTMENT,
    GOVERNMENT_SUPPORT,
    EMPLOYMENT_INCOME,
    HOUSEHOLD_DEBT,
    OTHER;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static NewsCategory fromValue(String value) {
        return valueOf(value.toUpperCase());
    }
}
