package com.economicbriefing.analyzer.openai;

import java.util.List;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.dto.RetrievalRouterResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetrievalRouterValidationTest {

    @Test
    void promptChecksTheWholeIssueBeforeCreatingWhy() {
        assertTrue(com.economicbriefing.analyzer.openai.prompt.RetrievalRouterPromptBuilder.SYSTEM_PROMPT
                .contains("여러 필드에 걸쳐 원인→작동 방식→결과가 설명돼 있으면"));
        assertTrue(com.economicbriefing.analyzer.openai.prompt.RetrievalRouterPromptBuilder.SYSTEM_PROMPT
                .contains("의심스러우면 요청하지 마세요"));
        assertTrue(com.economicbriefing.analyzer.openai.prompt.RetrievalRouterPromptBuilder.SYSTEM_PROMPT
                .contains("broad SYSTEM request를 피하세요"));
        assertTrue(com.economicbriefing.analyzer.openai.prompt.RetrievalRouterPromptBuilder.SYSTEM_PROMPT
                .contains("연결 사실과 작동 원리 설명은 다릅니다"));
        assertTrue(com.economicbriefing.analyzer.openai.prompt.RetrievalRouterPromptBuilder.SYSTEM_PROMPT
                .contains("A→중간 단계→B"));
    }

    private final ArticleAnalysisResponse baseline = new ArticleAnalysisResponse(List.of(
            new ArticleAnalysisResponse.ArticleAnalysis("article-1", List.of(
                    new ArticleAnalysisResponse.Issue(
                            "ISA 개편", List.of("한도가 바뀐다"), List.of(), List.of(), List.of(), List.of("ISA"))))));

    @Test
    void acceptsReferenceToActualAnalyzerField() {
        assertDoesNotThrow(() -> OpenAiNewsAnalyzer.validateRouterResult(route("issues[0].keyTerms[0]"), baseline));
    }

    @Test
    void rejectsReferenceOutsideAnalyzerResult() {
        assertThrows(IllegalArgumentException.class,
                () -> OpenAiNewsAnalyzer.validateRouterResult(route("issues[0].keyTerms[1]"), baseline));
    }

    @Test
    void rejectsNeedsRetrievalThatDisagreesWithRequests() {
        var requests = route("issues[0].keyTerms[0]")
                .articles().get(0).issues().get(0).requests();
        var response = new RetrievalRouterResponse(List.of(
                new RetrievalRouterResponse.ArticleRoute("article-1", List.of(
                        new RetrievalRouterResponse.IssueRoute("ISA 개편", false, requests)))));
        assertThrows(IllegalArgumentException.class,
                () -> OpenAiNewsAnalyzer.validateRouterResult(response, baseline));
    }

    private RetrievalRouterResponse route(String reference) {
        var request = new RetrievalRouterResponse.RetrievalRequest(
                RetrievalRouterResponse.GapType.TERM,
                "ISA", "ISA란 무엇인가", reference, "핵심 제도 용어",
                RetrievalRouterResponse.Priority.HIGH);
        return new RetrievalRouterResponse(List.of(
                new RetrievalRouterResponse.ArticleRoute("article-1", List.of(
                        new RetrievalRouterResponse.IssueRoute("ISA 개편", true, List.of(request))))));
    }
}
