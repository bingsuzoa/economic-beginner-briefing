package com.economicbriefing.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.economicbriefing.economicflow.EconomicFlowGraphRepository;
import com.economicbriefing.economicflow.EventRelationType;
import com.economicbriefing.economicflow.NodeKind;
import com.economicbriefing.economicflow.entity.EconomicEventEntity;
import com.economicbriefing.economicflow.repository.EconomicEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EconomicNetworkControllerTest {
    private final EconomicEventRepository events = mock(EconomicEventRepository.class);
    private final EconomicFlowGraphRepository graph = mock(EconomicFlowGraphRepository.class);
    private final EconomicNetworkController controller = new EconomicNetworkController(events, graph);

    @Test
    void searchesTitlesAndReturnsMinimalDto() {
        var event = new EconomicEventEntity();
        event.setTitle("원/달러 환율 상승");
        event.setEventDate(LocalDate.of(2026, 9, 1));
        when(events.findTop20ByTitleContainingIgnoreCaseOrderByIdDesc("환율")).thenReturn(List.of(event));

        var result = controller.search(" 환율 ");

        assertTrue(result.success());
        assertEquals("원/달러 환율 상승", result.data().getFirst().label());
    }

    @Test
    void validatesDepthAndMissingNode() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.graph(1, 0).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.graph(1, 4).getStatusCode());
        when(graph.loadAround(99, 2)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, controller.graph(99, 2).getStatusCode());
    }

    @Test
    void preservesEdgeDirection() {
        var nodes = List.of(
                new EconomicFlowGraphRepository.NodeView(1L, NodeKind.EVENT, null, null, null, null,
                        "중동 긴장", LocalDate.of(2026, 9, 1)),
                new EconomicFlowGraphRepository.NodeView(2L, NodeKind.EVENT, null, null, null, null,
                        "국제유가 상승", LocalDate.of(2026, 9, 1)));
        var edges = List.of(new EconomicFlowGraphRepository.EdgeView(1L, 2L, EventRelationType.CAUSE));
        when(graph.loadAround(1, 2)).thenReturn(Optional.of(
                new EconomicFlowGraphRepository.GraphBatch(nodes, edges, Set.of())));

        var result = controller.graph(1, 2).getBody().data();

        assertEquals(1L, result.links().getFirst().source());
        assertEquals(2L, result.links().getFirst().target());
        assertEquals("CAUSE", result.links().getFirst().relationType());
        assertFalse(result.limited());
    }

    @Test
    void returnsOverviewWithoutARequiredCenter() {
        when(graph.loadOverview()).thenReturn(new EconomicFlowGraphRepository.GraphBatch(
                List.of(), List.of(), Set.of()));

        var result = controller.overview();

        assertTrue(result.success());
        assertNull(result.data().centerNodeId());
        assertEquals(0, result.data().depth());
    }
}
