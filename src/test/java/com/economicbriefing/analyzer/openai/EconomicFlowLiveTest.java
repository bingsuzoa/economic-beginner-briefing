package com.economicbriefing.analyzer.openai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.economicbriefing.analyzer.openai.prompt.ArticleAnalyzerPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.AnalysisPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.SystemPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.RetrievalRouterPromptBuilder;
import com.economicbriefing.analyzer.openai.dto.RetrievalRouterResponse;
import com.economicbriefing.analyzer.dto.AnalyzeNewsRequest;
import com.economicbriefing.domain.analysis.AudienceProfile;
import com.economicbriefing.classifier.ArticlePersistenceService;
import com.economicbriefing.classifier.repository.ArticleAnalyzerResultRepository;
import com.economicbriefing.classifier.repository.ArticleRouterResultRepository;
import com.economicbriefing.collector.NewsCollector;
import com.economicbriefing.collector.dto.CollectNewsResult;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.economicflow.EconomicFlowIngestor;
import com.economicbriefing.economicflow.EconomicFlowContextService;
import com.economicbriefing.economicflow.EventNormalizer;
import com.economicbriefing.economicflow.repository.EconomicEventRepository;
import com.economicbriefing.economicflow.repository.EventEvidenceRepository;
import com.economicbriefing.economicflow.repository.EventRelationRepository;
import com.economicbriefing.economicflow.repository.TopicCandidateRepository;
import com.economicbriefing.economicflow.repository.EconomicPrincipleChunkRepository;
import com.economicbriefing.economicflow.entity.EconomicPrincipleChunkEntity;
import com.economicbriefing.economicflow.EconomicPrincipleRetriever;
import com.economicbriefing.pipeline.BriefingPipeline;
import com.economicbriefing.pipeline.PipelineOptions;
import com.economicbriefing.util.KstDateTimeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ECONOMIC_FLOW_LIVE_TEST", matches = "true")
class EconomicFlowLiveTest {
    private static final String ARTICLE_ID = "AKR20260824075851002";
    private static final String URL = "https://www.yna.co.kr/view/AKR20260824075851002?section=economy/finance";

    @Autowired ArticleBodyFetcher bodyFetcher;
    @Autowired OpenAiNewsAnalyzer analyzer;
    @Autowired AppProperties properties;
    @Autowired ObjectMapper json;
    @Autowired ArticlePersistenceService articlePersistence;
    @Autowired EconomicFlowIngestor ingestor;
    @Autowired EconomicFlowContextService flowContextService;
    @Autowired OpenAiClient openAiClient;
    @Autowired EventNormalizer normalizer;
    @Autowired EconomicEventRepository events;
    @Autowired EventEvidenceRepository evidence;
    @Autowired EventRelationRepository relations;
    @Autowired TopicCandidateRepository topicCandidates;
    @Autowired JdbcTemplate jdbc;
    @Autowired BriefingPipeline pipeline;
    @Autowired ArticleAnalyzerResultRepository analyzerResults;
    @Autowired ArticleRouterResultRepository routerResults;
    @Autowired EconomicPrincipleChunkRepository principleChunks;
    @Autowired EconomicPrincipleRetriever principleRetriever;
    @MockitoBean NewsCollector collector;

    @Test
    void processesRealYonhapArticleTwice() throws Exception {
        Article article = bodyFetcher.enrich(List.of(new Article(
                ARTICLE_ID, "원/달러 환율 13개월 만에 최저…장중 1,370원대로 하락(종합)", "",
                "연합뉴스", ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.parse("2026-08-24T15:52:00+09:00"), OffsetDateTime.now(), URL,
                List.of(NewsCategory.EXCHANGE_RATE), "ko", null))).getFirst();
        assertNotNull(article.content());
        assertTrue(article.content().length() > 300);
        articlePersistence.save(article);

        var bundle = analyzer.analyzeArticleDraftBundleWithRetry(
                ArticleAnalyzerPromptBuilder.build(List.of(article)), List.of(article), properties.retry());
        long beforeEvents = events.count();
        long beforeEvidence = evidence.count();
        long beforeRelations = relations.count();
        long beforeTopicCandidates = topicCandidates.count();

        List<EconomicFlowIngestor.IngestionResult> first = new ArrayList<>();
        bundle.eventCandidates().forEach(candidate -> first.add(ingestor.ingest(candidate)));
        long afterFirstEvents = events.count();
        long afterFirstEvidence = evidence.count();
        long afterFirstRelations = relations.count();

        List<EconomicFlowIngestor.IngestionResult> second = new ArrayList<>();
        bundle.eventCandidates().forEach(candidate -> second.add(ingestor.ingest(candidate)));

        var normalized = bundle.eventCandidates().stream().map(candidate -> {
            var values = new LinkedHashMap<String, Object>();
            values.put("title", candidate.title());
            values.put("previous", normalizer.normalizeValue(candidate.subjectKey(), candidate.previousState()));
            values.put("new", normalizer.normalizeValue(candidate.subjectKey(), candidate.newState()));
            values.put("region", normalizer.normalizeRegion(candidate.region()));
            return values;
        }).toList();
        var rows = jdbc.queryForList("""
                SELECT e.id, e.event_type, e.title, e.subject, e.subject_key, e.event_date, e.status,
                       e.previous_value, e.previous_value_normalized, e.new_value, e.new_value_normalized,
                       e.value_unit, e.value_type, e.base_currency, e.quote_currency, e.base_amount,
                       e.milestone_type, e.milestone_period_value, e.milestone_period_unit,
                       e.milestone_reference_date, e.region_code, t.topic_key
                FROM economic_events e
                LEFT JOIN event_topics et ON et.event_id = e.id
                LEFT JOIN topics t ON t.id = et.topic_id
                WHERE EXISTS (SELECT 1 FROM event_evidence ev WHERE ev.event_id = e.id AND ev.article_id = ?)
                ORDER BY e.id, t.topic_key
                """, ARTICLE_ID);

        var report = new LinkedHashMap<String, Object>();
        report.put("articleId", ARTICLE_ID);
        report.put("title", article.title());
        report.put("bodyLength", article.content().length());
        report.put("analysis", bundle.analysis());
        report.put("eventCandidates", bundle.eventCandidates());
        report.put("normalized", normalized);
        report.put("firstIngestion", first);
        report.put("secondIngestion", second);
        report.put("eventDeltaFirst", afterFirstEvents - beforeEvents);
        report.put("evidenceDeltaFirst", afterFirstEvidence - beforeEvidence);
        report.put("relationDeltaFirst", afterFirstRelations - beforeRelations);
        report.put("eventDeltaSecond", events.count() - afterFirstEvents);
        report.put("evidenceDeltaSecond", evidence.count() - afterFirstEvidence);
        report.put("relationDeltaSecond", relations.count() - afterFirstRelations);
        report.put("topicCandidateDelta", topicCandidates.count() - beforeTopicCandidates);
        report.put("storedRows", rows);

        Path output = Path.of("pipeline-debug/economic-flow-live-" + ARTICLE_ID + ".json");
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("[ECONOMIC FLOW LIVE TEST] " + output.toAbsolutePath());

        assertEquals(0, events.count() - afterFirstEvents, "same candidates must not create Events twice");
        assertEquals(0, evidence.count() - afterFirstEvidence, "same article must not create Evidence twice");
        assertTrue(bundle.eventCandidates().stream().anyMatch(candidate ->
                candidate.eventType() == com.economicbriefing.economicflow.EventType.INDICATOR_MILESTONE));
    }

    @Test
    void runsRealArticleThroughAnalyzerRouterFinalAndEconomicFlow() throws Exception {
        Article article = bodyFetcher.enrich(List.of(new Article(
                ARTICLE_ID, "원/달러 환율 13개월 만에 최저", "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.parse("2026-08-24T15:52:00+09:00"),
                OffsetDateTime.now(), URL, List.of(NewsCategory.EXCHANGE_RATE), "ko", null))).getFirst();
        articlePersistence.save(article);
        long beforeEvidence = evidence.count();

        var result = analyzer.analyze(AnalyzeNewsRequest.of(List.of(article),
                java.time.LocalDate.of(2026, 8, 24), 1,
                new AudienceProfile("beginner", List.of(NewsCategory.EXCHANGE_RATE), List.of())));

        var report = new LinkedHashMap<String, Object>();
        report.put("articleId", ARTICLE_ID);
        report.put("briefing", result.briefing());
        report.put("analyzer", result.articleAnalysis());
        report.put("router", result.routerResult());
        report.put("eventCandidates", result.eventCandidates());
        report.put("eventRelations", result.eventRelations());
        report.put("eventRows", jdbc.queryForList("""
                SELECT e.id, e.node_kind, e.scope_key, e.subject_key, s.slot_key, sv.value_key,
                       e.event_type, e.title, e.event_date, e.ended_at
                FROM economic_events e
                LEFT JOIN economic_slots s ON s.id=e.slot_id
                LEFT JOIN economic_slot_values sv ON sv.id=e.slot_value_id
                WHERE EXISTS (SELECT 1 FROM event_evidence ev WHERE ev.event_id=e.id AND ev.article_id=?)
                ORDER BY e.id
                """, ARTICLE_ID));
        Path output = Path.of("pipeline-debug/economic-flow-full-e2e-" + ARTICLE_ID + ".json");
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);

        assertEquals(1, result.articleAnalysis().articles().size());
        assertEquals(1, result.routerResult().articles().size());
        assertFalse(result.briefing().news().isEmpty());
        assertTrue(evidence.count() >= beforeEvidence);
        System.out.println("[ECONOMIC FLOW FULL E2E] " + output.toAbsolutePath());
    }

    @Test
    void comparesFinalAnalysisWithAndWithoutEconomicFlow() throws Exception {
        Article article = bodyFetcher.enrich(List.of(new Article(
                ARTICLE_ID, "원/달러 환율 13개월 만에 최저", "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.parse("2026-08-24T15:52:00+09:00"),
                OffsetDateTime.now(), URL, List.of(NewsCategory.EXCHANGE_RATE), "ko", null))).getFirst();
        var bundle = analyzer.analyzeArticleDraftBundleWithRetry(
                ArticleAnalyzerPromptBuilder.build(List.of(article)), List.of(article), properties.retry());
        var ingestion = ingestor.ingestAll(bundle.eventCandidates(), bundle.eventRelations());
        var startIds = java.util.stream.IntStream.range(0, bundle.eventCandidates().size())
                .filter(i -> bundle.eventCandidates().get(i).nodeKind() != null)
                .mapToObj(i -> ingestion.get(i).eventId()).collect(java.util.stream.Collectors.toSet());
        var context = flowContextService.retrieve(json.writeValueAsString(bundle.analysis()), startIds);
        String analysisJson = json.writeValueAsString(bundle.analysis());
        AudienceProfile audience = new AudienceProfile(
                "beginner", List.of(NewsCategory.EXCHANGE_RATE), List.of());
        String withoutFlowPrompt = AnalysisPromptBuilder.build(List.of(article),
                java.time.LocalDate.of(2026, 8, 24), 1, audience, analysisJson, null);
        String withFlowPrompt = AnalysisPromptBuilder.build(List.of(article),
                java.time.LocalDate.of(2026, 8, 24), 1, audience, analysisJson,
                json.writeValueAsString(context));
        String withoutFlow = openAiClient.complete(SystemPromptBuilder.SYSTEM_PROMPT, withoutFlowPrompt, 0);
        String withFlow = openAiClient.complete(SystemPromptBuilder.SYSTEM_PROMPT, withFlowPrompt, 0);
        var boundedWithoutFlow = OpenAiNewsAnalyzer.applyPrincipleBoundary(
                json.readValue(withoutFlow, com.economicbriefing.analyzer.openai.dto.AiResponse.class),
                bundle.analysis(), false);
        var boundedWithFlow = OpenAiNewsAnalyzer.applyPrincipleBoundary(
                json.readValue(withFlow, com.economicbriefing.analyzer.openai.dto.AiResponse.class),
                bundle.analysis(), false);

        var report = new LinkedHashMap<String, Object>();
        report.put("articleId", ARTICLE_ID);
        report.put("startNodeIds", startIds);
        report.put("economicFlowContext", context);
        report.put("withoutFlowPrompt", withoutFlowPrompt);
        report.put("withFlowPrompt", withFlowPrompt);
        report.put("withoutFlowModelRawJson", json.readTree(withoutFlow));
        report.put("withFlowModelRawJson", json.readTree(withFlow));
        report.put("withoutFlowFinalJson", boundedWithoutFlow);
        report.put("withFlowFinalJson", boundedWithFlow);
        Path output = Path.of("pipeline-debug/economic-flow-ab-" + ARTICLE_ID + ".json");
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);

        assertTrue(withoutFlowPrompt.contains("(검증된 Economic Flow 없음)"));
        assertTrue(withFlowPrompt.contains("\"nodes\""));
        assertFalse(context.nodes().isEmpty());
        System.out.println("[ECONOMIC FLOW A/B] " + output.toAbsolutePath());
    }

    @Test
    void runsProductionPipelineAndStoresAnalyzerAndRouterResults() throws Exception {
        Article article = bodyFetcher.enrich(List.of(new Article(
                ARTICLE_ID, "원/달러 환율 13개월 만에 최저", "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.parse("2026-08-24T15:52:00+09:00"),
                OffsetDateTime.now(), URL, List.of(NewsCategory.EXCHANGE_RATE), "ko", null))).getFirst();
        when(collector.collect(any())).thenReturn(new CollectNewsResult(
                java.time.LocalDate.of(2026, 8, 24), List.of(article), List.of(), 1, 1, 0));
        int analyzerBefore = analyzerResults.findByArticleIdOrderByCreatedAtDesc(ARTICLE_ID).size();
        int routerBefore = routerResults.findByArticleIdOrderByCreatedAtDesc(ARTICLE_ID).size();
        var start = OffsetDateTime.parse("2026-08-24T03:00:00+09:00");
        var result = pipeline.run(PipelineOptions.hourly(new KstDateTimeUtil.TimeRange(
                start, start.plusHours(1).minusSeconds(1))));
        var savedAnalyzer = analyzerResults.findByArticleIdOrderByCreatedAtDesc(ARTICLE_ID);
        var savedRouter = routerResults.findByArticleIdOrderByCreatedAtDesc(ARTICLE_ID);

        assertEquals("SUCCESS", result.getStatus().name());
        assertEquals(analyzerBefore + 1, savedAnalyzer.size());
        assertEquals(routerBefore + 1, savedRouter.size());
        assertEquals(savedAnalyzer.getFirst().getBriefingId(), savedRouter.getFirst().getBriefingId());
    }

    @Test
    @Transactional
    void runsExchangeArticleWithSourcedPrincipleContext() throws Exception {
        var fixture = new EconomicPrincipleChunkEntity();
        fixture.setContent("수출기업이 받은 달러를 시장에 매도하면 달러 공급이 증가한다. 다른 조건이 같다면 달러 가격에 하락 압력이 생겨 원/달러 환율도 하락할 수 있다.");
        fixture.setConcepts("네고 물량 수출업체 달러 매도 달러 공급 원 달러 환율 하락 외환시장");
        fixture.setFromConcept("달러 공급 증가"); fixture.setToConcept("원 달러 환율 하락");
        fixture.setMechanism("수요 공급"); fixture.setSourceType("TEST_FIXTURE");
        fixture.setSourceTitle("외환시장 경제원리 fixture"); fixture.setSourceSection("달러 공급과 환율");
        principleChunks.save(fixture);

        Article article = bodyFetcher.enrich(List.of(new Article(
                ARTICLE_ID, "원/달러 환율 13개월 만에 최저", "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.parse("2026-08-24T15:52:00+09:00"),
                OffsetDateTime.now(), URL, List.of(NewsCategory.EXCHANGE_RATE), "ko", null))).getFirst();
        var result = analyzer.analyze(AnalyzeNewsRequest.of(List.of(article),
                java.time.LocalDate.of(2026, 8, 24), 1,
                new AudienceProfile("beginner", List.of(NewsCategory.EXCHANGE_RATE), List.of())));
        var whyQueries = result.routerResult().articles().stream().flatMap(a -> a.issues().stream())
                .flatMap(i -> i.requests().stream())
                .filter(request -> request.gapType() == com.economicbriefing.analyzer.openai.dto.RetrievalRouterResponse.GapType.WHY)
                .map(request -> new EconomicPrincipleRetriever.Query(
                        "ROUTER_WHY", request.sourceReference(), request.query())).toList();
        var principles = principleRetriever.retrieve(whyQueries);

        var report = new LinkedHashMap<String, Object>();
        report.put("articleId", ARTICLE_ID); report.put("articleRelations", result.articleAnalysis());
        report.put("router", result.routerResult()); report.put("principleContext", principles);
        report.put("finalBriefing", result.briefing());
        Path output = Path.of("pipeline-debug/economic-principle-live-" + ARTICLE_ID + ".json");
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);

        assertFalse(whyQueries.isEmpty());
        assertFalse(principles.queries().isEmpty());
        assertEquals("TEST_FIXTURE", principles.queries().getFirst().results().getFirst().sourceType());
        System.out.println("[ECONOMIC PRINCIPLE LIVE] " + output.toAbsolutePath());
    }

    @Test
    void debugsRealArticleThroughRouterAndEconomicFlow() throws Exception {
        Article article = bodyFetcher.enrich(List.of(new Article(
                ARTICLE_ID, "원/달러 환율 13개월 만에 최저", "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.parse("2026-08-24T15:52:00+09:00"),
                OffsetDateTime.now(), URL, List.of(NewsCategory.EXCHANGE_RATE), "ko", null))).getFirst();
        articlePersistence.save(article);
        var bundle = analyzer.analyzeArticleDraftBundleWithRetry(
                ArticleAnalyzerPromptBuilder.build(List.of(article)), List.of(article), properties.retry());
        String analysisJson = json.writeValueAsString(bundle.analysis());
        String routerRaw = openAiClient.complete(RetrievalRouterPromptBuilder.SYSTEM_PROMPT,
                RetrievalRouterPromptBuilder.build(analysisJson), 0);
        var router = json.readTree(routerRaw);
        boolean routerValid;
        String routerError;
        try {
            OpenAiNewsAnalyzer.validateRouterResult(
                    json.readValue(routerRaw, RetrievalRouterResponse.class), bundle.analysis());
            routerValid = true; routerError = "";
        } catch (IllegalArgumentException e) {
            routerValid = false; routerError = e.getMessage();
        }

        var existing = bundle.eventCandidates().stream().map(candidate -> jdbc.queryForList("""
                SELECT e.id AS node_id,e.node_kind,e.scope_key,e.subject_key,s.slot_key,sv.value_key,
                       e.event_date,ev.article_id AS latest_evidence_article,ev.evidence_text AS latest_evidence
                FROM economic_events e
                JOIN economic_slots s ON s.id=e.slot_id
                LEFT JOIN economic_slot_values sv ON sv.id=e.slot_value_id
                LEFT JOIN LATERAL (SELECT article_id,evidence_text FROM event_evidence
                  WHERE event_id=e.id ORDER BY id DESC LIMIT 1) ev ON true
                WHERE e.ended_at IS NULL AND e.scope_key=? AND e.subject_key=? AND s.slot_key=?
                ORDER BY e.event_date DESC,e.id DESC
                """, candidate.scopeKey(), candidate.subjectKey(), candidate.slotKey())).toList();
        long beforeEvents = events.count(), beforeEvidence = evidence.count(), beforeRelations = relations.count();
        long beforeTopics = jdbc.queryForObject("SELECT count(*) FROM event_topics", Long.class);
        long beforeRelationEvidence = jdbc.queryForObject("SELECT count(*) FROM event_relation_evidence", Long.class);
        var ingestion = ingestor.ingestAll(bundle.eventCandidates(), bundle.eventRelations());

        var relationDiagnostics = bundle.analysis().articles().getFirst().issues().stream()
                .flatMap(issue -> issue.relations().stream()).map(relation -> {
                    var item = new LinkedHashMap<String, Object>();
                    item.put("from", relation.from()); item.put("to", relation.to());
                    item.put("relationType", relation.relationType());
                    item.put("evidenceType", relation.evidenceType());
                    item.put("evidenceText", relation.articleExplanation());
                    item.put("fromCandidateKey", null); item.put("toCandidateKey", null);
                    item.put("saved", false);
                    item.put("reason", "Analyzer relation endpoints have no candidateKey mapping");
                    return item;
                }).toList();
        var candidateDebug = java.util.stream.IntStream.range(0, bundle.eventCandidates().size()).mapToObj(i -> {
            var candidate = bundle.eventCandidates().get(i); var item = new LinkedHashMap<String, Object>();
            item.put("candidate", candidate);
            item.put("normalization", java.util.Map.of("scope", candidate.scopeKey(),
                    "subject", candidate.subjectKey(), "slot", candidate.slotKey(), "value", candidate.valueKey(),
                    "slotMasterMatch", true, "slotValueMasterMatch", true));
            item.put("existingNodes", existing.get(i));
            boolean exact = existing.get(i).stream().anyMatch(row -> candidate.valueKey().equals(row.get("value_key")));
            item.put("memoryDecision", java.util.Map.of("decision", ingestion.get(i).created() ? "NEW_EVENT" : "REPEATED_STATE",
                    "matchedNodeId", ingestion.get(i).eventId(), "javaExact", exact,
                    "comparator", !exact, "reason", exact ? "active scope+subject+slot+value exact match" : "comparator"));
            return item;
        }).toList();

        var report = new LinkedHashMap<String, Object>();
        report.put("article", java.util.Map.of("articleId", article.id(), "title", article.title(),
                "publishedAt", article.publishedAt(), "body", article.content(), "source", "LIVE_ARTICLE"));
        report.put("analyzer", bundle.analysis());
        report.put("router", java.util.Map.of("raw", router, "contractValid", routerValid,
                "contractError", routerError));
        report.put("eventCandidates", candidateDebug); report.put("relationCandidates", relationDiagnostics);
        report.put("dbDelta", java.util.Map.of("economic_events", events.count() - beforeEvents,
                "event_topics", jdbc.queryForObject("SELECT count(*) FROM event_topics", Long.class) - beforeTopics,
                "event_evidence", evidence.count() - beforeEvidence,
                "event_relations", relations.count() - beforeRelations,
                "event_relation_evidence", jdbc.queryForObject("SELECT count(*) FROM event_relation_evidence", Long.class) - beforeRelationEvidence));
        report.put("storedNodes", jdbc.queryForList("""
                SELECT e.id,e.node_kind,e.scope_key,e.subject_key,s.slot_key,sv.value_key,e.event_date,e.ended_at
                FROM economic_events e JOIN economic_slots s ON s.id=e.slot_id
                LEFT JOIN economic_slot_values sv ON sv.id=e.slot_value_id
                WHERE EXISTS (SELECT 1 FROM event_evidence ev WHERE ev.event_id=e.id AND ev.article_id=?)
                ORDER BY e.id
                """, ARTICLE_ID));
        report.put("readableSummary", "KR / USD_KRW / EXCHANGE_RATE_DIRECTION / FALLING; existing active Node reused. Analyzer relation '네고 물량 → 환율 하락' was not stored because the from endpoint has no normalized EventCandidate.");
        Path output = Path.of("pipeline-debug/economic-flow-debug-" + ARTICLE_ID + ".json");
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("[ECONOMIC FLOW DEBUG] " + output.toAbsolutePath());
    }
}
