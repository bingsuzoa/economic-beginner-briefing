package com.economicbriefing.exchangerate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CurrentExchangeRateResponse(
        String currency, int unit, String unitLabel, BigDecimal rate,
        String source, OffsetDateTime sourceTimestamp) {}
