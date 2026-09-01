package com.economicbriefing.analyzer.openai;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.economicbriefing.analyzer.openai.dto.RetrievalRouterResponse;
import com.economicbriefing.analyzer.openai.prompt.ArticleAnalyzerPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.RetrievalRouterPromptBuilder;
import com.economicbriefing.classifier.ArticlePersistenceService;
import com.economicbriefing.domain.article.*;
import com.economicbriefing.economicflow.*;
import com.economicbriefing.economicflow.entity.*;
import com.economicbriefing.economicflow.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"briefing.dry-run=false", "briefing.scheduler.enabled=false"})
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "manual.flow.input", matches = ".+")
class ManualEconomicFlowDebugTest {
    private static final Pattern ARTICLE_ID = Pattern.compile("AKR\\d+");
    @Autowired ObjectMapper json;
    @Autowired ArticleBodyFetcher fetcher;
    @Autowired OpenAiNewsAnalyzer analyzer;
    @Autowired OpenAiClient client;
    @Autowired com.economicbriefing.config.AppProperties properties;
    @Autowired ArticlePersistenceService articles;
    @Autowired EconomicFlowIngestor ingestor;
    @Autowired EconomicEventRepository events;
    @Autowired EventRelationRepository relations;
    @Autowired EconomicSlotRepository slots;
    @Autowired EconomicSlotValueRepository values;
    @Autowired TopicRepository topics;
    @Autowired JdbcTemplate jdbc;

    @Test
    void runsOnlyManifestArticlesInOrder() throws Exception {
        Path input = Path.of(System.getProperty("manual.flow.input")).toAbsolutePath().normalize();
        Manifest manifest = json.readValue(input.toFile(), Manifest.class);
        assertNotNull(manifest.articles());
        assertFalse(manifest.articles().isEmpty(), "manifest articles must not be empty");
        seedMasters();
        int repeat = Math.max(1, Integer.getInteger("manual.flow.analysisRepeat", 1));
        List<Object> reports = new ArrayList<>();
        for (ManifestArticle item : manifest.articles()) {
            try {
                reports.add(process(item, repeat));
            } catch (Exception e) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("label", item.label()); failure.put("status", "FAILED");
                failure.put("errorType", e.getClass().getName()); failure.put("error", e.getMessage());
                writeArticle(item.label(), failure); reports.add(failure);
            }
        }

        var output = new LinkedHashMap<String, Object>();
        output.put("input", input.toString()); output.put("analysisRepeat", repeat);
        output.put("database", "isolated H2 in-memory test database"); output.put("articles", reports);
        Path target = Path.of("pipeline-debug/manual-economic-flow-debug.json");
        Files.createDirectories(target.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), output);
        System.out.println("[MANUAL ECONOMIC FLOW DEBUG] " + target.toAbsolutePath());
    }

    private Map<String, Object> process(ManifestArticle input, int repeat) throws Exception {
        Article article = article(input);
        List<Object> runs = new ArrayList<>();
        List<Object> fingerprints = new ArrayList<>();
        OpenAiNewsAnalyzer.AnalyzerDraftBundle selected = null;
        JsonNode selectedRouter = null;
        for (int run = 1; run <= repeat; run++) {
            var bundle = analyzer.analyzeArticleDraftBundleWithRetry(
                    ArticleAnalyzerPromptBuilder.build(List.of(article)), List.of(article), properties.retry());
            String analyzerJson = json.writeValueAsString(bundle.analysis());
            String flowClaimsJson = json.writeValueAsString(bundle.economicFlows().stream()
                    .map(flow -> flow.flow().flowClaims()).toList());
            String explainedPathsJson = json.writeValueAsString(OpenAiNewsAnalyzer.sameEvidencePaths(bundle.analysis()));
            String raw = client.complete(RetrievalRouterPromptBuilder.SYSTEM_PROMPT,
                    RetrievalRouterPromptBuilder.build(analyzerJson, flowClaimsJson, explainedPathsJson), 0);
            JsonNode router = json.readTree(raw);
            String contractError = routerError(raw, bundle);
            runs.add(Map.of("run", run, "status", "SUCCESS",
                    "routerContractValid", contractError.isEmpty(), "routerContractError", contractError));
            fingerprints.add(fingerprint(bundle, router));
            if (run == 1) { selected = bundle; selectedRouter = router; }
        }
        if (repeat > 1) return repeatOnly(input, article, runs, fingerprints, selected, selectedRouter);

        articles.save(article);
        var articleFlow = selected.economicFlows().getFirst();
        var result = ingestor.ingestFlow(articleFlow.article(), articleFlow.flow());
        result.resolvedNodes().forEach(node -> assertTrue(events.existsById(node.resolvedNodeId())));
        result.resolvedEdges().forEach(edge -> assertTrue(relations
                .findByFromEvent_IdAndToEvent_IdAndRelationType(
                        edge.fromNodeId(), edge.toNodeId(), edge.relationType()).isPresent()));
        var report = new LinkedHashMap<String, Object>();
        report.put("label", input.label()); report.put("article", articleView(article));
        report.put("analysisRuns", runs); report.put("analyzer", selected.analysis());
        report.put("router", selectedRouter);
        var nodes = result.resolvedNodes().stream().map(node -> events.findById(node.resolvedNodeId())
                .map(event -> new OutputNode(event.getId(), event.getTitle(), event.getEventDate()))
                .orElseThrow()).toList();
        report.put("economicFlow", Map.of("claims", articleFlow.flow().flowClaims(),
                "nodes", nodes));
        writeArticle(input.label(), report);
        return report;
    }

    private Map<String, Object> repeatOnly(ManifestArticle input, Article article, List<Object> runs,
            List<Object> fingerprints, OpenAiNewsAnalyzer.AnalyzerDraftBundle selected, JsonNode router) throws Exception {
        var report = new LinkedHashMap<String, Object>();
        report.put("label", input.label()); report.put("article", articleView(article));
        report.put("analysisRuns", runs); report.put("dbWriteSkipped", true);
        report.put("analyzer", selected.analysis()); report.put("router", router);
        report.put("economicFlow", Map.of("claims",
                selected.economicFlows().getFirst().flow().flowClaims()));
        report.put("comparison", Map.of("runs", fingerprints,
                "allDimensionsIdentical", fingerprints.stream().distinct().count() == 1));
        report.put("reason", "analysisRepeat > 1 compares LLM output without DB storage");
        writeArticle(input.label(), report);
        return report;
    }

    private Map<String, Object> fingerprint(OpenAiNewsAnalyzer.AnalyzerDraftBundle bundle, JsonNode router) {
        var issues = bundle.analysis().articles().getFirst().issues();
        return Map.of(
                "issues", issues.stream().map(issue -> issue.name()).toList(),
                "flowClaims", bundle.economicFlows().getFirst().flow().flowClaims(),
                "relations", issues.stream().flatMap(issue -> issue.relations().stream()).toList(),
                "routerRequests", router.path("articles"));
    }

    private Article article(ManifestArticle input) {
        if (blank(input.url()) == blank(input.body())) {
            throw new IllegalArgumentException("Exactly one of url or body must be provided: " + input.label());
        }
        String id = !blank(input.articleId()) ? input.articleId() : idFrom(input.url(), input.label());
        OffsetDateTime published = !blank(input.publishedAt())
                ? OffsetDateTime.parse(input.publishedAt()) : OffsetDateTime.now();
        Article article = new Article(id, blank(input.title()) ? input.label() : input.title(), "",
                sourceName(input.url()), ArticleSourceType.NEWS_MEDIA, published, OffsetDateTime.now(),
                blank(input.url()) ? "manual://" + id : input.url(), List.of(), "ko",
                blank(input.body()) ? "" : input.body());
        if (!blank(input.url())) article = fetcher.enrich(List.of(article)).getFirst();
        if (blank(article.content())) throw new IllegalArgumentException("Article body fetch failed: " + input.label());
        return article;
    }

    private String routerError(String raw, OpenAiNewsAnalyzer.AnalyzerDraftBundle bundle) {
        try {
            OpenAiNewsAnalyzer.validateRouterResult(
                    read(raw, RetrievalRouterResponse.class), bundle.analysis());
            return "";
        } catch (Exception e) { return e.getMessage(); }
    }

    private <T> T read(String raw, Class<T> type) throws Exception { return json.readValue(raw, type); }

    private List<Map<String, Object>> existingNodes(EventCandidate candidate) {
        if (blank(candidate.slotKey())) return List.of();
        return jdbc.queryForList("""
                SELECT e.id AS node_id,e.node_kind,e.scope_key,e.subject_key,s.slot_key,sv.value_key,
                       e.event_date,e.ended_at,
                       (SELECT ev.article_id FROM event_evidence ev WHERE ev.event_id=e.id
                        ORDER BY ev.id DESC LIMIT 1) AS latest_evidence_article,
                       (SELECT ev.evidence_text FROM event_evidence ev WHERE ev.event_id=e.id
                        ORDER BY ev.id DESC LIMIT 1) AS latest_evidence
                FROM economic_events e JOIN economic_slots s ON s.id=e.slot_id
                LEFT JOIN economic_slot_values sv ON sv.id=e.slot_value_id
                WHERE e.ended_at IS NULL AND e.scope_key=? AND e.subject_key=? AND s.slot_key=?
                ORDER BY e.event_date DESC,e.id DESC
                """, candidate.scopeKey(), candidate.subjectKey(), candidate.slotKey());
    }

    private List<Map<String, Object>> candidateDebug(List<EventCandidate> candidates,
            List<List<Map<String, Object>>> existing, List<EconomicFlowIngestor.IngestionResult> results) {
        return java.util.stream.IntStream.range(0, candidates.size()).mapToObj(i -> {
            EventCandidate c = candidates.get(i); var result = results.get(i);
            Map<String, Object> item = new LinkedHashMap<>(); item.put("candidate", c);
            var slot = slots.findBySlotKeyAndActiveTrue(c.slotKey());
            Map<String, Object> normalization = new LinkedHashMap<>();
            normalization.put("scope", c.scopeKey()); normalization.put("subject", c.subjectKey());
            normalization.put("slot", c.slotKey()); normalization.put("value", c.valueKey());
            normalization.put("slotMasterMatch", slot.isPresent());
            normalization.put("slotValueMasterMatch", c.valueKey() == null || slot.isPresent()
                    && values.findBySlot_IdAndValueKeyAndActiveTrue(slot.get().getId(), c.valueKey()).isPresent());
            item.put("normalization", normalization);
            Map<String, Object> search = new LinkedHashMap<>();
            search.put("scope", c.scopeKey()); search.put("subject", c.subjectKey()); search.put("slot", c.slotKey());
            item.put("existingNodeSearch", search); item.put("existingNodes", existing.get(i));
            item.put("memoryDecision", result);
            List<String> warnings = new ArrayList<>();
            if (c.eventType() == EventType.INDICATOR_MILESTONE && c.nodeKind() == NodeKind.STATE) {
                warnings.add("MILESTONE_EVENT_AND_STATE_MERGED");
            }
            if ((c.title() + " " + c.subject()).contains("요구") && "RATE_DECISION".equals(c.slotKey())) {
                warnings.add("REQUEST_MAY_NOT_BE_RATE_DECISION");
            }
            if (candidates.stream().anyMatch(other -> other != c
                    && Objects.equals(c.scopeKey(), other.scopeKey())
                    && Objects.equals(c.subjectKey(), other.subjectKey())
                    && Objects.equals(c.slotKey(), other.slotKey())
                    && Objects.equals(c.valueKey(), other.valueKey())
                    && !Objects.equals(c.title(), other.title()))) {
                warnings.add("POSSIBLE_DIFFERENT_MEANING_SAME_NORMALIZED_ADDRESS");
            }
            if (candidates.stream().anyMatch(other -> other != c
                    && Objects.equals(c.subjectKey(), other.subjectKey())
                    && (!Objects.equals(c.slotKey(), other.slotKey())
                    || !Objects.equals(c.valueKey(), other.valueKey())))) {
                warnings.add("POSSIBLE_SAME_SUBJECT_DIFFERENT_NORMALIZED_ADDRESS");
            }
            item.put("semanticWarnings", warnings); return item;
        }).toList();
    }

    private List<Map<String, Object>> relationDebug(OpenAiNewsAnalyzer.AnalyzerDraftBundle bundle,
            List<EconomicFlowIngestor.IngestionResult> results) {
        Map<String, Long> ids = new HashMap<>();
        for (int i = 0; i < bundle.eventCandidates().size(); i++) {
            ids.put(bundle.eventCandidates().get(i).candidateKey(), results.get(i).eventId());
        }
        return bundle.analysis().articles().getFirst().issues().stream().flatMap(issue -> issue.relations().stream())
                .map(relation -> {
                    var mapped = bundle.eventRelations().stream()
                            .filter(candidate -> candidate.evidenceText().equals(relation.articleExplanation()))
                            .findFirst();
                    String fromKey = mapped.map(EventRelationCandidate::fromCandidateKey).orElse(null);
                    String toKey = mapped.map(EventRelationCandidate::toCandidateKey).orElse(null);
                    Long fromId = ids.get(fromKey), toId = ids.get(toKey);
                    boolean saved = fromId != null && toId != null && relations.findAll().stream().anyMatch(edge ->
                            edge.getFromEvent().getId().equals(fromId) && edge.getToEvent().getId().equals(toId));
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("from", relation.from()); item.put("to", relation.to());
                    item.put("relationType", relation.relationType()); item.put("evidenceType", relation.evidenceType());
                    item.put("evidenceText", relation.articleExplanation()); item.put("fromCandidateKey", fromKey);
                    item.put("toCandidateKey", toKey); item.put("fromEndpointCandidateExists", ids.containsKey(fromKey));
                    item.put("toEndpointCandidateExists", ids.containsKey(toKey));
                    item.put("semanticFromCandidates", semanticCandidates(relation.from(), bundle.eventCandidates()));
                    item.put("semanticToCandidates", semanticCandidates(relation.to(), bundle.eventCandidates()));
                    item.put("fromNodeId", fromId); item.put("toNodeId", toId); item.put("edgeSaved", saved);
                    item.put("notSavedReason", saved ? "" : missingReason(fromKey, toKey, fromId, toId,
                            relation.from(), relation.to(), bundle.eventCandidates()));
                    return item;
                }).toList();
    }

    private static String missingReason(String fromKey, String toKey, Long fromId, Long toId,
            String from, String to, List<EventCandidate> candidates) {
        if (fromKey == null && toKey == null) {
            boolean fromSemantic = !semanticCandidates(from, candidates).isEmpty();
            boolean toSemantic = !semanticCandidates(to, candidates).isEmpty();
            if (!fromSemantic && toSemantic) return "from endpoint Candidate absent; to semantic Candidate exists but candidateKey mapping absent";
            if (!fromSemantic) return "from endpoint Candidate absent; to endpoint Candidate also not mapped";
            return "semantic endpoint Candidates exist but relation candidateKey mapping absent";
        }
        if (fromId == null) return "fromCandidateKey has no DB Node mapping";
        if (toId == null) return "toCandidateKey has no DB Node mapping";
        return "mapped endpoints did not produce an Edge";
    }

    private static List<String> semanticCandidates(String endpoint, List<EventCandidate> candidates) {
        Set<String> terms = terms(endpoint);
        return candidates.stream().filter(candidate -> {
            Set<String> other = terms(String.join(" ", candidate.title(), candidate.subject(),
                    candidate.subjectKey(), Objects.toString(candidate.newState(), "")));
            return terms.stream().anyMatch(other::contains);
        }).map(EventCandidate::candidateKey).toList();
    }

    private static Set<String> terms(String text) {
        return Arrays.stream(text.split("[^\\p{L}\\p{N}_]+"))
                .filter(term -> term.length() >= 2).collect(Collectors.toSet());
    }

    private Map<String, Object> articleView(Article article) {
        return Map.of("articleId", article.id(), "title", article.title(),
                "publishedAt", article.publishedAt(), "url", article.url(), "body", article.content());
    }

    private void writeArticle(String label, Object report) throws Exception {
        Path path = Path.of("pipeline-debug/manual-economic-flow-debug-" + safe(label) + ".json");
        Files.createDirectories(path.getParent()); json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), report);
    }

    private void seedMasters() {
        Map<TopicDomain, List<String>> topicKeys = Map.ofEntries(
                Map.entry(TopicDomain.REAL_ESTATE, List.of("MORTGAGE", "HOUSEHOLD_DEBT", "HOUSING_PRICE", "HOUSING_SUPPLY",
                        "JEONSE", "HOUSING_TRANSACTION", "UNSOLD_HOUSING", "DSR", "LTV", "REDEVELOPMENT")),
                Map.entry(TopicDomain.MONETARY, List.of("BASE_RATE", "MARKET_RATE", "LIQUIDITY")),
                Map.entry(TopicDomain.PRICE, List.of("CPI", "PPI", "IMPORT_PRICE", "OIL_PRICE")),
                Map.entry(TopicDomain.FX, List.of("EXCHANGE_RATE", "USD_KRW", "JPY_KRW", "FX_RESERVE")),
                Map.entry(TopicDomain.STOCK, List.of("KOSPI", "KOSDAQ", "SHORT_SELLING", "CAPITAL_MARKET", "FOREIGN_INVESTOR")),
                Map.entry(TopicDomain.MACRO, List.of("GDP", "ECONOMIC_GROWTH", "CONSUMPTION")),
                Map.entry(TopicDomain.EMPLOYMENT, List.of("EMPLOYMENT", "UNEMPLOYMENT")),
                Map.entry(TopicDomain.TRADE, List.of("EXPORT", "IMPORT")),
                Map.entry(TopicDomain.INDUSTRY, List.of("SEMICONDUCTOR", "AUTOMOBILE", "BATTERY", "SHIPBUILDING", "DEFENSE")),
                Map.entry(TopicDomain.FINANCE, List.of("LOAN_RATE", "DEPOSIT_RATE", "CORPORATE_LOAN")),
                Map.entry(TopicDomain.FISCAL, List.of("HOUSING_TAX")));
        topicKeys.forEach((domain, keys) -> keys.forEach(key -> {
            TopicEntity topic = new TopicEntity(); topic.setTopicKey(key); topic.setName(key); topic.setDomain(domain);
            topics.save(topic);
        }));
        slot("RATE_DECISION", "RATE_HIKE", "RATE_HOLD", "RATE_CUT");
        slot("POLICY_STANCE", "HAWKISH", "DOVISH", "CAUTIOUS_ON_CUT", "NEUTRAL");
        slot("INFLATION_STATUS", "ABOVE_TARGET", "NEAR_TARGET", "BELOW_TARGET");
        slot("EXCHANGE_RATE_DIRECTION", "RISING", "FALLING", "FLAT");
        slot("MARKET_DIRECTION", "RISING", "FALLING", "STABLE");
        slot("CORPORATE_STRUCTURE_STATUS", "SPLIT_ANNOUNCED", "SPLIT_APPROVED", "SPLIT_COMPLETED", "SPLIT_CANCELLED");
    }

    private void slot(String key, String... valueKeys) {
        EconomicSlotEntity slot = new EconomicSlotEntity(); slot.setSlotKey(key); slot.setName(key); slots.save(slot);
        for (String valueKey : valueKeys) {
            EconomicSlotValueEntity value = new EconomicSlotValueEntity(); value.setSlot(slot);
            value.setValueKey(valueKey); value.setName(valueKey); values.save(value);
        }
    }

    private static String idFrom(String url, String fallback) {
        var matcher = ARTICLE_ID.matcher(Objects.toString(url, ""));
        return matcher.find() ? matcher.group() : safe(fallback);
    }
    private static String sourceName(String url) { return url != null && url.contains("yna.co.kr") ? "연합뉴스" : "manual"; }
    private static String safe(String value) { return value.replaceAll("[^A-Za-z0-9가-힣._-]", "_"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    record Manifest(List<ManifestArticle> articles) {}
    record ManifestArticle(String label, String url, String articleId, String title,
            String publishedAt, String body) {}
    record OutputNode(Long id, String title, LocalDate eventDate) {}
}
