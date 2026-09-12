package com.economicbriefing.exchangerate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class FixerExchangeRateClientTest {
    private final FixerExchangeRateClient client = new FixerExchangeRateClient(properties(), new ObjectMapper());

    @Test
    void calculatesKrwCrossRatesFromEurBase() throws Exception {
        var snapshot = client.parse("""
                {"success":true,"timestamp":1787222644,"base":"EUR",
                 "rates":{"USD":1.170316,"JPY":185.321872,"KRW":1631.338522}}
                """);
        assertEquals("1393.929949", snapshot.rates().get(SupportedCurrency.USD).toPlainString());
        assertEquals("880.273065", snapshot.rates().get(SupportedCurrency.JPY).toPlainString());
        assertEquals(1787222644L, snapshot.sourceTimestamp().getEpochSecond());
    }

    static ExchangeRateProperties properties() {
        return new ExchangeRateProperties(
                new ExchangeRateProperties.Fixer("test", URI.create("http://example.com"), Duration.ofSeconds(1), false, "0 0 9,18 * * *"),
                new ExchangeRateProperties.Ecos("test", URI.create("https://example.com"), Duration.ofSeconds(1), false, "0 30 18 * * *"));
    }
}
