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
import com.economicbriefing.economicflow.EventRelationType;
import com.economicbriefing.economicflow.FlowClaimCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;

final class EconomicFlowJudge {
    private static final String SYSTEM_PROMPT = """
            검증된 relation 중 장기 경제흐름 그래프에 저장할 관계만 고릅니다. 근거 일치 여부는 재검증하지 마세요.
            from이 경제 변수·경제주체의 상태·행동·정책·제약이고, to의 금리·환율·가격·수요·공급·생산·투자·소비·
            수주·매출·비용·고용·자금조달·유동성·정책·리스크·시장 행동을 실제로 변화시키면 포함할 수 있습니다.
            미래·전망 관계도 이러한 전달경로가 있으면 포함합니다. 특정 기업 관계도 실제 수주·매출 변화면 포함합니다.
            국가·국제기구가 자원·안보·전략적 위치의 명시된 중요성에 따라 위협·병합 시도·투자·관리·외교 행동을 하는 관계도 포함합니다.
            단, 그 조건이 해당 행동의 동기·목적·배경으로 기사 근거에 직접 연결된 경우만 포함하고, 단순 병렬 언급은 제외하세요.
            단순 주목·조명·거론·평가·전망 문구·일정·정의·순위·수치 재표현은 제외하세요.
            같은 결과에 대해 일회성 인물 발언 relation과 더 일반적인 경제 상태 relation이 함께 있으면 경제 상태 relation을
            우선하고 발언 relation은 제외하세요. 다만 기준금리 결정·규제 시행·재정지출 같은 실제 정책 행동은 포함합니다.
            서로 다른 금리·환율·수요·공급 같은 독립 경제 변수가 같은 결과에 각각 영향을 준 관계는 중복이 아니므로
            각 전달경로를 유지하세요. 일반 경제 상태 우선 규칙을 시장 변수 relation 제거에 적용하지 마세요.
            relation을 생성하거나 고쳐 쓰지 말고 입력 index마다 순서대로 include를 판정하세요.
            """;
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"decisions":{"type":"array","items":{
            "type":"object","additionalProperties":false,"properties":{"index":{"type":"integer"},"include":{"type":"boolean"}},
            "required":["index","include"]}}},"required":["decisions"]}
            """;

    private final OpenAiClient client;
    private final ObjectMapper json;
    private final OpenAiProperties openAi;
    private final AppProperties app;

    EconomicFlowJudge(OpenAiClient client, ObjectMapper json, OpenAiProperties openAi, AppProperties app) {
        this.client = client; this.json = json; this.openAi = openAi; this.app = app;
    }

    AnalyzerDraftBundle judge(AnalyzerDraftBundle bundle) {
        List<Entry> entries = entries(bundle.analysis());
        if (entries.isEmpty()) return bundle;
        Set<Integer> included = RetryExecutor.execute(() -> call(entries), app.retry());
        var claimsByArticle = new ArrayList<List<FlowClaimCandidate>>();
        for (int i = 0; i < bundle.analysis().articles().size(); i++) claimsByArticle.add(new ArrayList<>());
        entries.stream().filter(entry -> included.contains(entry.index())).forEach(entry -> {
            var relation = entry.relation();
            claimsByArticle.get(entry.article()).add(new FlowClaimCandidate(
                    relation.from(), relation.to(), flowType(relation.relationType())));
        });
        var flows = new ArrayList<ArticleEconomicFlow>();
        for (int i = 0; i < bundle.economicFlows().size(); i++) {
            flows.add(new ArticleEconomicFlow(bundle.economicFlows().get(i).article(),
                    new EconomicFlowExtraction(claimsByArticle.get(i).stream().distinct().toList())));
        }
        return new AnalyzerDraftBundle(bundle.analysis(), bundle.eventCandidates(), bundle.eventRelations(),
                List.copyOf(flows));
    }

    private Set<Integer> call(List<Entry> entries) {
        try {
            String input = json.writeValueAsString(entries.stream().map(entry -> new Input(entry.index(),
                    entry.relation().from(), entry.relation().to(), entry.relation().relationType().name(),
                    entry.relation().articleExplanation(), entry.relation().evidenceType().name())).toList());
            String raw = client.completeWithSchema(SYSTEM_PROMPT, input,
                    openAi.model(), 0, "economic_flow_judge", SCHEMA);
            Response response = json.readValue(raw, Response.class);
            if (response.decisions() == null || response.decisions().size() != entries.size()) {
                throw new IllegalArgumentException("Economic Flow Judge result count mismatch");
            }
            Set<Integer> seen = new HashSet<>(); Set<Integer> included = new HashSet<>();
            for (Decision decision : response.decisions()) {
                if (decision.index() < 0 || decision.index() >= entries.size() || !seen.add(decision.index())) {
                    throw new IllegalArgumentException("Invalid Economic Flow Judge index");
                }
                if (decision.include()) included.add(decision.index());
            }
            return included;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to judge economic flow relations", e);
        }
    }

    private static List<Entry> entries(ArticleAnalysisResponse response) {
        List<Entry> entries = new ArrayList<>();
        for (int a = 0; a < response.articles().size(); a++) {
            for (var issue : response.articles().get(a).issues()) {
                for (var relation : issue.relations()) entries.add(new Entry(entries.size(), a, relation));
            }
        }
        return entries;
    }

    private static EventRelationType flowType(ArticleAnalysisResponse.RelationType type) {
        return switch (type) {
            case PURPOSE -> EventRelationType.PURPOSE;
            case CONDITION -> EventRelationType.CONDITION;
            case MOTIVATION -> EventRelationType.MOTIVATION;
            default -> EventRelationType.CAUSE;
        };
    }

    private record Entry(int index, int article, ArticleAnalysisResponse.Relation relation) {}
    private record Input(int index, String from, String to, String relationType, String evidence, String evidenceType) {}
    private record Response(List<Decision> decisions) {}
    private record Decision(int index, boolean include) {}
}
