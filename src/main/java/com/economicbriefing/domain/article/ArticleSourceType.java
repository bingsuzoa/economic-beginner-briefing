package com.economicbriefing.domain.article;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ArticleSourceType {
    NEWS_MEDIA,
    GOVERNMENT,
    PUBLIC_INSTITUTION,
    FINANCIAL_INSTITUTION,
    OTHER;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ArticleSourceType fromValue(String value) {
        return valueOf(value.toUpperCase());
    }
}
