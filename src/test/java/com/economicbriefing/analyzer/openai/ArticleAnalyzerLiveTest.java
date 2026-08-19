package com.economicbriefing.analyzer.openai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.prompt.ArticleAnalyzerPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.ArticleValidatorPromptBuilder;
import com.economicbriefing.analyzer.dto.ArticleValidationResult;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ARTICLE_ANALYZER_LIVE_TEST", matches = "true")
class ArticleAnalyzerLiveTest {

    private static final String GOLD_ARTICLE_ID = "AKR20260816026000002";
    private static final String DEFAULT_URL =
            "https://www.yna.co.kr/view/AKR20260816026000002?section=economy/all";
    private static final Pattern BODY = Pattern.compile(
            "class=\"story-news article\"(.*?)<p class=\"txt-copyright", Pattern.DOTALL);
    private static final Pattern PARAGRAPH = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL);

    @Test
    void analyzesProvidedYonhapArticle() throws Exception {
        String url = System.getenv().getOrDefault("ARTICLE_ANALYZER_TEST_URL", DEFAULT_URL);
        var idMatch = Pattern.compile("AKR\\d+").matcher(url);
        assertTrue(idMatch.find(), "Yonhap article ID not found in URL");
        String articleId = idMatch.group();
        String html = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        var bodyMatch = BODY.matcher(html);
        assertTrue(bodyMatch.find(), "article body not found");

        StringBuilder content = new StringBuilder();
        var paragraphs = PARAGRAPH.matcher(bodyMatch.group(1));
        while (paragraphs.find()) {
            content.append(paragraphs.group(1).replaceAll("<[^>]+>", " ")).append('\n');
        }
        assertTrue(content.length() > 300, "article body is unexpectedly short");

        Article article = new Article(
                articleId, articleId, "",
                "연합뉴스", ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.parse("2026-08-17T05:45:00+09:00"), OffsetDateTime.now(),
                url, List.of(NewsCategory.TAX, NewsCategory.INVESTMENT), "ko", content.toString());

        ObjectMapper json = new ObjectMapper();
        OpenAiProperties properties = new OpenAiProperties(
                System.getenv("OPENAI_API_KEY"),
                System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o"),
                0.3, Duration.ofSeconds(60), 15);
        OpenAiClient client = new OpenAiClient(properties, json);
        String baselinePath = System.getenv("ARTICLE_ANALYZER_BASELINE_PATH");
        ArticleAnalysisResponse response = baselinePath == null
                ? json.readValue(client.complete(
                        ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT,
                        ArticleAnalyzerPromptBuilder.build(List.of(article)), 0), ArticleAnalysisResponse.class)
                : json.readValue(Path.of(baselinePath).toFile(), ArticleAnalysisResponse.class);
        String itemValidationJson = client.complete(
                ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT,
                ArticleValidatorPromptBuilder.build(
                        List.of(article), json.writeValueAsString(response)), 0);
        String missingReviewJson = client.complete(
                ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT,
                ArticleValidatorPromptBuilder.build(
                        List.of(article), json.writeValueAsString(response)), 0);
        ArticleValidationResult itemValidation = json.readValue(itemValidationJson, ArticleValidationResult.class);
        ArticleValidationResult missingReview = json.readValue(missingReviewJson, ArticleValidationResult.class);
        ArticleValidationResult validation = ArticleValidationMerger.merge(
                List.of(article), response, itemValidation, missingReview);

        assertEquals(1, response.articles().size());
        assertEquals(article.id(), response.articles().get(0).articleId());
        assertFalse(response.articles().get(0).issues().isEmpty());
        assertTrue(response.articles().get(0).issues().stream()
                .allMatch(issue -> !issue.mainFacts().isEmpty()));

        Path analyzerOutput = Path.of("pipeline-debug/article-analyzer-" + articleId + ".json");
        Path validatorOutput = Path.of("pipeline-debug/article-validator-" + articleId + ".json");
        Files.createDirectories(analyzerOutput.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(analyzerOutput.toFile(), response);
        json.writerWithDefaultPrettyPrinter().writeValue(validatorOutput.toFile(), validation);

        if (!GOLD_ARTICLE_ID.equals(articleId)) {
            System.out.printf("[ARTICLE ANALYZER LIVE TEST] bodyChars=%d analyzer=%s validator=%s%n",
                    content.length(), analyzerOutput.toAbsolutePath(), validatorOutput.toAbsolutePath());
            return;
        }

        ArticleAnalysisResponse goldBaseline = json.readValue(
                Path.of("src/test/resources/article-validator/AKR20260816026000002-baseline.json").toFile(),
                ArticleAnalysisResponse.class);
        String goldBaselineJson = json.writeValueAsString(goldBaseline);
        ArticleValidationResult goldItems = json.readValue(client.complete(
                ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT,
                ArticleValidatorPromptBuilder.build(List.of(article), goldBaselineJson), 0),
                ArticleValidationResult.class);
        ArticleValidationResult goldMissing = json.readValue(client.complete(
                ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT,
                ArticleValidatorPromptBuilder.build(List.of(article), goldBaselineJson), 0),
                ArticleValidationResult.class);
        ArticleValidationResult goldValidation = ArticleValidationMerger.merge(
                List.of(article), goldBaseline, goldItems, goldMissing);
        Path goldOutput = Path.of("pipeline-debug/article-validator-gold-AKR20260816026000002.json");
        json.writerWithDefaultPrettyPrinter().writeValue(goldOutput.toFile(), goldValidation);

        assertTrue(hasTypeChange(goldValidation, "issues[0].relations[0].evidenceType", "FACT", "CLAIM"));
        assertTrue(hasFinding(goldValidation, "부부 공동명의"));
        assertTrue(hasFinding(goldValidation, "비거주"));
        assertTrue(hasFinding(goldValidation, "13개 반기"));
        assertTrue(hasTypeChange(goldValidation, "issues[2].relations[0]", "FACT", "CLAIM"));
        assertTrue(hasTypeChange(goldValidation, "issues[2].relations[1].evidenceType", "FACT", "INTERPRETATION"));
        assertFalse(goldValidation.articles().get(0).findings().stream()
                .anyMatch(f -> f.type() == ArticleValidationResult.FindingType.UNSUPPORTED
                        && f.description().contains("무기한")));
        assertFalse(goldValidation.articles().get(0).findings().stream()
                .anyMatch(f -> f.currentValue() != null && f.currentValue().isTextual()
                        && "CLAIMED_EFFECT".equals(f.currentValue().asText())
                        && f.suggestedValue() != null && "CLAIM".equals(f.suggestedValue().asText())));
        System.out.printf("[ARTICLE ANALYZER LIVE TEST] bodyChars=%d analyzer=%s validator=%s%n",
                content.length(), analyzerOutput.toAbsolutePath(), validatorOutput.toAbsolutePath());
    }

    private static boolean hasFinding(ArticleValidationResult validation, String evidence) {
        return validation.articles().get(0).findings().stream()
                .anyMatch(finding -> finding.evidence() != null && finding.evidence().contains(evidence));
    }

    private static boolean hasTypeChange(
            ArticleValidationResult validation, String reference, String current, String suggested) {
        return validation.articles().get(0).findings().stream()
                .anyMatch(finding -> finding.targetReference() != null
                        && finding.targetReference().startsWith(reference)
                        && finding.currentValue() != null && current.equals(finding.currentValue().asText())
                        && finding.suggestedValue() != null && suggested.equals(finding.suggestedValue().asText()));
    }
}
