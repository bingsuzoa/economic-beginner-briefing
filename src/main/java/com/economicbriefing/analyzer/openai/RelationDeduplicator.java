package com.economicbriefing.analyzer.openai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.economicbriefing.analyzer.openai.OpenAiNewsAnalyzer.AnalyzerDraftBundle;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.util.RetryExecutor;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.economicflow.ArticleEconomicFlow;
import com.economicbriefing.economicflow.EconomicFlowExtraction;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Keeps one representative for relations that express the same causal mechanism.
 * The result is shared by graph ingestion and the article presenter, so the two
 * views cannot disagree about which relationships exist.
 */
final class RelationDeduplicator {
    private static final String SYSTEM_PROMPT = """
            같은 article 안의 검증된 경제 relation들을 의미 단위로 중복 제거합니다. 관계를 생성·수정·보완하지 마세요.
            표면적인 from/to 표현이 달라도 동일한 원인 메커니즘과 전달경로를 말하면 중복입니다.
            예: "반도체 수출 증가 → 전체 수출 증가"와 "반도체 중심 수출 성장 → 수출 실적 개선"은 같은 메커니즘일 수 있습니다.
            중복 그룹마다 기사 근거가 가장 직접적이고 초보자가 이해하기 쉬운 대표 relation 하나만 keep=true로 남기세요.
            금리·환율·수요·공급·정책처럼 서로 다른 독립 경제 변수가 같은 결과에 영향을 주는 관계는 중복이므로 제거하면 안 됩니다.
            각 index를 입력 순서대로 하나씩 빠짐없이 keep 판정하세요.
            """;
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"decisions":{"type":"array","items":{
            "type":"object","additionalProperties":false,"properties":{"index":{"type":"integer"},"keep":{"type":"boolean"}},
            "required":["index","keep"]}}},"required":["decisions"]}
            """;

    private final OpenAiClient client;
    private final ObjectMapper json;
    private final OpenAiProperties openAi;
    private final AppProperties app;

    RelationDeduplicator(OpenAiClient client, ObjectMapper json, OpenAiProperties openAi, AppProperties app) {
        this.client = client;
        this.json = json;
        this.openAi = openAi;
        this.app = app;
    }

    AnalyzerDraftBundle deduplicate(AnalyzerDraftBundle bundle) {
        List<Entry> entries = entries(bundle.analysis());
        if (entries.size() < 2) return bundle;
        Set<Integer> kept = RetryExecutor.execute(() -> call(entries), app.retry());
        ArticleAnalysisResponse analysis = filterAnalysis(bundle.analysis(), entries, kept);
        return new AnalyzerDraftBundle(analysis, bundle.eventCandidates(), bundle.eventRelations(),
                filterFlows(bundle.economicFlows(), analysis));
    }

    private Set<Integer> call(List<Entry> entries) {
        try {
            String input = json.writeValueAsString(entries.stream().map(entry -> new Input(
                    entry.index(), entry.articleId(), entry.relation().from(), entry.relation().to(),
                    entry.relation().relationType().name(), entry.relation().articleExplanation(),
                    entry.relation().evidenceType().name())).toList());
            String raw = client.completeWithSchema(SYSTEM_PROMPT, input,
                    openAi.model(), 0, "relation_deduplication", SCHEMA);
            Response response = json.readValue(raw, Response.class);
            if (response.decisions() == null || response.decisions().size() != entries.size()) {
                throw new IllegalArgumentException("Relation Deduplicator result count mismatch");
            }
            Set<Integer> seen = new HashSet<>();
            Set<Integer> kept = new HashSet<>();
            for (Decision decision : response.decisions()) {
                if (decision.index() < 0 || decision.index() >= entries.size() || !seen.add(decision.index())) {
                    throw new IllegalArgumentException("Invalid Relation Deduplicator index");
                }
                if (decision.keep()) kept.add(decision.index());
            }
            return kept;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deduplicate analyzer relations", e);
        }
    }

    private static List<Entry> entries(ArticleAnalysisResponse response) {
        List<Entry> result = new ArrayList<>();
        for (int a = 0; a < response.articles().size(); a++) {
            var article = response.articles().get(a);
            for (int i = 0; i < article.issues().size(); i++) {
                for (int r = 0; r < article.issues().get(i).relations().size(); r++) {
                    result.add(new Entry(result.size(), a, i, r, article.articleId(),
                            article.issues().get(i).relations().get(r)));
                }
            }
        }
        return result;
    }

    private static ArticleAnalysisResponse filterAnalysis(
            ArticleAnalysisResponse response, List<Entry> entries, Set<Integer> kept) {
        Set<Position> allowed = new HashSet<>();
        entries.stream().filter(entry -> kept.contains(entry.index()))
                .map(entry -> new Position(entry.article(), entry.issue(), entry.relationIndex())).forEach(allowed::add);
        var articles = new ArrayList<ArticleAnalysisResponse.ArticleAnalysis>();
        for (int a = 0; a < response.articles().size(); a++) {
            var article = response.articles().get(a);
            var issues = new ArrayList<ArticleAnalysisResponse.Issue>();
            for (int i = 0; i < article.issues().size(); i++) {
                var issue = article.issues().get(i);
                var relations = new ArrayList<ArticleAnalysisResponse.Relation>();
                for (int r = 0; r < issue.relations().size(); r++) {
                    if (allowed.contains(new Position(a, i, r))) relations.add(issue.relations().get(r));
                }
                issues.add(new ArticleAnalysisResponse.Issue(issue.name(), issue.mainFacts(), issue.changes(),
                        List.copyOf(relations), issue.statements(), issue.keyTerms()));
            }
            articles.add(new ArticleAnalysisResponse.ArticleAnalysis(article.articleId(), List.copyOf(issues)));
        }
        return new ArticleAnalysisResponse(List.copyOf(articles));
    }

    private static List<ArticleEconomicFlow> filterFlows(
            List<ArticleEconomicFlow> flows, ArticleAnalysisResponse analysis) {
        var result = new ArrayList<ArticleEconomicFlow>();
        for (int a = 0; a < flows.size(); a++) {
            Set<String> endpoints = new HashSet<>();
            analysis.articles().get(a).issues().stream().flatMap(issue -> issue.relations().stream())
                    .map(relation -> key(relation.from(), relation.to())).forEach(endpoints::add);
            var flow = flows.get(a);
            var claims = flow.flow().flowClaims().stream()
                    .filter(claim -> endpoints.contains(key(claim.from(), claim.to()))).toList();
            result.add(new ArticleEconomicFlow(flow.article(), new EconomicFlowExtraction(claims)));
        }
        return List.copyOf(result);
    }

    private static String key(String from, String to) { return from + "\u0000" + to; }

    private record Entry(int index, int article, int issue, int relationIndex, String articleId,
                         ArticleAnalysisResponse.Relation relation) {}
    private record Position(int article, int issue, int relation) {}
    private record Input(int index, String articleId, String from, String to, String relationType,
                         String evidence, String evidenceType) {}
    private record Response(List<Decision> decisions) {}
    private record Decision(int index, boolean keep) {}
}
