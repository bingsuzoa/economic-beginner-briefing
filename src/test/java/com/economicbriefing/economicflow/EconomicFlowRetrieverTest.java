package com.economicbriefing.economicflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class EconomicFlowRetrieverTest {
    @Test
    void returnsBoundedAnchoredPathsWithArticleProvenance() {
        var graph = mock(EconomicFlowGraphRepository.class);
        var node = new EconomicFlowGraphRepository.NodeView(1L, NodeKind.EVENT, "", "", null, null, "정책 압박", LocalDate.now());
        var edge = new EconomicFlowGraphRepository.EdgeView(1L, 2L, EventRelationType.RESPONSE, "old-article");
        when(graph.loadAround(1L, 2)).thenReturn(Optional.of(new EconomicFlowGraphRepository.GraphBatch(
                List.of(node), List.of(edge), Set.of())));
        var result = new EconomicFlowRetriever(graph).retrieve(List.of(new EconomicFlowRetriever.Request("r", "왜?")), Set.of(1L));
        assertEquals(List.of("old-article"), result.results().getFirst().path().articleIds());
        verify(graph).loadAround(1L, 2);
    }
}
