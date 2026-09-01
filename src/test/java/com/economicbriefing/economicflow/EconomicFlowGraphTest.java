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
import com.economicbriefing.economicflow.repository.EconomicPrincipleChunkRepository;
import com.economicbriefing.economicflow.entity.EconomicPrincipleChunkEntity;
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
    @Autowired EconomicPrincipleChunkRepository principleChunks;
    @Autowired EconomicPrincipleRetriever principleRetriever;
    @MockitoBean EconomicFlowTraversalJudge judge;

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

        var chunk = new EconomicPrincipleChunkEntity();
        chunk.setContent("고용 악화와 경기 둔화는 통화 완화 필요를 높여 금리 인하 판단에 영향을 줄 수 있다.");
        chunk.setConcepts("고용 악화 경기 둔화 통화 완화 금리 인하 판단");
        chunk.setSourceType("TEST_FIXTURE"); chunk.setSourceTitle("통화정책 fixture");
        principleChunks.save(chunk);
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
