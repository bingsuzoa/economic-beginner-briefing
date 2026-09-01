package com.economicbriefing.economicflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.reset;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.economicbriefing.classifier.entity.ArticleEntity;
import com.economicbriefing.classifier.repository.ArticleRepository;
import com.economicbriefing.economicflow.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EconomicFlowV2Test {
    @Autowired EconomicFlowIngestor ingestor;
    @Autowired EconomicEventRepository events;
    @Autowired EventRelationRepository relations;
    @Autowired EventEvidenceRepository evidence;
    @Autowired EventRelationEvidenceRepository relationEvidence;
    @Autowired ArticleRepository articles;
    @MockBean FlowNodeResolver resolver;

    @BeforeEach
    void setUp() {
        for (String id : List.of("flow-a", "flow-b", "flow-c", "flow-d")) saveArticle(id);
        when(resolver.resolve(anyList())).thenAnswer(invocation -> {
            List<FlowNodeResolver.Comparison> inputs = invocation.getArgument(0);
            return inputs.stream().collect(java.util.stream.Collectors.toMap(
                    FlowNodeResolver.Comparison::newNode,
                    input -> new FlowNodeResolver.Decision(input.newNode(),
                            FlowResolverDecision.NO_MATCH, null)));
        });
    }

    @Test
    void savesReusableCauseChainAndArticleEvidence() {
        var result = ingest("flow-a", List.of(
                claim("엔화 시장 불안", "강제 포지션 청산 위험", EventRelationType.CAUSE),
                claim("강제 포지션 청산 위험", "글로벌 금융시장 불안", EventRelationType.CAUSE),
                claim("글로벌 금융시장 불안", "미국 차입비용 상승 위험", EventRelationType.CAUSE)));

        assertEquals(4, result.resolvedNodes().size());
        assertEquals(4, events.count());
        assertEquals(3, relations.count());
        assertEquals(4, evidence.count());
        assertEquals(3, relationEvidence.count());
        assertTrue(events.findAll().stream().allMatch(event -> event.getNodeKind() == NodeKind.EVENT
                && "FLOW".equals(event.getScopeKey()) && event.getSlot() == null));
    }

    @Test
    void keepsFlowContractFreeOfArticleCopiesAndTemporaryKeys() {
        assertArrayEquals(new String[] {"text"}, java.util.Arrays.stream(FlowNodeCandidate.class
                .getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
        assertArrayEquals(new String[] {"from", "to", "relationType"}, java.util.Arrays.stream(
                FlowClaimCandidate.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)
                .toArray(String[]::new));
    }

    @Test
    void skipsResolverWhenDatabaseHasNoCandidates() {
        reset(resolver);

        ingest("flow-a", List.of(claim("완전히 새로운 경제 현상", "새로운 정책 대응",
                EventRelationType.RESPONSE)));

        verifyNoInteractions(resolver);
        assertEquals(2, events.count());
    }

    @Test
    void reusesSameNodesAndEdgeAndOnlyAddsNewArticleEvidence() {
        var first = ingest("flow-a", List.of(claim("엔화 시장 불안", "강제 포지션 청산 위험",
                EventRelationType.CAUSE)));
        when(resolver.resolve(anyList())).thenAnswer(invocation -> {
            List<FlowNodeResolver.Comparison> inputs = invocation.getArgument(0);
            return Map.of(
                    "엔화 시장 혼란", new FlowNodeResolver.Decision("엔화 시장 혼란", FlowResolverDecision.SAME,
                            first.resolvedNodes().get(0).resolvedNodeId()),
                    "강제 청산 가능성", new FlowNodeResolver.Decision("강제 청산 가능성", FlowResolverDecision.SAME,
                            first.resolvedNodes().get(1).resolvedNodeId()));
        });

        var second = ingest("flow-b", List.of(claim("엔화 시장 혼란", "강제 청산 가능성",
                EventRelationType.CAUSE)));

        assertEquals(2, events.count());
        assertEquals(1, relations.count());
        assertEquals(first.resolvedNodes().get(0).resolvedNodeId(), second.resolvedNodes().get(0).resolvedNodeId());
        assertEquals(4, evidence.count());
        assertEquals(2, relationEvidence.count());
    }

    @Test
    void relatedButDistinctAndNoMatchCreateNewNodes() {
        ingest("flow-a", List.of(claim("엔화 약세", "수입물가 상승", EventRelationType.CAUSE)));
        when(resolver.resolve(anyList())).thenAnswer(invocation -> {
            List<FlowNodeResolver.Comparison> inputs = invocation.getArgument(0);
            return inputs.stream().collect(java.util.stream.Collectors.toMap(FlowNodeResolver.Comparison::newNode, i ->
                    new FlowNodeResolver.Decision(i.newNode(),
                            i.newNode().equals("엔화 시장의 무질서") ? FlowResolverDecision.RELATED_BUT_DISTINCT
                                    : FlowResolverDecision.NO_MATCH, null)));
        });

        ingest("flow-b", List.of(claim("엔화 시장의 무질서", "강제 청산 위험", EventRelationType.CAUSE)));

        assertEquals(4, events.count());
    }

    @Test
    void laterArticleReusesPastEventAndAddsPurposeEdge() {
        var first = ingest("flow-a", List.of(claim("미국의 엔화 매수 개입", "엔화 가치 안정",
                EventRelationType.PURPOSE)));
        when(resolver.resolve(anyList())).thenAnswer(invocation -> {
            List<FlowNodeResolver.Comparison> inputs = invocation.getArgument(0);
            return inputs.stream().collect(java.util.stream.Collectors.toMap(FlowNodeResolver.Comparison::newNode, i ->
                    i.newNode().equals("미국 엔화 매수 개입")
                            ? new FlowNodeResolver.Decision(i.newNode(), FlowResolverDecision.SAME,
                                    first.resolvedNodes().get(0).resolvedNodeId())
                            : new FlowNodeResolver.Decision(i.newNode(), FlowResolverDecision.NO_MATCH, null)));
        });

        var second = ingest("flow-c", List.of(claim("미국 엔화 매수 개입", "미국 금리 상승 위험 완화",
                EventRelationType.PURPOSE)));

        assertFalse(second.resolvedNodes().get(0).newNodeCreated());
        assertEquals(3, events.count());
        assertEquals(2, relations.count());
    }

    @Test
    void rejectsOrphanAndMissingEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> ingest("flow-a",
                List.of(new FlowClaimCandidate("국고채 금리", "국고채 금리", EventRelationType.CAUSE))));
        assertDoesNotThrow(() -> ingest("flow-empty", List.of()));
    }

    private EconomicFlowIngestor.FlowIngestionResult ingest(String article, List<FlowClaimCandidate> claims) {
        return ingestor.ingestFlow(new ArticleContext(article, article, "https://example.com/" + article,
                java.time.OffsetDateTime.parse("2026-08-29T00:00:00+09:00"), "기사 원문 " + article),
                new EconomicFlowExtraction(claims));
    }

    private FlowClaimCandidate claim(String from, String to, EventRelationType type) {
        return new FlowClaimCandidate(from, to, type);
    }

    private void saveArticle(String id) {
        ArticleEntity article = new ArticleEntity();
        article.setId(id); article.setSource("test"); article.setTitle(id);
        article.setUrl("https://example.com/" + id); article.setCollectedAt(OffsetDateTime.now());
        articles.save(article);
    }
}
