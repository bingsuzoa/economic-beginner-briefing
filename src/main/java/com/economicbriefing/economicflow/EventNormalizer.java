package com.economicbriefing.economicflow;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class EventNormalizer {

    private static final Pattern KRW = Pattern.compile("^([0-9,]+(?:\\.[0-9]+)?)\\s*(억|만)?\\s*원$");
    private static final Pattern KRW_BAND = Pattern.compile("^([0-9,]+)\\s*원대$");
    private static final Pattern PERCENT = Pattern.compile("^([0-9,]+(?:\\.[0-9]+)?)\\s*%$");

    public NormalizedValue normalizeValue(String raw) {
        return normalizeValue(null, raw);
    }

    public NormalizedValue normalizeValue(String subjectKey, String raw) {
        FxPair pair = FxPair.from(subjectKey);
        if (raw == null || raw.isBlank()) return new NormalizedValue(null, null, null,
                pair == null ? null : pair.baseCurrency(), pair == null ? null : pair.quoteCurrency(),
                pair == null ? null : pair.baseAmount());
        String value = raw.trim();
        var band = KRW_BAND.matcher(value);
        if (pair != null && band.matches()) {
            return new NormalizedValue(number(band.group(1)), ValueUnit.FX_RATE, ValueType.RANGE_BAND,
                    pair.baseCurrency(), pair.quoteCurrency(), pair.baseAmount());
        }
        var krw = KRW.matcher(value);
        if (krw.matches()) {
            BigDecimal amount = new BigDecimal(krw.group(1).replace(",", ""));
            if ("억".equals(krw.group(2))) amount = amount.multiply(BigDecimal.valueOf(100_000_000));
            if ("만".equals(krw.group(2))) amount = amount.multiply(BigDecimal.valueOf(10_000));
            return new NormalizedValue(amount.stripTrailingZeros().toPlainString(),
                    pair == null ? ValueUnit.KRW : ValueUnit.FX_RATE, ValueType.EXACT,
                    pair == null ? null : pair.baseCurrency(), pair == null ? null : pair.quoteCurrency(),
                    pair == null ? null : pair.baseAmount());
        }
        var percent = PERCENT.matcher(value);
        if (percent.matches()) {
            return new NormalizedValue(
                    new BigDecimal(percent.group(1).replace(",", "")).stripTrailingZeros().toPlainString(),
                    ValueUnit.PERCENT, ValueType.EXACT, null, null, null);
        }
        return new NormalizedValue(value.replaceAll("\\s+", " "), ValueUnit.TEXT, ValueType.TEXT,
                pair == null ? null : pair.baseCurrency(), pair == null ? null : pair.quoteCurrency(),
                pair == null ? null : pair.baseAmount());
    }

    public String normalizeRegion(String region) {
        if (region == null || region.isBlank()) return null;
        String compact = region.replaceAll("\\s+", "");
        return compact.equals("수도권") || (compact.contains("서울") && compact.contains("경기") && compact.contains("인천"))
                ? "CAPITAL_AREA" : region.trim();
    }

    private static String number(String value) {
        return new BigDecimal(value.replace(",", "")).stripTrailingZeros().toPlainString();
    }

    public record NormalizedValue(String value, ValueUnit unit, ValueType valueType,
            String baseCurrency, String quoteCurrency, Integer baseAmount) {}

    private record FxPair(String baseCurrency, String quoteCurrency, int baseAmount) {
        private static FxPair from(String subjectKey) {
            return switch (subjectKey == null ? "" : subjectKey) {
                case "USD_KRW" -> new FxPair("USD", "KRW", 1);
                case "JPY_KRW" -> new FxPair("JPY", "KRW", 100);
                default -> null;
            };
        }
    }
}
