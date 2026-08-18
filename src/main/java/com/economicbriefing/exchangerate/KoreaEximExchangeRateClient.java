package com.economicbriefing.exchangerate;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KoreaEximExchangeRateClient {

    private static final Logger log = LoggerFactory.getLogger(KoreaEximExchangeRateClient.class);
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ExchangeRateProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public KoreaEximExchangeRateClient(ExchangeRateProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build());
    }

    KoreaEximExchangeRateClient(
            ExchangeRateProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public Map<SupportedCurrency, BigDecimal> fetchRates(LocalDate date) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new ExchangeRateFetchException("KOREA_EXIM_API_KEY is not configured");
        }

        log.info("[ExchangeRate] API call started date={}", date);
        try {
            var request = HttpRequest.newBuilder()
                    .uri(requestUri(date))
                    .timeout(properties.timeout())
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ExchangeRateFetchException("Korea Exim API returned HTTP " + response.statusCode());
            }

            Map<SupportedCurrency, BigDecimal> rates = parseRates(response.body());
            log.info("[ExchangeRate] API call succeeded date={} currencies={}", date, rates.keySet());
            return rates;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeRateFetchException("Korea Exim API call interrupted", e);
        } catch (IOException e) {
            throw new ExchangeRateFetchException("Korea Exim API call failed", e);
        }
    }

    Map<SupportedCurrency, BigDecimal> parseRates(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (root == null || root.isNull() || (root.isArray() && root.isEmpty())) {
            return Map.of();
        }
        if (!root.isArray()) {
            throw new ExchangeRateFetchException("Unexpected Korea Exim API response");
        }

        log.info("[ExchangeRate] API response count={}", root.size());
        Map<SupportedCurrency, BigDecimal> rates = new EnumMap<>(SupportedCurrency.class);
        for (JsonNode item : root) {
            int result = item.path("result").asInt(1);
            if (result != 1) {
                throw new ExchangeRateFetchException("Korea Exim API result code=" + result);
            }
            for (SupportedCurrency currency : SupportedCurrency.values()) {
                if (currency.apiCode().equals(item.path("cur_unit").asText())) {
                    String rawRate = item.path("deal_bas_r").asText();
                    if (rawRate.isBlank()) {
                        throw new ExchangeRateFetchException(currency.name() + " deal_bas_r is empty");
                    }
                    rates.put(currency, new BigDecimal(rawRate.replace(",", "")));
                }
            }
        }
        return rates;
    }

    private URI requestUri(LocalDate date) {
        String query = "authkey=" + encode(properties.apiKey())
                + "&searchdate=" + API_DATE.format(date)
                + "&data=AP01";
        return URI.create(properties.apiUrl() + "?" + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static class ExchangeRateFetchException extends RuntimeException {
        public ExchangeRateFetchException(String message) { super(message); }
        public ExchangeRateFetchException(String message, Throwable cause) { super(message, cause); }
    }
}
