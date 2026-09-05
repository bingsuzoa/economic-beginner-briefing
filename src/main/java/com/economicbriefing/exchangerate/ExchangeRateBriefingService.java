package com.economicbriefing.exchangerate;

import com.economicbriefing.classifier.entity.ArticleEntity;
import com.economicbriefing.classifier.repository.ArticlePresentationRepository;
import com.economicbriefing.classifier.repository.ArticleRepository;
import com.economicbriefing.economicflow.entity.EventRelationEvidenceEntity;
import com.economicbriefing.economicflow.repository.EventRelationEvidenceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeRateBriefingService {
    private static final int FRESHNESS_HOURS = 24;
    private final ArticlePresentationRepository presentations;
    private final ArticleRepository articles;
    private final EventRelationEvidenceRepository relationEvidence;
    private final ObjectMapper json;

    public ExchangeRateBriefingService(ArticlePresentationRepository presentations, ArticleRepository articles,
            EventRelationEvidenceRepository relationEvidence, ObjectMapper json) {
        this.presentations = presentations;
        this.articles = articles;
        this.relationEvidence = relationEvidence;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ExchangeRateBriefingResponse find(SupportedCurrency currency) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(FRESHNESS_HOURS);
        Set<String> seen = new HashSet<>();
        List<Candidate> matches = new ArrayList<>();
        for (var presentation : presentations.findByCreatedAtAfterOrderByCreatedAtDesc(cutoff)) {
            if (!seen.add(presentation.getArticleId())) continue;
            ArticleEntity article = articles.findById(presentation.getArticleId()).orElse(null);
            if (article == null || publishedAt(article).isBefore(cutoff) || !matches(currency, article)) continue;
            try {
                JsonNode node = json.readTree(presentation.getPresentationJson());
                String explanation = explanation(node);
                if (!explanation.isBlank()) matches.add(new Candidate(article, node, explanation));
            } catch (Exception ignored) {
                // Malformed historical presentation is not displayable.
            }
        }
        return matches.stream().max(Comparator.comparing(candidate -> publishedAt(candidate.article())))
                .map(candidate -> response(currency, candidate)).orElse(null);
    }

    private ExchangeRateBriefingResponse response(SupportedCurrency currency, Candidate candidate) {
        ArticleEntity article = candidate.article();
        String title = text(candidate.presentation(), "displayTitle");
        if (title.isBlank()) title = article.getTitle();
        return new ExchangeRateBriefingResponse(currency.name(), article.getId(), title, candidate.explanation(),
                flow(currency, article.getId()), article.getSource(), publishedAt(article), article.getUrl());
    }

    private List<ExchangeRateBriefingResponse.Flow> flow(SupportedCurrency currency, String articleId) {
        List<ExchangeRateBriefingResponse.Flow> result = new ArrayList<>();
        for (EventRelationEvidenceEntity evidence : relationEvidence.findByArticleId(articleId)) {
            var relation = evidence.getRelation();
            if (relation == null || relation.getFromEvent() == null || relation.getToEvent() == null) continue;
            String from = relation.getFromEvent().getTitle();
            String to = relation.getToEvent().getTitle();
            if (!matches(currency, from + " " + to)) continue;
            var item = new ExchangeRateBriefingResponse.Flow(from, to, relation.getRelationType().name());
            if (!result.contains(item)) result.add(item);
            if (result.size() == 3) break;
        }
        return List.copyOf(result);
    }

    private boolean matches(SupportedCurrency currency, ArticleEntity article) {
        String category = safe(article.getCategory()).toLowerCase(Locale.ROOT);
        if (!category.isBlank() && !contains(category, "exchange", "investment", "interest", "cost", "economy", "finance", "경제", "금융", "환율", "투자", "금리", "물가")) return false;
        return matches(currency, String.join(" ", safe(article.getTitle()), safe(article.getBody()), safe(article.getCategory())));
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

    private static String explanation(JsonNode presentation) {
        JsonNode why = presentation.path("whyExplanations");
        if (why.isArray()) for (JsonNode item : why) {
            String explanation = text(item, "explanation");
            if (!explanation.isBlank()) return explanation;
        }
        String happened = text(presentation, "whatHappened");
        if (!happened.isBlank()) return happened;
        JsonNode summary = presentation.path("summary");
        if (!summary.isArray()) return "";
        List<String> lines = new ArrayList<>();
        for (JsonNode item : summary) if (!item.asText().isBlank()) lines.add(item.asText());
        return String.join(" ", lines);
    }

    private static String text(JsonNode node, String field) { return safe(node.path(field).asText()); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static OffsetDateTime publishedAt(ArticleEntity article) {
        return article.getPublishedAt() != null ? article.getPublishedAt() : article.getCollectedAt();
    }
    private record Candidate(ArticleEntity article, JsonNode presentation, String explanation) {}
}
