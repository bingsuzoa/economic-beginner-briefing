package com.economicbriefing.exchangerate;

import com.economicbriefing.briefing.DailyBriefingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeRateBriefingService {
    private static final int FRESHNESS_HOURS = 24;
    private final DailyBriefingRepository briefings;
    private final ObjectMapper json;

    public ExchangeRateBriefingService(DailyBriefingRepository briefings, ObjectMapper json) {
        this.briefings = briefings;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ExchangeRateBriefingResponse find(SupportedCurrency currency) {
        var latest = briefings.findFirstByStatusOrderByFinishedAtDesc("SUCCESS").orElse(null);
        if (latest == null || latest.getFinishedAt() == null
                || latest.getFinishedAt().isBefore(OffsetDateTime.now().minusHours(FRESHNESS_HOURS))) return null;
        try {
            for (JsonNode flow : json.readTree(latest.getResultJson()).path("flows")) {
                String searchable = text(flow, "title") + " " + text(flow, "explanation");
                if (!matches(currency, searchable)) continue;
                JsonNode source = flow.path("sources").path(0);
                return new ExchangeRateBriefingResponse(currency.name(), text(source, "articleId"),
                        text(flow, "title"), text(flow, "explanation"), List.of(), text(source, "source"),
                        parseTime(text(source, "publishedAt")), text(source, "url"));
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean matches(SupportedCurrency currency, String value) {
        String text = safe(value).toLowerCase(Locale.ROOT);
        boolean exchangeContext = contains(text, "환율", "외환", "원/달러", "원달러", "원/엔", "엔/원");
        if (!exchangeContext) return false;
        return currency == SupportedCurrency.USD
                ? contains(text, "원/달러", "원달러", "달러", "usd", "원화")
                : contains(text, "엔화", "엔/원", "원/엔", "jpy", "일본 엔");
    }

    private static boolean contains(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private static String text(JsonNode node, String field) { return safe(node.path(field).asText()); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static OffsetDateTime parseTime(String value) { try { return OffsetDateTime.parse(value); } catch (Exception e) { return null; } }
}
