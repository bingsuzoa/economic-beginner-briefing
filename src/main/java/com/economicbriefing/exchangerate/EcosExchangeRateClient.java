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
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcosExchangeRateClient {
    static final String TABLE_CODE = "731Y001";
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final ExchangeRateProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public EcosExchangeRateClient(ExchangeRateProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.ecos().timeout()).build());
    }

    EcosExchangeRateClient(ExchangeRateProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public List<DailyRate> fetch(SupportedCurrency currency, LocalDate from, LocalDate to) {
        if (properties.ecos().apiKey() == null || properties.ecos().apiKey().isBlank()) {
            throw new ExchangeRateFetchException("ECOS_API_KEY is not configured");
        }
        String item = currency == SupportedCurrency.USD ? "0000001" : "0000002";
        String path = "/StatisticSearch/" + encode(properties.ecos().apiKey())
                + "/json/kr/1/1000/" + TABLE_CODE + "/D/" + API_DATE.format(from) + "/"
                + API_DATE.format(to) + "/" + item + "/";
        try {
            var request = HttpRequest.newBuilder(URI.create(properties.ecos().apiUrl() + path))
                    .timeout(properties.ecos().timeout()).GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new ExchangeRateFetchException("ECOS HTTP " + response.statusCode());
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeRateFetchException("ECOS API call interrupted", e);
        } catch (IOException e) {
            throw new ExchangeRateFetchException("ECOS API call failed", e);
        }
    }

    List<DailyRate> parse(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode result = root.path("RESULT");
        if (!result.isMissingNode()) {
            String code = result.path("CODE").asText();
            if ("INFO-200".equals(code)) return List.of();
            throw new ExchangeRateFetchException("ECOS error code=" + code);
        }
        JsonNode rows = root.path("StatisticSearch").path("row");
        if (!rows.isArray()) throw new ExchangeRateFetchException("Unexpected ECOS response");
        List<DailyRate> rates = new ArrayList<>();
        for (JsonNode row : rows) {
            rates.add(new DailyRate(LocalDate.parse(row.path("TIME").asText(), API_DATE),
                    new BigDecimal(row.path("DATA_VALUE").asText().replace(",", ""))));
        }
        return rates;
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    public record DailyRate(LocalDate date, BigDecimal rate) {}

    public static class ExchangeRateFetchException extends RuntimeException {
        public ExchangeRateFetchException(String message) { super(message); }
        public ExchangeRateFetchException(String message, Throwable cause) { super(message, cause); }
    }
}
