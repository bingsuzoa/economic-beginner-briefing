package com.economicbriefing.exchangerate;

public enum SupportedCurrency {
    USD("달러", "USD", 1, "1달러", "🇺🇸"),
    JPY("엔", "JPY(100)", 100, "100엔", "🇯🇵");

    private final String displayName;
    private final String apiCode;
    private final int unit;
    private final String unitLabel;
    private final String flag;

    SupportedCurrency(String displayName, String apiCode, int unit, String unitLabel, String flag) {
        this.displayName = displayName;
        this.apiCode = apiCode;
        this.unit = unit;
        this.unitLabel = unitLabel;
        this.flag = flag;
    }

    public String displayName() { return displayName; }
    public String apiCode() { return apiCode; }
    public int unit() { return unit; }
    public String unitLabel() { return unitLabel; }
    public String flag() { return flag; }

    public static SupportedCurrency from(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unsupported currency: " + value);
        }
    }
}
