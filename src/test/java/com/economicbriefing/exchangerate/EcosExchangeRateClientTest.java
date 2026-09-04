package com.economicbriefing.exchangerate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EcosExchangeRateClientTest {
    private final EcosExchangeRateClient client = new EcosExchangeRateClient(
            FixerExchangeRateClientTest.properties(), new ObjectMapper());

    @Test
    void parsesDailyStatisticRows() throws Exception {
        var rows = client.parse("""
                {"StatisticSearch":{"list_total_count":1,"row":[
                  {"TIME":"20260820","DATA_VALUE":"1,402.5"}
                ]}}
                """);
        assertEquals("2026-08-20", rows.get(0).date().toString());
        assertEquals("1402.5", rows.get(0).rate().toPlainString());
    }
}
