package com.economicbriefing.analyzer.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.economicbriefing.analyzer.openai.OpenAiNewsAnalyzer.AnalyzerDraftBundle;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.economicflow.ArticleContext;
import com.economicbriefing.economicflow.ArticleEconomicFlow;
import com.economicbriefing.economicflow.EconomicFlowExtraction;
import com.economicbriefing.economicflow.EventRelationType;
import com.economicbriefing.economicflow.FlowClaimCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;

class RelationDeduplicatorTest {

    @Test
    void removesOnlyRelationsTheDeduplicatorMarksAsDuplicateFromAnalysisAndFlow() {
        var client = mock(OpenAiClient.class);
        when(client.completeWithSchema(anyString(), anyString(), anyString(), anyDouble(), anyString(), anyString()))
                .thenReturn("""
                        {"decisions":[{"index":0,"keep":true},{"index":1,"keep":false},{"index":2,"keep":true}]}
                        """);

        var result = deduplicator(client).deduplicate(bundle());

        var relations = result.analysis().articles().getFirst().issues().getFirst().relations();
        assertEquals(List.of("반도체 수출 증가", "환율 상승"), relations.stream()
                .map(ArticleAnalysisResponse.Relation::from).toList());
        assertEquals(List.of("반도체 수출 증가", "환율 상승"), result.economicFlows().getFirst().flow().flowClaims().stream()
                .map(FlowClaimCandidate::from).toList());
    }

    private RelationDeduplicator deduplicator(OpenAiClient client) {
        var app = new AppProperties(false, null,
                new AppProperties.RetryProperties(1, Duration.ZERO, Duration.ZERO),
                null, null, null, null, null);
        var openAi = new OpenAiProperties("test", "gpt-4o", 0, Duration.ofSeconds(1), 1);
        return new RelationDeduplicator(client, new ObjectMapper(), openAi, app);
    }

    private AnalyzerDraftBundle bundle() {
        var relations = List.of(
                relation("반도체 수출 증가", "전체 수출 증가"),
                relation("반도체 중심 수출 성장", "수출 실적 개선"),
                relation("환율 상승", "수입물가 상승"));
        var analysis = new ArticleAnalysisResponse(List.of(new ArticleAnalysisResponse.ArticleAnalysis("article-1",
                List.of(new ArticleAnalysisResponse.Issue("수출", List.of(), List.of(), relations, List.of(), List.of())))));
        var claims = List.of(
                new FlowClaimCandidate("반도체 수출 증가", "전체 수출 증가", EventRelationType.CAUSE),
                new FlowClaimCandidate("반도체 중심 수출 성장", "수출 실적 개선", EventRelationType.CAUSE),
                new FlowClaimCandidate("환율 상승", "수입물가 상승", EventRelationType.CAUSE));
        var flow = new ArticleEconomicFlow(new ArticleContext("article-1", "제목", "", OffsetDateTime.now(), ""),
                new EconomicFlowExtraction(claims));
        return new AnalyzerDraftBundle(analysis, List.of(), List.of(), List.of(flow));
    }

    private ArticleAnalysisResponse.Relation relation(String from, String to) {
        return new ArticleAnalysisResponse.Relation(from, to,
                ArticleAnalysisResponse.RelationType.CAUSE_OR_RESULT, "기사 근거",
                ArticleAnalysisResponse.StatementType.FACT, null);
    }
}
