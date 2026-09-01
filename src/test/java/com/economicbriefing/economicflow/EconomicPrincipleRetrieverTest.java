package com.economicbriefing.economicflow;

import java.util.List;
import com.economicbriefing.economicflow.entity.EconomicPrincipleChunkEntity;
import com.economicbriefing.economicflow.repository.EconomicPrincipleChunkRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EconomicPrincipleRetrieverTest {
    private final EconomicPrincipleChunkRepository repository = mock(EconomicPrincipleChunkRepository.class);
    private final EconomicPrincipleRetriever retriever = new EconomicPrincipleRetriever(repository);

    @Test
    void retrievesSourcedChunkForRouterAndFlowQueriesWithoutWritingFlow() {
        when(repository.findByActiveTrue()).thenReturn(List.of(chunk()));

        var context = retriever.retrieve(List.of(
                new EconomicPrincipleRetriever.Query("ROUTER_WHY", "issues[0].relations[0]",
                        "달러 매도가 왜 원 달러 환율 하락에 영향을 미치는가"),
                new EconomicPrincipleRetriever.Query("FLOW_JUDGE", "economicFlow.principleQuery",
                        "달러 공급 증가와 환율 하락의 경제적 메커니즘")));

        assertEquals(2, context.queries().size());
        assertEquals("TEST_FIXTURE", context.queries().getFirst().results().getFirst().sourceType());
        assertEquals("외환시장 원리 fixture", context.queries().getFirst().results().getFirst().sourceTitle());
        verify(repository).findByActiveTrue();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void returnsEmptyContextBelowThreshold() {
        when(repository.findByActiveTrue()).thenReturn(List.of(chunk()));

        var context = retriever.retrieve(List.of(new EconomicPrincipleRetriever.Query(
                "ROUTER_WHY", "issues[0].relations[0]", "보험사기 신고 절차")));

        assertTrue(context.queries().isEmpty());
    }

    private EconomicPrincipleChunkEntity chunk() {
        var chunk = new EconomicPrincipleChunkEntity();
        chunk.setContent("수출기업의 달러 매도가 늘면 외환시장의 달러 공급이 증가하고 원 달러 환율에 하락 압력이 생길 수 있다.");
        chunk.setConcepts("네고 물량 달러 매도 달러 공급 원 달러 환율 하락 외환시장");
        chunk.setFromConcept("달러 공급 증가"); chunk.setToConcept("원 달러 환율 하락");
        chunk.setMechanism("수요 공급"); chunk.setSourceType("TEST_FIXTURE");
        chunk.setSourceTitle("외환시장 원리 fixture"); chunk.setSourceSection("달러 공급과 환율");
        return chunk;
    }
}
