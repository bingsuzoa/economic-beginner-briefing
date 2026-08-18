package com.economicbriefing.exchangerate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KoreaEximExchangeRateClientTest {

    private final KoreaEximExchangeRateClient client = new KoreaEximExchangeRateClient(
            new ExchangeRateProperties("test", URI.create("https://example.com"),
                    Duration.ofSeconds(1), false, "0 0 12 * * MON-FRI"),
            new ObjectMapper());

    @Test
    void extractsUsdDealBaseRateRegardlessOfArrayOrderAndComma() throws Exception {
        String body = """
                [
                  {"result":1,"cur_unit":"EUR","deal_bas_r":"1,620.10"},
                  {"result":1,"cur_unit":"USD","deal_bas_r":"1,380.20"}
                ]
                """;

        assertEquals("1380.20", client.parseUsdRate(body).orElseThrow().toPlainString());
    }

    @Test
    void emptyResponseIsAValidNonBusinessDay() throws Exception {
        assertEquals(true, client.parseUsdRate("null").isEmpty());
        assertEquals(true, client.parseUsdRate("[]").isEmpty());
    }

    @Test
    void apiErrorCodeFailsFast() {
        assertThrows(KoreaEximExchangeRateClient.ExchangeRateFetchException.class,
                () -> client.parseUsdRate("[{\"result\":4}]"));
    }
}
