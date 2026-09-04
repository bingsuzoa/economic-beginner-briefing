package com.economicbriefing.economicflow;

import java.util.List;
import com.economicbriefing.classifier.EmbeddingService;
import com.economicbriefing.economicflow.repository.PrincipleVectorRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EconomicPrincipleRetrieverTest {
    private final PrincipleVectorRepository repository = mock(PrincipleVectorRepository.class);
    private final EmbeddingService embeddings = mock(EmbeddingService.class);
    private final EconomicPrincipleRetriever retriever = new EconomicPrincipleRetriever(repository, embeddings);

    @Test
    void retrievesSourcedChunkForRouterAndFlowQueriesWithoutWritingFlow() {
        var profile = new PrincipleVectorRepository.EmbeddingProfile("text-embedding-3-large", 1536);
        when(repository.embeddingProfile()).thenReturn(java.util.Optional.of(profile));
        when(embeddings.embed(anyString(), eq(profile.model()), eq(profile.dimensions()))).thenReturn(new float[] {0.1f, 0.2f});
        when(repository.search(anyString(), eq(profile.model()), eq(profile.dimensions()), eq(3))).thenReturn(List.of(chunk()));

        var context = retriever.retrieve(List.of(
                new EconomicPrincipleRetriever.Query("ROUTER_WHY", "issues[0].relations[0]",
                        "달러 매도가 왜 원 달러 환율 하락에 영향을 미치는가"),
                new EconomicPrincipleRetriever.Query("FLOW_JUDGE", "economicFlow.principleQuery",
                        "달러 공급 증가와 환율 하락의 경제적 메커니즘")));

        assertEquals(2, context.queries().size());
        assertEquals("경제원리.pdf", context.queries().getFirst().results().getFirst().sourceType());
        assertEquals("달러 공급과 환율", context.queries().getFirst().results().getFirst().sourceTitle());
        verify(repository).embeddingProfile();
        verify(repository, times(2)).search(anyString(), eq(profile.model()), eq(profile.dimensions()), eq(3));
    }

    @Test
    void returnsEmptyContextBelowThreshold() {
        when(repository.embeddingProfile()).thenReturn(java.util.Optional.empty());

        var context = retriever.retrieve(List.of(new EconomicPrincipleRetriever.Query(
                "ROUTER_WHY", "issues[0].relations[0]", "보험사기 신고 절차")));

        assertTrue(context.queries().isEmpty());
        verifyNoInteractions(embeddings);
    }

    private PrincipleVectorRepository.SearchResult chunk() {
        return new PrincipleVectorRepository.SearchResult("fx-1",
                "수출기업의 달러 매도가 늘면 외환시장의 달러 공급이 증가하고 원 달러 환율에 하락 압력이 생길 수 있다.",
                "경제원리.pdf", "달러 공급과 환율", 10, 12, 0.91);
    }
}
