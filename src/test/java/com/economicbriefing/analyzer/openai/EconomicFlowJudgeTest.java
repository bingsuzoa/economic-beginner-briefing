package com.economicbriefing.analyzer.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EconomicFlowJudgeTest {
    @Test
    void shouldSelectPredictionTransmissionButExcludeOutlookLabel() {
        ObjectMapper json = new ObjectMapper(); AtomicInteger calls = new AtomicInteger(); String[] system = new String[1];
        OpenAiProperties openAi = new OpenAiProperties(
                "test", "main", 0, Duration.ofSeconds(1), 1, "cheap", "cheap");
        OpenAiClient client = new OpenAiClient(openAi, json) {
            @Override public String completeWithSchema(String systemPrompt, String userPrompt, String model,
                    double temperature, String schemaName, String schema) {
                calls.incrementAndGet(); system[0] = systemPrompt;
                return """
                        {"decisions":[{"index":0,"include":false},{"index":1,"include":true},
                        {"index":2,"include":true},{"index":3,"include":false},{"index":4,"include":true},
                        {"index":5,"include":true}]}
                        """;
            }
        };
        var issue = new ArticleAnalysisResponse.Issue("건설", List.of(), List.of(), List.of(
                relation("건설업 전망", "밝음", "건설업 전망은 밝다."),
                relation("전력 수요 증가", "발전설비 역량 보유 건설사 수혜",
                        "전력 수요 증가로 발전설비 역량을 가진 건설사의 수혜가 예상된다."),
                relation("기준금리 인상 가능성", "국고채 금리 상승", "금리 인상 가능성으로 국고채 금리가 올랐다."),
                relation("중앙은행 총재 매파 발언", "국고채 금리 상승", "총재 발언으로 국고채 금리가 올랐다."),
                relation("중앙은행 기준금리 인상", "시장금리 상승", "기준금리를 인상해 시장금리가 올랐다."),
                relation("기준금리 인상 가능성", "국고채 금리 상승", "금리 인상 가능성으로 국고채 금리가 올랐다.")),
                List.of(), List.of());
        var analysis = new ArticleAnalysisResponse(List.of(
                new ArticleAnalysisResponse.ArticleAnalysis("article-1", List.of(issue))));
        var context = new ArticleContext("article-1", "제목", "url", OffsetDateTime.now(), "본문");
        var bundle = new OpenAiNewsAnalyzer.AnalyzerDraftBundle(analysis, List.of(), List.of(),
                List.of(new ArticleEconomicFlow(context, new EconomicFlowExtraction(List.of()))));
        var app = new AppProperties(false, null,
                new AppProperties.RetryProperties(1, Duration.ZERO, Duration.ZERO),
                null, null, null, null, null);

        var judged = new EconomicFlowJudge(client, json, openAi, app).judge(bundle);

        assertEquals(1, calls.get());
        assertEquals("전력 수요 증가",
                judged.economicFlows().getFirst().flow().flowClaims().getFirst().from());
        assertEquals(List.of("전력 수요 증가", "기준금리 인상 가능성", "중앙은행 기준금리 인상"),
                judged.economicFlows().getFirst().flow().flowClaims().stream()
                        .map(com.economicbriefing.economicflow.FlowClaimCandidate::from).toList());
        assertEquals(true, system[0].contains("일회성 인물 발언"));
        assertEquals(true, system[0].contains("자원·안보·전략적 위치"));
    }

    private static ArticleAnalysisResponse.Relation relation(String from, String to, String evidence) {
        return new ArticleAnalysisResponse.Relation(from, to,
                ArticleAnalysisResponse.RelationType.EXPECTED_EFFECT, evidence,
                ArticleAnalysisResponse.StatementType.PREDICTION, null);
    }
}
