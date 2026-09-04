package com.economicbriefing.analyzer.openai;

import com.economicbriefing.analyzer.dto.ArticleValidationResult;
import com.economicbriefing.analyzer.openai.dto.RetrievalRouterResponse;
import com.economicbriefing.analyzer.openai.prompt.ArticleAnalyzerPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.ArticleValidatorPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.RetrievalRouterPromptBuilder;
import com.economicbriefing.classifier.ArticlePersistenceService;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.economicflow.EconomicFlowContextService;
import com.economicbriefing.economicflow.EconomicFlowIngestor;
import com.economicbriefing.economicflow.EconomicPrincipleRetriever;
import com.economicbriefing.economicflow.repository.PrincipleVectorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/** Real OpenAI + PostgreSQL/pgvector diagnostic path. Never runs without an explicit URL property. */
@SpringBootTest
@TestPropertySource(properties = {"briefing.dry-run=false", "briefing.scheduler.enabled=false"})
@EnabledIfEnvironmentVariable(named = "ARTICLE_PRINCIPLE_URL", matches = "https?://.+")
class ArticlePrincipleRetrievalLiveTest {
    private static final Pattern ARTICLE_ID = Pattern.compile("AKR\\d+");
    private static final int TOP_K = 3;

    @Autowired private ArticleBodyFetcher fetcher;
    @Autowired private ArticlePersistenceService articles;
    @Autowired private OpenAiNewsAnalyzer analyzer;
    @Autowired private OpenAiClient client;
    @Autowired private AppProperties properties;
    @Autowired private EconomicFlowIngestor ingestor;
    @Autowired private EconomicFlowContextService flowContext;
    @Autowired private EconomicPrincipleRetriever principles;
    @Autowired private ArticlePresenter presenter;
    @Autowired private PrincipleVectorRepository principleVectors;
    @Autowired private ObjectMapper json;

    @Test
    void runsOneArticleThroughAnalysisFlowRouterValidationAndPrincipleRetrieval() throws Exception {
        String url = System.getenv("ARTICLE_PRINCIPLE_URL");
        var idMatcher = ARTICLE_ID.matcher(url);
        assertTrue(idMatcher.find(), "Yonhap article ID not found in URL");
        String articleId = idMatcher.group();
        Article article = fetcher.enrich(List.of(new Article(articleId, articleId, "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.now(), OffsetDateTime.now(), url,
                List.of(), "ko", null))).getFirst();
        assertFalse(article.content().isBlank(), "article body fetch failed");
        articles.save(article);

        var bundle = analyzer.analyzeArticleDraftBundleWithRetry(
                ArticleAnalyzerPromptBuilder.build(List.of(article)), List.of(article), properties.retry());
        String analysisJson = json.writeValueAsString(bundle.analysis());
        String flowClaimsJson = json.writeValueAsString(bundle.economicFlows().stream()
                .map(flow -> flow.flow().flowClaims()).toList());
        String explainedPathsJson = json.writeValueAsString(OpenAiNewsAnalyzer.sameEvidencePaths(bundle.analysis()));
        String routerRaw = client.complete(RetrievalRouterPromptBuilder.SYSTEM_PROMPT,
                RetrievalRouterPromptBuilder.build(analysisJson, flowClaimsJson, explainedPathsJson), 0);
        var router = json.readValue(routerRaw, RetrievalRouterResponse.class);
        OpenAiNewsAnalyzer.validateRouterResult(router, bundle.analysis());

        String validationPrompt = ArticleValidatorPromptBuilder.build(List.of(article), analysisJson, bundle.analysis());
        ArticleValidationResult itemValidation = json.readValue(client.complete(
                ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT, validationPrompt, 0), ArticleValidationResult.class);
        ArticleValidationResult missingValidation = json.readValue(client.complete(
                ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT, validationPrompt, 0), ArticleValidationResult.class);
        var validation = ArticleValidationMerger.merge(List.of(article), bundle.analysis(), itemValidation, missingValidation);

        var ingestions = bundle.economicFlows().stream()
                .map(flow -> ingestor.ingestFlow(flow.article(), flow.flow())).toList();
        var startNodeIds = ingestions.stream().flatMap(result -> result.resolvedNodes().stream())
                .map(EconomicFlowIngestor.ResolvedFlowNode::resolvedNodeId).collect(java.util.stream.Collectors.toSet());
        EconomicFlowContextService.Context graph = startNodeIds.isEmpty() ? null
                : flowContext.retrieve(analysisJson, startNodeIds);

        var queries = presenter.principleQueries(bundle.analysis());
        var retrieval = principles.retrieve(queries);
        var presentations = presenter.present(bundle.analysis(), bundle.economicFlows(), retrieval);

        var report = new LinkedHashMap<String, Object>();
        report.put("article", java.util.Map.of("articleId", article.id(), "title", article.title(),
                "url", article.url(), "bodyLength", article.content().length()));
        report.put("analyzer", bundle.analysis());
        var flowReport = new LinkedHashMap<String, Object>();
        flowReport.put("claims", bundle.economicFlows().stream().map(flow -> flow.flow().flowClaims()).toList());
        flowReport.put("ingestions", ingestions); flowReport.put("context", graph);
        report.put("economicFlow", flowReport);
        report.put("router", router);
        report.put("validation", validation);
        report.put("principleRetrieval", retrieval);
        report.put("presenter", presentations);
        var retrievalLog = new LinkedHashMap<String, Object>();
        retrievalLog.put("table", "economic_principle_chunk"); retrievalLog.put("topK", TOP_K);
        retrievalLog.put("embeddingProfile", principleVectors.embeddingProfile().orElse(null));
        retrievalLog.put("requestCount", queries.size()); retrievalLog.put("resultCount", retrieval.queries().size());
        report.put("retrievalLog", retrievalLog);
        Path output = Path.of("pipeline-debug/article-principle-retrieval-" + articleId + ".json");
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("[ARTICLE PRINCIPLE RETRIEVAL LIVE] " + output.toAbsolutePath());

        assertEquals(1, bundle.analysis().articles().size());
        assertEquals(1, router.articles().size());
    }
}
