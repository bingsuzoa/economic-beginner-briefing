package com.economicbriefing.analyzer.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.dto.ArticlePresentationResponse;
import com.economicbriefing.classifier.entity.RelationExplanationAssetEntity;
import com.economicbriefing.classifier.repository.RelationExplanationAssetRepository;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.economicflow.ArticleContext;
import com.economicbriefing.economicflow.ArticleEconomicFlow;
import com.economicbriefing.economicflow.EconomicFlowExtraction;
import com.economicbriefing.economicflow.EconomicPrincipleRetriever;
import com.economicbriefing.economicflow.EventRelationType;
import com.economicbriefing.economicflow.FlowClaimCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArticlePresenterTest {
    private static final String ARTICLE_ID = "article-1";
    private static final String QUERY = "‘원화 강세 → 국고채 금리 하락’는 왜 이어졌나요?";

    @Test
    void acceptsOnlyCandidateChunkIdsAndReturnsPresentation() {
        assertEquals(ARTICLE_ID + ":0", ArticlePresenter.flowRequests(analysis()).getFirst().id());
        var client = mock(OpenAiClient.class);
        when(client.complete(anyString(), anyString(), anyDouble())).thenReturn("""
                {"articles":[{"articleId":"article-1","displayTitle":"국고채 금리 하락","summary":["국고채 금리가 내렸다.","원화 강세가 영향을 미쳤다."],"whatHappened":"국고채 금리가 하락했다.","whyExplanations":[{"requestId":"article-1:0","question":"‘원화 강세 → 국고채 금리 하락’는 왜 이어졌나요?","explanation":"원화 가치가 오르면 원화 채권 수요가 늘어 채권 금리가 내릴 수 있다.","explanationKind":"GENERAL_PRINCIPLE","usedPrincipleChunkIds":["direct"]}]}]}
                """);

        var result = presenter(client).present(analysis(), flows(), principles());

        assertEquals("direct", result.getFirst().whyExplanations().getFirst().usedPrincipleChunkIds().getFirst());
        assertEquals("원화 강세", ((FlowClaimCandidate) result.getFirst().flowClaims().getFirst()).from());
    }

    @Test
    void rejectsChunkIdOutsideRequestCandidates() {
        var client = mock(OpenAiClient.class);
        when(client.complete(anyString(), anyString(), anyDouble())).thenReturn("""
                {"articles":[{"articleId":"article-1","displayTitle":"제목","summary":["요약 하나","요약 둘"],"whatHappened":"내용","whyExplanations":[{"requestId":"article-1:0","question":"‘원화 강세 → 국고채 금리 하락’는 왜 이어졌나요?","explanation":"설명","explanationKind":"GENERAL_PRINCIPLE","usedPrincipleChunkIds":["unknown"]}]}]}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> presenter(client).present(analysis(), List.of(), principles()));
    }

    @Test
    void acceptsGeneralExplanationWithoutPrincipleChunk() {
        var client = mock(OpenAiClient.class);
        when(client.complete(anyString(), anyString(), anyDouble())).thenReturn("""
                {"articles":[{"articleId":"article-1","displayTitle":"제목","summary":["요약 하나","요약 둘"],"whatHappened":"내용","whyExplanations":[{"requestId":"article-1:0","question":"‘원화 강세 → 국고채 금리 하락’는 왜 이어졌나요?","explanation":"일반적인 경제 설명","explanationKind":"GENERAL_PRINCIPLE","usedPrincipleChunkIds":[]}]}]}
                """);

        var result = presenter(client).present(analysis(), List.of(), principles());

        assertEquals("일반적인 경제 설명", result.getFirst().whyExplanations().getFirst().explanation());
    }

    @Test
    void reusesCachedGeneralPrincipleExplanation() {
        var client = mock(OpenAiClient.class);
        when(client.complete(anyString(), anyString(), anyDouble())).thenReturn("""
                {"articles":[{"articleId":"article-1","displayTitle":"제목","summary":["요약 하나","요약 둘"],"whatHappened":"내용","whyExplanations":[]}]}
                """);
        var assets = mock(RelationExplanationAssetRepository.class);
        var asset = new RelationExplanationAssetEntity(); asset.setExplanation("저장된 일반 원리 설명");
        asset.setPrincipleChunkIds("[]");
        when(assets.findFirstByRelationKeyAndExplanationKindOrderByIdDesc(anyString(), anyString())).thenReturn(Optional.of(asset));

        var result = presenter(client, assets).present(analysis(), List.of(), principles());

        assertEquals("저장된 일반 원리 설명", result.getFirst().whyExplanations().getFirst().explanation());
        assertEquals(ArticlePresentationResponse.ExplanationKind.GENERAL_PRINCIPLE,
                result.getFirst().whyExplanations().getFirst().explanationKind());
    }

    private ArticlePresenter presenter(OpenAiClient client) {
        return presenter(client, null);
    }

    private ArticlePresenter presenter(OpenAiClient client, RelationExplanationAssetRepository assets) {
        var app = new AppProperties(false, null,
                new AppProperties.RetryProperties(1, Duration.ZERO, Duration.ZERO),
                null, null, null, null, null);
        return new ArticlePresenter(client, new ObjectMapper(), app, assets);
    }

    private ArticleAnalysisResponse analysis() {
        return new ArticleAnalysisResponse(List.of(new ArticleAnalysisResponse.ArticleAnalysis(ARTICLE_ID,
                List.of(new ArticleAnalysisResponse.Issue("국고채", List.of("국고채 금리가 하락했다."),
                        List.of(), List.of(new ArticleAnalysisResponse.Relation("원화 강세", "국고채 금리 하락",
                        ArticleAnalysisResponse.RelationType.CAUSE_OR_RESULT, "근거",
                        ArticleAnalysisResponse.StatementType.FACT, null)), List.of(), List.of())))));
    }

    private EconomicPrincipleRetriever.Context principles() {
        var query = new EconomicPrincipleRetriever.Query("router", "issues[0].relations[0].articleExplanation", QUERY);
        var chunk = new EconomicPrincipleRetriever.Chunk("direct", "원화 강세는 채권 수요를 높여 금리를 낮춘다.",
                "book", "환율", "1", 1.0);
        return new EconomicPrincipleRetriever.Context(List.of(new EconomicPrincipleRetriever.QueryResult(query, List.of(chunk))));
    }

    private List<ArticleEconomicFlow> flows() {
        var claim = new FlowClaimCandidate("원화 강세", "국고채 금리 하락", EventRelationType.CAUSE);
        return List.of(new ArticleEconomicFlow(new ArticleContext(ARTICLE_ID, "제목", "", OffsetDateTime.now(), ""),
                new EconomicFlowExtraction(List.of(claim))));
    }
}
