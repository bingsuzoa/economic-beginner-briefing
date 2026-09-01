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

final class RelationValidator {
    private static final String SYSTEM_PROMPT = """
            기존 경제 relation의 원문 근거 일치 여부만 검증합니다. 관계를 생성·수정·보완하지 마세요.
            evidence가 from을 to의 원인·조건·목적·대응·경제적 영향으로 직접 표현하면 VALID입니다.
            미래 영향도 직접 표현됐다면 VALID이며 endpoint의 간결한 요약은 원문과 글자까지 같을 필요가 없습니다.
            예: "AIDC 착공이 증가하면서 건설사 수혜가 전망된다"는 AIDC 착공 증가 → 건설사 수혜를 VALID로 판정합니다.
            단순 동시 등장, 병렬절, 관심·평가 서술, 경제 상식으로만 가능한 연결은 INVALID입니다.
            예: "데이터센터를 건설 중이고 원전 경험이 있어 원전 수주 후보로 꼽힌다"에서
            데이터센터 건설 → 원전 수주 후보는 INVALID입니다. 결과를 직접 설명하는 절이 원전 경험이기 때문입니다.
            또한 "데이터센터 착공 관측이 나오며 원전 EPC 권한을 가진 만큼 원전 확대 시 성장이 예상된다"에서
            데이터센터 착공 → 원전 확대 시 성장은 INVALID입니다. 결과의 직접 조건은 원전 EPC 권한과 원전 확대입니다.
            각 index를 입력 순서대로 하나씩 빠짐없이 판정하세요.
            """;
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"decisions":{"type":"array","items":{
            "type":"object","additionalProperties":false,"properties":{"index":{"type":"integer"},"valid":{"type":"boolean"}},
            "required":["index","valid"]}}},"required":["decisions"]}
            """;

    private final OpenAiClient client;
    private final ObjectMapper json;
    private final OpenAiProperties openAi;
    private final AppProperties app;

    RelationValidator(OpenAiClient client, ObjectMapper json, OpenAiProperties openAi, AppProperties app) {
        this.client = client;
        this.json = json;
        this.openAi = openAi;
        this.app = app;
    }

    AnalyzerDraftBundle validate(AnalyzerDraftBundle bundle) {
        List<Entry> entries = entries(bundle.analysis());
        if (entries.isEmpty()) return bundle;
        Set<Integer> valid = RetryExecutor.execute(() -> call(entries), app.retry());
        ArticleAnalysisResponse analysis = filterAnalysis(bundle.analysis(), entries, valid);
        return new AnalyzerDraftBundle(analysis, bundle.eventCandidates(), bundle.eventRelations(),
                filterFlows(bundle.economicFlows(), analysis));
    }

    private Set<Integer> call(List<Entry> entries) {
        try {
            String input = json.writeValueAsString(entries.stream().map(entry -> new Input(
                    entry.index(), entry.relation().from(), entry.relation().to(),
                    entry.relation().relationType().name(), entry.relation().articleExplanation(),
                    entry.relation().evidenceType().name())).toList());
            String raw = client.completeWithSchema(SYSTEM_PROMPT, input,
                    openAi.model(), 0, "relation_validation", SCHEMA);
            Response response = json.readValue(raw, Response.class);
            if (response.decisions() == null || response.decisions().size() != entries.size()) {
                throw new IllegalArgumentException("Relation Validator result count mismatch");
            }
            Set<Integer> seen = new HashSet<>();
            Set<Integer> valid = new HashSet<>();
            for (Decision decision : response.decisions()) {
                if (decision.index() < 0 || decision.index() >= entries.size() || !seen.add(decision.index())) {
                    throw new IllegalArgumentException("Invalid Relation Validator index");
                }
                if (decision.valid()) valid.add(decision.index());
            }
            return valid;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to validate analyzer relations", e);
        }
    }

    private static List<Entry> entries(ArticleAnalysisResponse response) {
        List<Entry> result = new ArrayList<>();
        for (int a = 0; a < response.articles().size(); a++) {
            var article = response.articles().get(a);
            for (int i = 0; i < article.issues().size(); i++) {
                var relations = article.issues().get(i).relations();
                for (int r = 0; r < relations.size(); r++) {
                    result.add(new Entry(result.size(), a, i, r, relations.get(r)));
                }
            }
        }
        return result;
    }

    private static ArticleAnalysisResponse filterAnalysis(
            ArticleAnalysisResponse response, List<Entry> entries, Set<Integer> valid) {
        Set<Position> allowed = new HashSet<>();
        entries.stream().filter(entry -> valid.contains(entry.index()))
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

    private static String key(String from, String to) {
        return from.replaceAll("\\s+", "").trim() + "→" + to.replaceAll("\\s+", "").trim();
    }

    private record Entry(int index, int article, int issue, int relationIndex,
            ArticleAnalysisResponse.Relation relation) {}
    private record Position(int article, int issue, int relation) {}
    private record Input(int index, String from, String to, String relationType, String evidence, String evidenceType) {}
    private record Response(List<Decision> decisions) {}
    private record Decision(int index, boolean valid) {}
}
