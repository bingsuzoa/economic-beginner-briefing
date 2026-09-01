package com.economicbriefing.economicflow;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EventNormalizerTest {
    private final EventNormalizer normalizer = new EventNormalizer();

    @Test
    void normalizesMoneyPercentAndCapitalArea() {
        assertEquals("500000000", normalizer.normalizeValue("5억원").value());
        assertEquals("500000000", normalizer.normalizeValue("5억 원").value());
        assertEquals("500000000", normalizer.normalizeValue("500,000,000원").value());
        assertEquals("2.5", normalizer.normalizeValue("2.50 %").value());
        assertEquals(ValueUnit.PERCENT, normalizer.normalizeValue("2.50 %").unit());
        assertEquals("CAPITAL_AREA", normalizer.normalizeRegion("서울·경기·인천"));
        assertEquals("CAPITAL_AREA", normalizer.normalizeRegion("수도권"));
    }

    @Test
    void preservesFxPairAndRangeBandMeaning() {
        var usd = normalizer.normalizeValue("USD_KRW", "1,382.4원");
        assertEquals("1382.4", usd.value());
        assertEquals(ValueUnit.FX_RATE, usd.unit());
        assertEquals(ValueType.EXACT, usd.valueType());
        assertEquals("USD", usd.baseCurrency());
        assertEquals("KRW", usd.quoteCurrency());
        assertEquals(1, usd.baseAmount());

        var jpy = normalizer.normalizeValue("JPY_KRW", "869.72원");
        assertEquals(ValueUnit.FX_RATE, jpy.unit());
        assertEquals("JPY", jpy.baseCurrency());
        assertEquals(100, jpy.baseAmount());

        var band = normalizer.normalizeValue("USD_KRW", "1,370원대");
        assertEquals("1370", band.value());
        assertEquals(ValueUnit.FX_RATE, band.unit());
        assertEquals(ValueType.RANGE_BAND, band.valueType());
        assertNotEquals(ValueType.EXACT, band.valueType());
    }
}
