package com.economicbriefing.analyzer.openai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import com.economicbriefing.classifier.ArticlePersistenceService;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.economicflow.*;
import com.economicbriefing.economicflow.entity.*;
import com.economicbriefing.economicflow.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EconomicFlowCoreE2ETest {
    @Autowired ObjectMapper json;
    @Autowired ArticlePersistenceService articlePersistence;
    @Autowired EconomicFlowIngestor ingestor;
    @Autowired EconomicEventRepository events;
    @Autowired EventEvidenceRepository evidence;
    @Autowired EventRelationRepository relations;
    @Autowired EventRelationEvidenceRepository relationEvidence;
    @Autowired EconomicSlotRepository slots;
    @Autowired EconomicSlotValueRepository values;
    @Autowired TopicRepository topics;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void masters() {
        topic("US_INFLATION", "미국 물가", TopicDomain.PRICE);
        topic("US_MONETARY_POLICY", "미국 통화정책", TopicDomain.MONETARY);
        slot("INFLATION_STATUS", "ABOVE_TARGET", "NEAR_TARGET");
        slot("POLICY_STANCE", "CAUTIOUS_ON_CUT");
        slot("RATE_DECISION", "RATE_CUT", "RATE_HOLD");
    }

    @Test
    void verifiesNormalizedStateMemoryNewEventAndExplicitRelationEndToEnd() throws Exception {
        var repeated1 = analyze("state-1", "미국 물가는 연준 목표치를 웃돌고 있다.",
                candidate("inflation-1", "STATE", "US", "INFLATION", "INFLATION_STATUS", "ABOVE_TARGET"), null);
        var first = ingestor.ingestAll(repeated1.eventCandidates(), repeated1.eventRelations()).getFirst();

        var repeated2 = analyze("state-2", "미국 물가는 여전히 연준 목표치를 웃돌고 있다.",
                candidate("inflation-2", "STATE", "US", "INFLATION", "INFLATION_STATUS", "ABOVE_TARGET"), null);
        var repeated = ingestor.ingestAll(repeated2.eventCandidates(), repeated2.eventRelations()).getFirst();

        String relationEvidenceText = "물가가 목표치를 웃돌아 연준은 금리 인하에 신중한 입장을 보였다.";
        var relationDraft = analyze("relation-1", relationEvidenceText,
                candidate("inflation-relation", "STATE", "US", "INFLATION", "INFLATION_STATUS", "ABOVE_TARGET")
                        + "," + candidate("stance-relation", "STATE", "US", "FED", "POLICY_STANCE", "CAUTIOUS_ON_CUT"),
                """
                {"evidence":"%s","atomicRelations":[{"from":"INFLATION","to":"FED",
                "relationType":"CAUSE_OR_RESULT","evidenceType":"FACT","speaker":null,"storeInEconomicFlow":true,
                "fromCandidateKey":"inflation-relation","toCandidateKey":"stance-relation"}]}
                """.formatted(relationEvidenceText));
        var relationResults = ingestor.ingestAll(relationDraft.eventCandidates(), relationDraft.eventRelations());
        ingestor.ingestAll(relationDraft.eventCandidates(), relationDraft.eventRelations());

        var changedDraft = analyze("state-3", "미국 물가는 연준 목표 수준에 근접했다.",
                candidate("inflation-3", "STATE", "US", "INFLATION", "INFLATION_STATUS", "NEAR_TARGET"), null);
        var changed = ingestor.ingestAll(changedDraft.eventCandidates(), changedDraft.eventRelations()).getFirst();

        var eventDraft = analyze("event-1", "연준은 별도의 기준금리 인하 결정을 내렸다.",
                candidate("rate-event", "EVENT", "US", "FED_RATE", "RATE_DECISION", "RATE_CUT"), null);
        var newEvent = ingestor.ingestAll(eventDraft.eventCandidates(), eventDraft.eventRelations()).getFirst();

        var demandDraft = analyze("demand-1", "한 정치인은 연준에 금리 인하를 요구했다.",
                candidate("rate-demand", "EVENT", "US", "RATE_CUT_DEMAND", "RATE_DECISION", "RATE_CUT"), null);
        var demand = ingestor.ingestAll(demandDraft.eventCandidates(), demandDraft.eventRelations()).getFirst();
        var holdDraft = analyze("hold-1", "연준은 기준금리를 동결했다.",
                candidate("rate-hold", "EVENT", "US", "FED_RATE", "RATE_DECISION", "RATE_HOLD"), null);
        var hold = ingestor.ingestAll(holdDraft.eventCandidates(), holdDraft.eventRelations()).getFirst();

        assertTrue(first.created());
        assertFalse(repeated.created());
        assertEquals(first.eventId(), repeated.eventId());
        assertTrue(changed.created());
        assertEquals(LocalDate.of(2026, 8, 25), events.findById(first.eventId()).orElseThrow().getEndedAt());
        assertTrue(relations.existsByFromEvent_IdAndToEvent_IdAndRelationType(
                first.eventId(), changed.eventId(), EventRelationType.STATE_CHANGED_TO));
        assertTrue(newEvent.created());
        assertEquals(2, relations.count(), "same Topic must not create an extra relation");
        assertTrue(relations.existsByFromEvent_IdAndToEvent_IdAndRelationType(
                relationResults.get(0).eventId(), relationResults.get(1).eventId(), EventRelationType.DIRECT_CAUSE));
        assertEquals(1, relationEvidence.count());
        assertEquals(3, evidence.countByEvent_Id(first.eventId()));
        assertEquals(1, events.findById(first.eventId()).orElseThrow().getTopics().size(),
                "only the lexically supported US_INFLATION topic is attached");
        assertFalse(relations.existsByFromEvent_IdAndToEvent_IdAndRelationType(
                demand.eventId(), hold.eventId(), EventRelationType.DIRECT_CAUSE));

        events.flush();
        var report = new LinkedHashMap<String, Object>();
        report.put("results", List.of(first, repeated, changed, newEvent));
        report.put("articles", List.of(
                debug("CASE_1_NEW_STATE", "state-1", repeated1, "NEW_EVENT", null, false,
                        "No active state existed; comparator selected a new normalized STATE."),
                debug("CASE_2_REPEATED_STATE", "state-2", repeated2, "REPEATED_STATE", first.eventId(), true,
                        "Exact active scope+subject+slot+value match; Java reused the Node and added Evidence."),
                debug("CASE_3_STATE_CHANGED", "state-3", changedDraft, "STATE_CHANGED", first.eventId(), false,
                        "Same scope+subject+slot with a different value; comparator matched the active state."),
                debug("CASE_4_NEW_POLICY_EVENT", "event-1", eventDraft, "NEW_EVENT", null, false,
                        "No matching normalized policy Event existed."),
                debug("CASE_5_EXPLICIT_RELATION", "relation-1", relationDraft,
                        "REPEATED_STATE + NEW_EVENT", first.eventId(), true,
                        "Both candidateKey endpoints resolved; ARTICLE_EXPLICIT edge and evidence were stored."),
                debug("CASE_6_SAME_TOPIC_NO_RELATION_A", "demand-1", demandDraft, "NEW_EVENT", null, false,
                        "Independent political demand; no article-explicit endpoint relation."),
                debug("CASE_6_SAME_TOPIC_NO_RELATION_B", "hold-1", holdDraft, "NEW_EVENT", null, false,
                        "Same US_MONETARY_POLICY Topic as demand, but no Edge was created.")));
        report.put("economic_events", jdbc.queryForList("""
                SELECT e.id,e.node_kind,e.scope_key,e.subject_key,s.slot_key,sv.value_key,e.event_date,e.ended_at
                FROM economic_events e LEFT JOIN economic_slots s ON s.id=e.slot_id
                LEFT JOIN economic_slot_values sv ON sv.id=e.slot_value_id ORDER BY e.id
                """));
        report.put("event_evidence", jdbc.queryForList("SELECT event_id,article_id,evidence_text FROM event_evidence ORDER BY id"));
        report.put("event_relations", jdbc.queryForList("SELECT id,from_event_id,to_event_id,relation_type,provenance FROM event_relations ORDER BY id"));
        report.put("event_relation_evidence", jdbc.queryForList("SELECT relation_id,article_id,evidence_text,evidence_type FROM event_relation_evidence ORDER BY id"));
        report.put("event_topics", jdbc.queryForList("SELECT event_id,topic_id FROM event_topics ORDER BY event_id,topic_id"));
        report.put("readableGraph", """
                [US INFLATION]
                ABOVE_TARGET (#%d) --STATE_CHANGED_TO--> NEAR_TARGET (#%d)
                ABOVE_TARGET (#%d) --DIRECT_CAUSE/ARTICLE_EXPLICIT--> CAUTIOUS_ON_CUT (#%d)

                [INDEPENDENT US_MONETARY_POLICY NODES]
                RATE_CUT_DEMAND (#%d)    FED_RATE_HOLD (#%d)
                Same Topic, no verified Edge.
                """.formatted(first.eventId(), changed.eventId(), relationResults.get(0).eventId(),
                        relationResults.get(1).eventId(), demand.eventId(), hold.eventId()));
        Path output = Path.of("pipeline-debug/economic-flow-core-e2e.json");
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }

    private OpenAiNewsAnalyzer.AnalyzerDraftBundle analyze(String articleId, String content,
            String candidates, String relation) throws Exception {
        Article article = new Article(articleId, articleId, "", "fixture", ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.parse("2026-08-25T09:00:00+09:00"), OffsetDateTime.now(),
                "https://example.com/" + articleId, List.of(NewsCategory.INTEREST_RATE), "ko", content);
        articlePersistence.save(article);
        String relationJson = relation == null ? "" : relation;
        String raw = """
                {"articles":[{"articleId":"%s","issues":[{"name":"경제 흐름","mainFacts":["%s"],
                "changes":[],"relationCandidates":[%s],"statements":[],"keyTerms":[]}],
                "eventCandidates":[%s]}]}
                """.formatted(articleId, content, relationJson, candidates);
        return OpenAiNewsAnalyzer.parseDraftBundle(json, raw, List.of(article));
    }

    private static String candidate(String key, String kind, String scope, String subject,
            String slot, String value) {
        return """
                {"eventType":"MARKET_EVENT","title":"%s","subject":"%s","subjectKey":"%s",
                "eventDate":"2026-08-25","previousState":null,"newState":"%s","status":"CONFIRMED",
                "region":"%s","topicKeys":%s,"newTopicCandidates":[],
                "evidenceText":"%s","milestoneType":null,"milestonePeriodValue":null,
                "milestonePeriodUnit":null,"milestoneReferenceDate":null,"candidateKey":"%s",
                "nodeKind":"%s","scopeKey":"%s","slotKey":"%s","valueKey":"%s"}
                """.formatted(subject, subject, subject, value, scope, topicsFor(key),
                        evidenceFor(key), key, kind, scope, slot, value);
    }

    private static String evidenceFor(String key) {
        return switch (key) {
            case "inflation-1" -> "미국 물가는 연준 목표치를 웃돌고 있다.";
            case "inflation-2" -> "미국 물가는 여전히 연준 목표치를 웃돌고 있다.";
            case "inflation-3" -> "미국 물가는 연준 목표 수준에 근접했다.";
            case "rate-event" -> "연준은 별도의 기준금리 인하 결정을 내렸다.";
            case "rate-demand" -> "한 정치인은 연준에 금리 인하를 요구했다.";
            case "rate-hold" -> "연준은 기준금리를 동결했다.";
            default -> "물가가 목표치를 웃돌아 연준은 금리 인하에 신중한 입장을 보였다.";
        };
    }

    private static String topicsFor(String key) {
        return key.startsWith("inflation")
                ? "[\"US_INFLATION\",\"US_MONETARY_POLICY\"]"
                : "[\"US_MONETARY_POLICY\"]";
    }

    private LinkedHashMap<String, Object> debug(String caseName, String articleId,
            OpenAiNewsAnalyzer.AnalyzerDraftBundle bundle, String decision, Long matchedNodeId,
            boolean javaExact, String reason) {
        var item = new LinkedHashMap<String, Object>();
        item.put("case", caseName);
        item.put("article", java.util.Map.of("articleId", articleId, "source", "TEST_FIXTURE",
                "body", evidenceFor(bundle.eventCandidates().getFirst().candidateKey())));
        item.put("analyzer", bundle.analysis());
        item.put("router", java.util.Map.of("executed", false,
                "reason", "Fixture isolates deterministic Economic Flow memory; Router is evaluated by LIVE_ARTICLE."));
        item.put("eventCandidates", bundle.eventCandidates());
        item.put("normalization", bundle.eventCandidates().stream().map(candidate -> java.util.Map.of(
                "scope", candidate.scopeKey(), "subject", candidate.subjectKey(),
                "slot", candidate.slotKey(), "value", candidate.valueKey(),
                "slotMasterMatch", slots.findBySlotKeyAndActiveTrue(candidate.slotKey()).isPresent(),
                "slotValueMasterMatch", values.findBySlot_IdAndValueKeyAndActiveTrue(
                        slots.findBySlotKeyAndActiveTrue(candidate.slotKey()).orElseThrow().getId(),
                        candidate.valueKey()).isPresent())).toList());
        item.put("memoryDecision", java.util.Map.of("decision", decision,
                "matchedNodeId", matchedNodeId == null ? "" : matchedNodeId,
                "javaExact", javaExact, "comparator", !javaExact, "reason", reason));
        item.put("relationCandidates", bundle.eventRelations());
        return item;
    }

    private void topic(String key, String name, TopicDomain domain) {
        TopicEntity topic = new TopicEntity();
        topic.setTopicKey(key); topic.setName(name); topic.setDomain(domain); topics.save(topic);
    }

    private void slot(String key, String... valueKeys) {
        EconomicSlotEntity slot = new EconomicSlotEntity(); slot.setSlotKey(key); slot.setName(key); slots.save(slot);
        for (String keyValue : valueKeys) {
            EconomicSlotValueEntity value = new EconomicSlotValueEntity();
            value.setSlot(slot); value.setValueKey(keyValue); value.setName(keyValue); values.save(value);
        }
    }
}
