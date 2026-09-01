package com.economicbriefing.analyzer.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.economicflow.ArticleContext;
import com.economicbriefing.economicflow.ArticleEconomicFlow;
import com.economicbriefing.economicflow.EconomicFlowExtraction;
import com.economicbriefing.economicflow.EventRelationType;
import com.economicbriefing.economicflow.FlowClaimCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RelationValidatorTest {
    @Test
    void shouldBatchValidateDirectEvidenceAndFilterAnalyzerAndFlow() {
        ObjectMapper json = new ObjectMapper();
        AtomicInteger calls = new AtomicInteger();
        String[] prompt = new String[1];
        OpenAiProperties openAi = new OpenAiProperties(
                "test", "main", 0, Duration.ofSeconds(1), 1, "cheap", "cheap");
        OpenAiClient client = new OpenAiClient(openAi, json) {
            @Override
            public String completeWithSchema(String systemPrompt, String userPrompt, String model,
                    double temperature, String schemaName, String schema) {
                calls.incrementAndGet(); prompt[0] = userPrompt;
                assertEquals("main", model);
                return """
                        {"decisions":[{"index":0,"valid":false},{"index":1,"valid":true},
                        {"index":2,"valid":true},{"index":3,"valid":false}]}
                        """;
            }
        };
        var app = new AppProperties(false, null,
                new AppProperties.RetryProperties(1, Duration.ZERO, Duration.ZERO),
                null, null, null, null, null);
        List<ArticleAnalysisResponse.Relation> relations = List.of(
                relation("데이터센터 건설", "해외 원전 수주 기대",
                        "A사는 데이터센터를 건설하고 있고, 원전 사업 경험이 있어 해외 원전 수주 후보로 꼽힌다."),
                relation("데이터센터 착공 증가", "건설사 신규 수주 확대",
                        "데이터센터 착공 증가로 건설사의 신규 수주 확대가 예상된다."),
                relation("비주택 수주 증가 기대", "투자자금 유입",
                        "비주택 수주 증가 기대가 투자자금 유입에 영향을 미쳤다."),
                relation("원전 확대", "건설업 역할 주목",
                        "원전 확대에 따라 건설업의 역할이 주목받고 있다."));
        var analysis = new ArticleAnalysisResponse(List.of(new ArticleAnalysisResponse.ArticleAnalysis(
                "article-1", List.of(new ArticleAnalysisResponse.Issue(
                        "건설", List.of(), List.of(), relations, List.of(), List.of())))));
        var claims = relations.stream().map(relation -> new FlowClaimCandidate(
                relation.from(), relation.to(), EventRelationType.CAUSE)).toList();
        var bundle = new OpenAiNewsAnalyzer.AnalyzerDraftBundle(analysis, List.of(), List.of(), List.of(
                new ArticleEconomicFlow(new ArticleContext(
                        "article-1", "제목", "url", OffsetDateTime.now(), "기사 본문"),
                        new EconomicFlowExtraction(claims))));

        var validated = new RelationValidator(client, json, openAi, app).validate(bundle);

        assertEquals(1, calls.get());
        assertEquals(List.of("데이터센터 착공 증가", "비주택 수주 증가 기대"),
                validated.analysis().articles().getFirst().issues().getFirst().relations().stream()
                        .map(ArticleAnalysisResponse.Relation::from).toList());
        assertEquals(2, validated.economicFlows().getFirst().flow().flowClaims().size());
        assertTrue(prompt[0].contains("데이터센터 착공 증가로 건설사의 신규 수주 확대가 예상된다."));
        assertFalse(prompt[0].contains("기사 전체 본문"));
    }

    private static ArticleAnalysisResponse.Relation relation(String from, String to, String evidence) {
        return new ArticleAnalysisResponse.Relation(from, to,
                ArticleAnalysisResponse.RelationType.CLAIMED_EFFECT, evidence,
                ArticleAnalysisResponse.StatementType.PREDICTION, null);
    }
}
