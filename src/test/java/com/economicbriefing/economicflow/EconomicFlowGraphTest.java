package com.economicbriefing.economicflow;

import java.time.LocalDate;
import java.util.Set;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import com.economicbriefing.economicflow.entity.EconomicEventEntity;
import com.economicbriefing.economicflow.entity.EventRelationEntity;
import com.economicbriefing.economicflow.repository.EconomicEventRepository;
import com.economicbriefing.economicflow.repository.EventRelationRepository;
import com.economicbriefing.economicflow.repository.PrincipleVectorRepository;
import com.economicbriefing.classifier.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EconomicFlowGraphTest {
    @Autowired EconomicEventRepository events;
    @Autowired EventRelationRepository relations;
    @Autowired EconomicFlowGraphRepository graph;
    @Autowired EconomicFlowContextService contextService;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired EconomicPrincipleRetriever principleRetriever;
    @MockitoBean EconomicFlowTraversalJudge judge;
    @MockitoBean PrincipleVectorRepository principleChunks;
    @MockitoBean EmbeddingService embeddings;

    @Test
    void expandsOnlyRequestedFrontierByAnotherThreeHops() throws Exception {
        var a = event("A"); var b = event("B"); var c = event("C"); var d = event("D");
        var e = event("E"); var f = event("F"); var g = event("G");
        relation(a, b); relation(b, c); relation(c, d); relation(d, e); relation(e, f); relation(f, g);

        var batch = graph.loadBackward(Set.of(g.getId()), 3, Set.of());
        assertEquals(3, batch.edges().size());
        assertEquals(Set.of(d.getId()), batch.frontierNodeIds());
        when(judge.judge(anyString(), anyList(), anyList(), anySet()))
                .thenReturn(new EconomicFlowTraversalJudge.Result(
                                TraversalDecision.MISSING_REAL_WORLD_CAUSE, Set.of(d.getId()), null),
                        new EconomicFlowTraversalJudge.Result(
                                TraversalDecision.ECONOMIC_MECHANISM, Set.of(),
                                "고용 악화와 경기 둔화가 금리 인하 판단에 영향을 주는 경제적 메커니즘"));

        var context = contextService.retrieve("G", Set.of(g.getId()));
        assertEquals(6, context.edges().size());
        assertFalse(context.graphExhausted());
        assertNotNull(context.principleQuery());
        verify(judge, times(2)).judge(anyString(), anyList(), anyList(), anySet());

        var profile = new PrincipleVectorRepository.EmbeddingProfile("test-model", 2);
        when(principleChunks.embeddingProfile()).thenReturn(java.util.Optional.of(profile));
        when(embeddings.embed(anyString(), eq("test-model"), eq(2))).thenReturn(new float[] {0.1f, 0.2f});
        when(principleChunks.search(anyString(), eq("test-model"), eq(2), eq(3))).thenReturn(List.of(
                new PrincipleVectorRepository.SearchResult("test-chunk",
                        "고용 악화와 경기 둔화는 통화 완화 필요를 높여 금리 인하 판단에 영향을 줄 수 있다.",
                        "TEST_FIXTURE", "통화정책 fixture", 1, 2, 0.9)));
        var principle = principleRetriever.retrieve(List.of(new EconomicPrincipleRetriever.Query(
                "FLOW_JUDGE", "economicFlow.principleQuery", context.principleQuery())));
        assertFalse(principle.queries().isEmpty());
        assertEquals("FLOW_JUDGE", principle.queries().getFirst().request().origin());

        var report = new java.util.LinkedHashMap<String, Object>();
        report.put("firstBatch", batch);
        report.put("finalContext", context);
        report.put("economic_events", jdbc.queryForList("SELECT id,title FROM economic_events ORDER BY id"));
        report.put("event_evidence", jdbc.queryForList("SELECT event_id,article_id,evidence_text FROM event_evidence ORDER BY id"));
        report.put("event_relations", jdbc.queryForList("SELECT from_event_id,to_event_id,relation_type,provenance FROM event_relations ORDER BY id"));
        report.put("event_relation_evidence", jdbc.queryForList("SELECT relation_id,article_id,evidence_text FROM event_relation_evidence ORDER BY id"));
        report.put("event_topics", jdbc.queryForList("SELECT event_id,topic_id FROM event_topics ORDER BY event_id"));
        Path output = Path.of("pipeline-debug/economic-flow-traversal-e2e.json");
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }

    @Test
    void returnsAvailablePartialGraphAsExhaustedWithoutInventingMoreHistory() {
        var a = event("A"); var b = event("B"); var c = event("C");
        relation(a, b); relation(b, c);

        var context = contextService.retrieve("C", Set.of(c.getId()));

        assertEquals(3, context.nodes().size());
        assertEquals(2, context.edges().size());
        assertTrue(context.graphExhausted());
        assertNull(context.principleQuery());
        verifyNoInteractions(judge);
    }

    @Test
    void loadsIncomingAndOutgoingNeighborhoodWithoutDuplicatingCycles() {
        var a = event("물가 압력");
        var center = event("미국 기준금리 인상 가능성");
        var b = event("한국 국고채 금리 상승");
        var c = event("달러 강세");
        relation(a, center);
        relation(center, b);
        relation(b, c);
        relation(c, center);

        var depthOne = graph.loadAround(center.getId(), 1).orElseThrow();
        assertEquals(Set.of(a.getId(), center.getId(), b.getId(), c.getId()),
                depthOne.nodes().stream().map(EconomicFlowGraphRepository.NodeView::nodeId)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(3, depthOne.edges().size());
        assertTrue(depthOne.edges().stream().anyMatch(edge ->
                edge.fromNodeId().equals(a.getId()) && edge.toNodeId().equals(center.getId())));
        assertTrue(depthOne.edges().stream().anyMatch(edge ->
                edge.fromNodeId().equals(center.getId()) && edge.toNodeId().equals(b.getId())));

        var depthTwo = graph.loadAround(center.getId(), 2).orElseThrow();
        assertEquals(4, depthTwo.nodes().size());
        assertEquals(4, depthTwo.edges().size());
        assertEquals(4, depthTwo.edges().stream()
                .map(edge -> edge.fromNodeId() + ":" + edge.toNodeId() + ":" + edge.relationType())
                .distinct().count());
    }

    @Test
    void returnsEmptyForMissingCenter() {
        assertTrue(graph.loadAround(Long.MAX_VALUE, 2).isEmpty());
    }

    @Test
    void capsWideThreeHopGraphAtFiftyNodes() {
        var center = event("CENTER");
        for (int i = 0; i < 60; i++) relation(center, event("NODE-" + i));

        var result = graph.loadAround(center.getId(), 3).orElseThrow();

        assertEquals(50, result.nodes().size());
        assertEquals(49, result.edges().size());
    }

    @Test
    void overviewReturnsRelationsBetweenIncludedNodes() {
        var old = event("OLD");
        var recentA = event("RECENT-A");
        var recentB = event("RECENT-B");
        relation(old, recentA);
        relation(recentA, recentB);

        var result = graph.loadOverview();

        assertEquals(3, result.nodes().size());
        assertEquals(2, result.edges().size());
    }

    private EconomicEventEntity event(String title) {
        var event = new EconomicEventEntity();
        event.setEventType(EventType.MARKET_EVENT); event.setTitle(title); event.setSubject(title);
        event.setSubjectKey(title); event.setEventDate(LocalDate.of(2026, 8, 25));
        event.setStatus(EventStatus.CONFIRMED); event.setNodeKind(NodeKind.EVENT); event.setScopeKey("US");
        return events.save(event);
    }

    private void relation(EconomicEventEntity from, EconomicEventEntity to) {
        var relation = new EventRelationEntity();
        relation.setFromEvent(from); relation.setToEvent(to); relation.setRelationType(EventRelationType.DIRECT_CAUSE);
        relation.setProvenance(RelationProvenance.ARTICLE_EXPLICIT); relation.setConfidence(1);
        relations.save(relation);
    }
}
