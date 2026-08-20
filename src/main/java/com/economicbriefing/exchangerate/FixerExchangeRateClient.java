package com.economicbriefing.exchangerate;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FixerExchangeRateClient {
    private final ExchangeRateProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public FixerExchangeRateClient(ExchangeRateProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.fixer().timeout()).build());
    }

    FixerExchangeRateClient(ExchangeRateProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public Snapshot fetchLatest() {
        if (properties.fixer().apiKey() == null || properties.fixer().apiKey().isBlank()) {
            throw new ExchangeRateFetchException("FIXER_API_KEY is not configured");
        }
        try {
            URI uri = URI.create(properties.fixer().apiUrl() + "?access_key="
                    + URLEncoder.encode(properties.fixer().apiKey(), StandardCharsets.UTF_8)
                    + "&symbols=USD,JPY,KRW");
            var request = HttpRequest.newBuilder(uri).timeout(properties.fixer().timeout()).GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new ExchangeRateFetchException("Fixer HTTP " + response.statusCode());
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeRateFetchException("Fixer API call interrupted", e);
        } catch (IOException e) {
            throw new ExchangeRateFetchException("Fixer API call failed", e);
        }
    }

    Snapshot parse(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!root.path("success").asBoolean()) {
            throw new ExchangeRateFetchException("Fixer error code=" + root.path("error").path("code").asText("unknown"));
        }
        if (!"EUR".equals(root.path("base").asText())) {
            throw new ExchangeRateFetchException("Unexpected Fixer base=" + root.path("base").asText());
        }
        BigDecimal usd = requiredRate(root, "USD");
        BigDecimal jpy = requiredRate(root, "JPY");
        BigDecimal krw = requiredRate(root, "KRW");
        Map<SupportedCurrency, BigDecimal> rates = new EnumMap<>(SupportedCurrency.class);
        rates.put(SupportedCurrency.USD, krw.divide(usd, 6, RoundingMode.HALF_UP));
        rates.put(SupportedCurrency.JPY, krw.multiply(BigDecimal.valueOf(100))
                .divide(jpy, 6, RoundingMode.HALF_UP));
        return new Snapshot(Instant.ofEpochSecond(root.path("timestamp").asLong()), rates);
    }

    private static BigDecimal requiredRate(JsonNode root, String currency) {
        String value = root.path("rates").path(currency).asText();
        if (value.isBlank()) throw new ExchangeRateFetchException("Fixer rate missing: " + currency);
        return new BigDecimal(value);
    }

    public record Snapshot(Instant sourceTimestamp, Map<SupportedCurrency, BigDecimal> rates) {}

    public static class ExchangeRateFetchException extends RuntimeException {
        public ExchangeRateFetchException(String message) { super(message); }
        public ExchangeRateFetchException(String message, Throwable cause) { super(message, cause); }
    }
}
